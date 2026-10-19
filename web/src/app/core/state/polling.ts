import { DestroyRef, effect, inject, signal } from '@angular/core';
import { QueueEvents, type QueueEvent } from '../realtime/queue-events';
import { SettingsStore } from './settings.store';

/** Eventos que chegam juntos (p.ex. chamada + atualização da fila) viram um só recarregamento. */
const COALESCE_MS = 120;

/**
 * Mantém a tela em dia com a fila: recarrega a cada evento SSE e ao reconectar. Só enquanto a
 * conexão em tempo real estiver fora do ar volta a consultar no intervalo configurado.
 */
export function injectLiveRefresh(refresh: () => void, onEvent?: (event: QueueEvent) => void): void {
  const realtime = inject(QueueEvents);
  const pollingMs = inject(SettingsStore).pollingMs;
  let pending: ReturnType<typeof setTimeout> | undefined;

  const schedule = () => {
    clearTimeout(pending);
    pending = setTimeout(refresh, COALESCE_MS);
  };

  const subscription = realtime.events.subscribe((event) => {
    if (event.type !== 'ready') onEvent?.(event as QueueEvent);
    schedule();
  });

  // contingência: polling só sem tempo real
  effect((onCleanup) => {
    if (realtime.state() === 'live') return;
    const timer = setInterval(() => {
      if (!document.hidden) refresh();
    }, pollingMs());
    onCleanup(() => clearInterval(timer));
  });

  const onVisible = () => {
    if (!document.hidden) schedule();
  };
  document.addEventListener('visibilitychange', onVisible);
  inject(DestroyRef).onDestroy(() => {
    subscription.unsubscribe();
    clearTimeout(pending);
    document.removeEventListener('visibilitychange', onVisible);
  });
}

const now = signal(Date.now());
let ticking = false;

/** Relógio de 1s compartilhado para cronômetros de espera e prazos de comparecimento. */
export function injectNow() {
  if (!ticking) {
    ticking = true;
    setInterval(() => now.set(Date.now()), 1000);
  }
  return now.asReadonly();
}
