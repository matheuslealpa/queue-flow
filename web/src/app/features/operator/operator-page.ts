import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { toast } from '@spartan-ng/brain/sonner';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmEmptyImports } from '@spartan-ng/helm/empty';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';
import { EntryApi, type EntryAction } from '../../core/api/entry.api';
import { TERMINAL_STATUSES, type EntryResponse } from '../../core/api/models';
import { QueueApi } from '../../core/api/queue.api';
import { elapsedSince } from '../../core/i18n/labels';
import { EnvironmentStore } from '../../core/state/environment.store';
import { injectLiveRefresh, injectNow } from '../../core/state/polling';
import { StatusBadge } from '../../shared/components/status-badge';
import { Ticket } from '../../shared/components/ticket';

type Busy = EntryAction | 'next' | null;

/** Estação de um guichê: chama a próxima senha e conduz o atendimento até o fim. */
@Component({
  selector: 'app-operator-page',
  imports: [RouterLink, HlmButtonImports, HlmCardImports, HlmEmptyImports, HlmSpinnerImports, StatusBadge, Ticket],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (counter(); as c) {
      <header class="flex flex-wrap items-end justify-between gap-2">
        <div>
          <p class="font-mono text-xs tracking-wider text-muted-foreground uppercase">{{ queue.value()?.name ?? 'Fila' }}</p>
          <h1 class="mt-0.5 text-xl font-semibold">{{ c.servicePointName }}</h1>
          <p class="text-sm text-muted-foreground">Atendente: {{ c.operatorName }}</p>
        </div>
        <p class="text-sm text-muted-foreground">
          <span class="font-mono font-semibold text-foreground">{{ waiting.value().length }}</span> aguardando
        </p>
      </header>

      <div class="mt-4 grid gap-4 lg:grid-cols-[1fr_16rem]">
        <section hlmCard class="overflow-hidden p-0">
          <div class="flex min-h-52 flex-col items-center justify-center gap-3 bg-board px-6 py-8 text-center">
            @if (active(); as e) {
              <app-ticket class="text-7xl text-signal md:text-8xl" [code]="e.ticket" />
              <app-status-badge [status]="e.status" class="[&>span]:bg-white/10 [&>span]:text-white" />
              <p class="min-h-5 font-mono text-sm" [class]="late() ? 'text-called' : 'text-[#C9D2DD]'">{{ timerText() }}</p>
            } @else if (last(); as e) {
              <p class="text-sm text-[#C9D2DD]">Última senha</p>
              <app-ticket class="text-5xl text-white/40" [code]="e.ticket" />
              <app-status-badge [status]="e.status" />
            } @else {
              <p class="font-display text-6xl font-black tracking-[0.12em] text-white/15" aria-hidden="true">----</p>
              <p class="text-sm text-[#C9D2DD]">Guichê livre. Chame a próxima senha.</p>
            }
          </div>

          <div class="flex flex-wrap gap-2 p-3">
            @switch (active()?.status) {
              @case ('CALLED') {
                <button hlmBtn size="lg" class="flex-1" [disabled]="!!busy()" (click)="act('start')">
                  @if (busy() === 'start') { <hlm-spinner /> } Iniciar atendimento
                </button>
                <button hlmBtn size="lg" variant="destructive" class="flex-1" [disabled]="!!busy()" (click)="act('no-show')">
                  Não compareceu
                </button>
              }
              @case ('IN_SERVICE') {
                <button hlmBtn size="lg" class="flex-1 bg-serving text-white hover:bg-serving/85" [disabled]="!!busy()" (click)="act('complete')">
                  @if (busy() === 'complete') { <hlm-spinner /> } Concluir atendimento
                </button>
                @if (queue.value()?.policy?.allowReturnFromService) {
                  <button hlmBtn size="lg" variant="outline" class="flex-1" [disabled]="!!busy()" (click)="act('return')">
                    Devolver à fila
                  </button>
                }
              }
              @default {
                @if (queue.value()?.status === 'CLOSED') {
                  <p class="flex-1 py-2 text-center text-sm text-muted-foreground">Fila encerrada: não há mais chamadas.</p>
                } @else {
                  <button
                    hlmBtn
                    size="lg"
                    class="flex-1 bg-signal text-signal-foreground hover:bg-signal/85"
                    [disabled]="!!busy()"
                    (click)="callNext()"
                  >
                    @if (busy() === 'next') { <hlm-spinner /> } Chamar próxima
                  </button>
                }
              }
            }
          </div>
        </section>

        <aside>
          <h2 class="mb-2 text-sm font-semibold">Próximas na fila</h2>
          <ol class="flex flex-col divide-y rounded-lg border bg-card">
            @for (e of upcoming(); track e.id) {
              <li class="flex items-center justify-between px-3 py-1.5">
                <app-ticket class="text-lg" [code]="e.ticket" />
                <span class="text-xs text-muted-foreground">
                  @if (e.priority.level > 0) { <span class="font-semibold text-foreground">{{ label(e.priority.classification) }}</span> · }
                  {{ elapsed(e) }}
                </span>
              </li>
            } @empty {
              <li class="px-3 py-4 text-sm text-muted-foreground">Ninguém aguardando.</li>
            }
          </ol>
        </aside>
      </div>
    } @else {
      <div hlmEmpty>
        <div hlmEmptyHeader>
          <h1 hlmEmptyTitle>Guichê desconhecido</h1>
          <p hlmEmptyDescription>Este navegador não tem um guichê com esse atendente. Escolha um na lista.</p>
        </div>
        <div hlmEmptyContent><a hlmBtn routerLink="/operador">Ver guichês</a></div>
      </div>
    }
  `,
})
export class OperatorPage {
  private readonly store = inject(EnvironmentStore);
  private readonly queueApi = inject(QueueApi);
  private readonly entryApi = inject(EntryApi);

  readonly operatorId = input.required<string>();

  protected readonly now = injectNow();
  protected readonly counter = computed(() => this.store.counter(this.operatorId()));
  private readonly entryId = computed(() => this.store.currentEntries()[this.operatorId()]);

  protected readonly queue = this.queueApi.queueResource(this.store.queueId);
  protected readonly waiting = this.queueApi.waitingResource(this.store.queueId, 5);
  protected readonly entry = this.entryApi.entryResource(this.entryId);
  protected readonly busy = signal<Busy>(null);

  protected readonly upcoming = computed(() => this.waiting.value().slice(0, 5));
  protected readonly active = computed(() => {
    const e = this.currentEntry();
    return e && !TERMINAL_STATUSES.has(e.status) && e.status !== 'WAITING' ? e : null;
  });
  protected readonly last = computed(() => {
    const e = this.currentEntry();
    return e && !this.active() ? e : null;
  });

  private readonly secondsLeft = computed(() => {
    const call = this.active()?.call;
    return call ? Math.round((Date.parse(call.expiresAt) - this.now()) / 1000) : 0;
  });
  protected readonly late = computed(() => this.active()?.status === 'CALLED' && this.secondsLeft() <= 0);
  protected readonly timerText = computed(() => {
    const e = this.active();
    if (!e) return '';
    if (e.status === 'IN_SERVICE') return `Chamada há ${this.sinceCall(e)}`;
    return this.secondsLeft() > 0
      ? `Comparecimento em até ${this.secondsLeft()}s`
      : 'Prazo esgotado: a senha volta para a fila';
  });

  constructor() {
    injectLiveRefresh(() => {
      this.entry.reload();
      this.waiting.reload();
      this.queue.reload();
    });
  }

  /** Resposta do resource apenas se for da senha em mãos agora (evita piscar a anterior ao trocar). */
  private currentEntry(): EntryResponse | undefined {
    const e = this.entry.hasValue() ? this.entry.value() : undefined;
    return e && e.id === this.entryId() ? e : undefined;
  }

  /** A API não informa quando o atendimento começou, só quando a senha foi chamada. */
  private sinceCall(e: EntryResponse): string {
    return elapsedSince(e.call?.calledAt ?? e.enqueuedAt, this.now());
  }

  protected label(code: string): string {
    return this.queue.value()?.classifications.find((c) => c.code === code)?.label ?? code;
  }

  protected elapsed(e: EntryResponse): string {
    return elapsedSince(e.enqueuedAt, this.now());
  }

  protected async callNext(): Promise<void> {
    const c = this.counter();
    if (!c) return;
    await this.run('next', async () => {
      const called = await this.queueApi.callNext(this.store.require().queueId, c.servicePointId, c.operatorId);
      if (!called) {
        toast.info('Ninguém aguardando.');
        return;
      }
      // o painel anuncia a chamada a partir do evento entry.called da API
      this.store.hold(c.operatorId, called.id);
      this.entry.set(called);
    });
  }

  protected async act(action: EntryAction): Promise<void> {
    const id = this.entryId();
    if (!id) return;
    await this.run(action, async () => {
      const reason = action === 'return' ? 'Devolvida pelo guichê' : undefined;
      this.entry.set(await this.entryApi.act(id, action, { operatorId: this.operatorId() }, reason));
    });
  }

  private async run(kind: Busy, action: () => Promise<void>): Promise<void> {
    this.busy.set(kind);
    try {
      await action();
    } catch {
      // o interceptor já mostrou o erro
    } finally {
      this.busy.set(null);
      this.waiting.reload();
    }
  }
}
