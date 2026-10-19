import { DestroyRef, effect, inject, Injectable, signal, untracked } from '@angular/core';
import { Subject } from 'rxjs';
import { API } from '../api/queue.api';
import { identityHeaders } from '../http/identity';
import { EnvironmentStore } from '../state/environment.store';
import { SettingsStore } from '../state/settings.store';
import { SseParser } from './sse-parser';

/** Espelho do QueueEvent da API (shared.infrastructure.realtime). */
export interface QueueEvent {
  type: string;
  organizationId: string;
  queueId: string;
  entryId: string | null;
  ticket: string | null;
  status: string | null;
  servicePointId: string | null;
  servicePointName: string | null;
  occurredAt: string;
}

/**
 * `live`: conectado, as telas só recarregam quando algo muda.
 * `connecting`/`offline`: as telas voltam ao polling até reconectar.
 */
export type RealtimeState = 'connecting' | 'live' | 'offline';

const MAX_BACKOFF_MS = 30_000;

/**
 * Uma conexão SSE por aba com `GET /queues/{id}/events`. Lida com `fetch` em vez de EventSource
 * porque a API identifica a organização por header, e EventSource não envia headers.
 */
@Injectable({ providedIn: 'root' })
export class QueueEvents {
  private readonly store = inject(EnvironmentStore);
  private readonly settings = inject(SettingsStore);

  readonly state = signal<RealtimeState>('offline');
  /** Eventos da fila; `ready` sinaliza (re)conexão, quando vale recarregar tudo. */
  readonly events = new Subject<QueueEvent | { type: 'ready' }>();

  constructor() {
    effect((onCleanup) => {
      const queueId = this.store.queueId();
      if (!queueId) {
        this.state.set('offline');
        return;
      }
      const controller = new AbortController();
      // só a fila decide reconectar; outros signals lidos ao abrir a conexão não devem ser rastreados
      untracked(() => void this.run(queueId, controller.signal));
      onCleanup(() => controller.abort());
    });
    inject(DestroyRef).onDestroy(() => this.events.complete());
  }

  private async run(queueId: string, abort: AbortSignal): Promise<void> {
    let attempt = 0;
    while (!abort.aborted) {
      this.state.set('connecting');
      try {
        const response = await fetch(`${API}/queues/${queueId}/events`, {
          headers: {
            Accept: 'text/event-stream, application/problem+json',
            ...identityHeaders('admin', this.store.environment(), this.settings.kioskChannel()),
          },
          cache: 'no-store',
          signal: abort,
        });
        if (!response.ok || !response.body) throw new Error(`HTTP ${response.status}`);
        await this.consume(response.body, abort, () => {
          attempt = 0;
          this.state.set('live');
        });
      } catch {
        // queda de rede, API reiniciando ou fila inacessível: tenta de novo com espera crescente
      }
      if (abort.aborted) return;
      this.state.set('offline');
      await sleep(Math.min(MAX_BACKOFF_MS, 1000 * 2 ** attempt++), abort);
    }
  }

  private async consume(body: ReadableStream<Uint8Array>, abort: AbortSignal, onReady: () => void): Promise<void> {
    const reader = body.getReader();
    const decoder = new TextDecoder();
    const parser = new SseParser();
    abort.addEventListener('abort', () => void reader.cancel(), { once: true });
    for (;;) {
      const { value, done } = await reader.read();
      if (done) return;
      for (const message of parser.push(decoder.decode(value, { stream: true }))) {
        if (message.event === 'ready') {
          onReady();
          this.events.next({ type: 'ready' });
        } else if (message.data) {
          this.events.next(JSON.parse(message.data) as QueueEvent);
        }
      }
    }
  }
}

function sleep(ms: number, abort: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, ms);
    abort.addEventListener('abort', () => (clearTimeout(timer), resolve()), { once: true });
  });
}
