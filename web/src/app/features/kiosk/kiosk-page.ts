import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';
import type { Classification, EntryResponse } from '../../core/api/models';
import { QueueApi } from '../../core/api/queue.api';
import { timeOf } from '../../core/i18n/labels';
import { EnvironmentStore } from '../../core/state/environment.store';
import { injectLiveRefresh } from '../../core/state/polling';
import { Ticket } from '../../shared/components/ticket';

const STUB_SECONDS = 12;

/** Autoatendimento: a pessoa escolhe o tipo de atendimento e recebe a senha impressa na tela. */
@Component({
  selector: 'app-kiosk-page',
  imports: [HlmSpinnerImports, Ticket],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-h-svh bg-background' },
  styles: `
    .stub {
      --notch: 14px;
      mask:
        radial-gradient(circle var(--notch) at 0 68%, #0000 98%, #000) left / 51% 100% no-repeat,
        radial-gradient(circle var(--notch) at 100% 68%, #0000 98%, #000) right / 51% 100% no-repeat;
    }
    .stub-enter {
      animation: print 600ms cubic-bezier(0.2, 0.8, 0.2, 1) both;
    }
    @keyframes print {
      from { clip-path: inset(0 0 100% 0); transform: translateY(-12px); }
      to { clip-path: inset(0 0 0 0); transform: none; }
    }
    @media (prefers-reduced-motion: reduce) {
      .stub-enter { animation: none; }
    }
  `,
  template: `
    <div class="mx-auto flex min-h-svh max-w-3xl flex-col px-4 py-10 md:py-16">
      <header class="text-center">
        <p class="font-mono text-xs tracking-[0.2em] text-muted-foreground uppercase">{{ queue.value()?.name ?? '' }}</p>
        <h1 class="mt-3 text-3xl font-semibold md:text-4xl">
          @if (issued()) { Sua senha } @else if (accepting()) { Toque para retirar sua senha }
        </h1>
      </header>

      <main class="flex flex-1 flex-col items-center justify-center py-10">
        @if (issued(); as e) {
          <div class="stub stub-enter w-full max-w-sm rounded-xl bg-card px-8 pt-8 pb-6 text-center shadow-[0_24px_48px_-24px_rgb(20_36_58/0.45)]">
            <p class="text-sm text-muted-foreground">{{ label(e.priority.classification) }}</p>
            <app-ticket class="mt-2 block text-8xl text-foreground" [code]="e.ticket" />
            <p class="mt-4 text-sm">Acompanhe o painel. Você será chamado pelo número.</p>
            <div class="my-6 border-t-2 border-dashed border-border" aria-hidden="true"></div>
            <p class="font-mono text-xs text-muted-foreground">
              {{ aheadText() }} · emitida às {{ time(e.createdAt) }}
            </p>
          </div>
          <button class="mt-8 text-sm font-medium text-muted-foreground underline underline-offset-4" (click)="reset()">
            Concluir ({{ countdown() }}s)
          </button>
        } @else if (accepting()) {
          <div class="grid w-full gap-4" [class.sm:grid-cols-2]="options().length > 1">
            @for (c of options(); track c.code) {
              <button
                class="group flex min-h-40 flex-col items-start justify-between rounded-2xl border-2 bg-card p-6 text-start transition
                       hover:border-foreground focus-visible:border-foreground focus-visible:ring-4 focus-visible:ring-signal/60 focus-visible:outline-none
                       active:translate-y-px disabled:opacity-60"
                [class.border-signal]="c.level > 0"
                [disabled]="!!busy()"
                (click)="take(c)"
              >
                <span class="text-2xl font-semibold">{{ c.label }}</span>
                <span class="flex w-full items-center justify-between text-sm text-muted-foreground">
                  {{ hint(c) }}
                  @if (busy() === c.code) { <hlm-spinner /> }
                </span>
              </button>
            }
          </div>
          @if (failure()) {
            <p class="mt-6 text-center text-sm text-called" role="alert">{{ failure() }}</p>
          }
        } @else if (queue.value(); as q) {
          <div class="text-center">
            <p class="font-display text-6xl font-black tracking-[0.12em] text-muted-foreground/40" aria-hidden="true">----</p>
            <p class="mt-6 text-xl font-semibold">{{ q.status === 'PAUSED' ? 'Emissão de senhas pausada' : 'Atendimento encerrado' }}</p>
            <p class="mt-2 text-muted-foreground">Procure a recepção.</p>
          </div>
        } @else {
          <hlm-spinner class="size-8" />
        }
      </main>
    </div>
  `,
})
export class KioskPage {
  private readonly store = inject(EnvironmentStore);
  private readonly queueApi = inject(QueueApi);

  protected readonly queue = this.queueApi.queueResource(this.store.queueId);
  protected readonly issued = signal<EntryResponse | null>(null);
  protected readonly ahead = signal<number | null>(null);
  protected readonly busy = signal<string | null>(null);
  protected readonly failure = signal<string | null>(null);
  protected readonly countdown = signal(STUB_SECONDS);

  protected readonly accepting = computed(() => this.queue.value()?.status === 'ACTIVE');
  /** Prioridades restritas só podem ser atribuídas pela recepção. */
  protected readonly options = computed(() =>
    (this.queue.value()?.classifications ?? []).filter((c) => !c.restricted).sort((a, b) => a.level - b.level),
  );
  protected readonly aheadText = computed(() => {
    const n = this.ahead();
    if (n == null) return 'Senha registrada';
    return n === 0 ? 'Você é o próximo' : `${n} ${n === 1 ? 'pessoa' : 'pessoas'} na sua frente`;
  });

  private timer: ReturnType<typeof setInterval> | undefined;

  constructor() {
    injectLiveRefresh(() => this.queue.reload());
    inject(DestroyRef).onDestroy(() => clearInterval(this.timer));
  }

  protected label(code: string): string {
    return this.queue.value()?.classifications.find((c) => c.code === code)?.label ?? code;
  }

  protected hint(c: Classification): string {
    return c.level > 0 ? 'Atendimento prioritário' : 'Atendimento geral';
  }

  protected time(iso: string): string {
    return timeOf(iso);
  }

  protected async take(c: Classification): Promise<void> {
    const env = this.store.require();
    this.busy.set(c.code);
    this.failure.set(null);
    try {
      const entry = await this.queueApi.enqueue(
        env.queueId,
        { serviceId: env.serviceId, priorityClassification: c.code === 'NORMAL' ? null : c.code },
        'kiosk',
      );
      this.issued.set(entry);
      this.ahead.set(null);
      this.startCountdown();
      // posição projetada pela própria API, já com a intercalação de prioridades
      this.queueApi
        .waitingList(env.queueId)
        .then((list) => this.ahead.set(Math.max(0, list.findIndex((e) => e.id === entry.id))))
        .catch(() => undefined);
    } catch {
      this.failure.set('Não foi possível emitir a senha agora. Tente de novo ou procure a recepção.');
    } finally {
      this.busy.set(null);
    }
  }

  protected reset(): void {
    clearInterval(this.timer);
    this.issued.set(null);
  }

  private startCountdown(): void {
    clearInterval(this.timer);
    this.countdown.set(STUB_SECONDS);
    this.timer = setInterval(() => {
      this.countdown.update((s) => s - 1);
      if (this.countdown() <= 0) this.reset();
    }, 1000);
  }
}
