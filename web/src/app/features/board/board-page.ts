import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { QueueApi } from '../../core/api/queue.api';
import { timeOf } from '../../core/i18n/labels';
import { EnvironmentStore } from '../../core/state/environment.store';
import { injectLiveRefresh, injectNow } from '../../core/state/polling';
import { QueueEvents } from '../../core/realtime/queue-events';
import { SettingsStore } from '../../core/state/settings.store';
import { Ticket } from '../../shared/components/ticket';

/**
 * Painel de chamadas para TV. Cada chamada chega como evento `entry.called` da API (SSE), venha
 * de qualquer guichê; o histórico fica no localStorage para sobreviver a recarregamentos.
 */
@Component({
  selector: 'app-board-page',
  imports: [Ticket],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block h-svh overflow-hidden bg-board-deep text-white' },
  styles: `
    /* matriz de LEDs apagados atrás da senha */
    .leds {
      background-image: radial-gradient(circle, rgb(255 255 255 / 0.05) 1.2px, transparent 1.6px);
      background-size: 9px 9px;
    }
    .glow {
      text-shadow: 0 0 24px rgb(244 196 48 / 0.45), 0 0 2px rgb(244 196 48 / 0.9);
    }
    .flash {
      animation: flash 1.1s steps(2, jump-none) 3;
    }
    @keyframes flash {
      50% { opacity: 0.15; }
    }
    @media (prefers-reduced-motion: reduce) {
      .flash { animation: none; }
    }
  `,
  template: `
    <div class="grid h-full grid-rows-[auto_1fr] lg:grid-cols-[1fr_minmax(18rem,26vw)] lg:grid-rows-[auto_1fr]">
      <header class="col-span-full flex items-center justify-between border-b border-white/10 px-6 py-4 lg:px-10">
        <p class="text-lg font-semibold tracking-wide text-[#C9D2DD] lg:text-2xl">{{ queue.value()?.name ?? '' }}</p>
        <div class="flex items-center gap-4">
          @if (realtime() !== 'live') {
            <span class="text-xs text-[#9AA8BA]" role="status">Sem conexão em tempo real · reconectando</span>
          }
          <p class="font-display text-2xl font-extrabold tracking-[0.15em] text-signal/80 tabular-nums lg:text-4xl">{{ clock() }}</p>
        </div>
      </header>

      <main class="leds flex flex-col items-center justify-center gap-6 px-6 py-8 text-center" aria-live="assertive">
        @if (latest(); as call) {
          <p class="text-sm font-semibold tracking-[0.3em] text-[#9AA8BA] uppercase lg:text-xl">Senha</p>
          <!-- @for de um item só: recria o elemento a cada nova chamada e reinicia o piscar -->
          @for (c of [call]; track c.entryId + c.calledAt) {
            <app-ticket class="glow flash text-[clamp(6rem,22vw,22rem)] text-signal" [code]="c.ticket" />
          }
          <p class="flex items-center gap-4 text-[clamp(2rem,5vw,5rem)] font-semibold">
            <span class="text-signal/70" aria-hidden="true">→</span> {{ call.servicePointName }}
          </p>
        } @else {
          <p class="font-display text-[clamp(6rem,22vw,22rem)] font-black tracking-[0.08em] text-white/10" aria-hidden="true">----</p>
          <p class="text-xl text-[#9AA8BA]">Aguarde a chamada da sua senha</p>
        }
      </main>

      <aside class="hidden border-s border-white/10 bg-board px-8 py-8 lg:block">
        <h2 class="text-sm font-semibold tracking-[0.3em] text-[#9AA8BA] uppercase">Últimas chamadas</h2>
        <ol class="mt-6 flex flex-col">
          @for (call of previous(); track call.entryId + call.calledAt) {
            <li class="flex items-baseline justify-between border-b border-white/10 py-4">
              <app-ticket class="text-5xl text-white/80" [code]="call.ticket" />
              <span class="text-end">
                <span class="block text-lg font-medium">{{ call.servicePointName }}</span>
                <span class="font-mono text-xs text-[#9AA8BA]">{{ time(call.calledAt) }}</span>
              </span>
            </li>
          } @empty {
            <li class="py-4 text-[#9AA8BA]">Nenhuma chamada ainda.</li>
          }
        </ol>
      </aside>
    </div>

    @if (soundWanted() && !soundReady()) {
      <button
        class="fixed right-4 bottom-4 rounded-full bg-white/10 px-4 py-2 text-sm text-white backdrop-blur hover:bg-white/20 focus-visible:ring-2 focus-visible:ring-signal focus-visible:outline-none"
        (click)="enableSound()"
      >
        Ativar som das chamadas
      </button>
    }
  `,
})
export class BoardPage {
  private readonly store = inject(EnvironmentStore);
  private readonly settings = inject(SettingsStore);

  protected readonly queue = inject(QueueApi).queueResource(this.store.queueId);
  private readonly now = injectNow();
  protected readonly realtime = inject(QueueEvents).state;

  protected readonly latest = computed(() => this.store.recentCalls()[0]);
  protected readonly previous = computed(() => this.store.recentCalls().slice(1, 6));
  protected readonly clock = computed(() =>
    new Date(this.now()).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }),
  );

  protected readonly soundWanted = computed(() => this.settings.settings().boardSound);
  protected readonly soundReady = signal(false);
  private audio: AudioContext | null = null;

  constructor() {
    injectLiveRefresh(
      () => this.queue.reload(),
      (event) => {
        if (event.type !== 'entry.called' || !event.entryId || !event.ticket) return;
        this.store.announce({
          entryId: event.entryId,
          ticket: event.ticket,
          servicePointName: event.servicePointName ?? this.store.servicePointName(event.servicePointId ?? ''),
          calledAt: event.occurredAt,
        });
      },
    );

    let lastKey: string | undefined;
    effect(() => {
      const call = this.latest();
      const key = call && call.entryId + call.calledAt;
      if (lastKey !== undefined && key && key !== lastKey) untracked(() => this.chime());
      lastKey = key ?? '';
    });
  }

  protected time(iso: string): string {
    return timeOf(iso);
  }

  /** Navegadores só liberam áudio após um gesto do usuário. */
  protected enableSound(): void {
    this.audio = new AudioContext();
    this.soundReady.set(true);
    this.chime();
  }

  /** Dois tons descendentes, como o "plim-plom" dos painéis de clínica. */
  private chime(): void {
    if (!this.audio || !this.soundWanted()) return;
    const ctx = this.audio;
    [880, 660].forEach((frequency, i) => {
      const start = ctx.currentTime + i * 0.32;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.value = frequency;
      gain.gain.setValueAtTime(0.0001, start);
      gain.gain.exponentialRampToValueAtTime(0.35, start + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.6);
      osc.connect(gain).connect(ctx.destination);
      osc.start(start);
      osc.stop(start + 0.62);
    });
  }
}
