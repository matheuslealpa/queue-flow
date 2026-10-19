import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { toast } from '@spartan-ng/brain/sonner';
import { HlmAlertDialogImports } from '@spartan-ng/helm/alert-dialog';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmNativeSelectImports } from '@spartan-ng/helm/native-select';
import { HlmSkeletonImports } from '@spartan-ng/helm/skeleton';
import type { QueueStatus } from '../../core/api/models';
import { QueueApi } from '../../core/api/queue.api';
import { ApiError, errorMessage } from '../../core/http/api-error';
import { formatSeconds, policySummary } from '../../core/i18n/labels';
import { EnvironmentService } from '../../core/state/environment.service';
import { EnvironmentStore } from '../../core/state/environment.store';
import { injectLiveRefresh } from '../../core/state/polling';
import { StatusBadge } from '../../shared/components/status-badge';
import { WaitingList } from './waiting-list';

@Component({
  selector: 'app-admin-page',
  imports: [
    RouterLink,
    HlmAlertDialogImports,
    HlmButtonImports,
    HlmCardImports,
    HlmFieldImports,
    HlmInputImports,
    HlmNativeSelectImports,
    HlmSkeletonImports,
    StatusBadge,
    WaitingList,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (queue.value(); as q) {
      <header class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p class="font-mono text-xs tracking-wider text-muted-foreground uppercase">Fila · prefixo {{ q.ticketPrefix }}</p>
          <h1 class="mt-0.5 flex flex-wrap items-center gap-2.5 text-xl font-semibold">
            {{ q.name }} <app-status-badge [status]="q.status" />
          </h1>
          <p class="mt-0.5 text-sm text-muted-foreground">
            {{ policy() }} · comparecimento em até {{ q.policy.callTimeoutSeconds }}s ·
            {{ q.policy.maxCallAttempts }} {{ q.policy.maxCallAttempts === 1 ? 'chamada' : 'chamadas' }} antes de “não compareceu”
          </p>
        </div>
        @if (q.status !== 'CLOSED') {
          <div class="flex gap-2">
            <button hlmBtn variant="outline" [disabled]="statusBusy()" (click)="changeStatus(q.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE')">
              {{ q.status === 'ACTIVE' ? 'Pausar fila' : 'Reabrir fila' }}
            </button>
            <hlm-alert-dialog>
              <button hlmBtn variant="destructive" hlmAlertDialogTrigger [disabled]="statusBusy()">Encerrar fila</button>
              <hlm-alert-dialog-content *hlmAlertDialogPortal="let ctx">
                <hlm-alert-dialog-header>
                  <h2 hlmAlertDialogTitle>Encerrar “{{ q.name }}”?</h2>
                  <p hlmAlertDialogDescription>
                    Uma fila encerrada não volta a abrir: não recebe novas senhas nem permite chamadas.
                  </p>
                </hlm-alert-dialog-header>
                <hlm-alert-dialog-footer>
                  <button hlmAlertDialogCancel>Manter aberta</button>
                  <button hlmAlertDialogAction variant="destructive" (click)="ctx.close(); changeStatus('CLOSED')">Encerrar fila</button>
                </hlm-alert-dialog-footer>
              </hlm-alert-dialog-content>
            </hlm-alert-dialog>
          </div>
        }
      </header>

      @if (q.status === 'PAUSED') {
        <p class="mt-3 rounded-md border border-signal/60 bg-signal/15 px-3 py-1.5 text-sm">
          Fila pausada: o totem não emite senhas, mas os guichês continuam chamando quem já está aguardando.
        </p>
      }

      <dl class="mt-4 grid grid-cols-2 gap-px overflow-hidden rounded-lg border bg-border sm:grid-cols-4">
        @for (m of metricItems(); track m.label) {
          <div class="bg-card px-3 py-2">
            <dt class="text-xs text-muted-foreground">{{ m.label }}</dt>
            <dd class="font-mono text-lg font-semibold tabular-nums">{{ m.value }}</dd>
          </div>
        }
      </dl>

      <div class="mt-5 grid gap-4 lg:grid-cols-[minmax(0,1fr)_18rem]">
        <section>
          <div class="mb-2 flex items-baseline justify-between">
            <h2 class="text-base font-semibold">Fila de espera</h2>
            <span class="text-xs text-muted-foreground">na ordem de chamada</span>
          </div>
          <app-waiting-list [entries]="waiting.value()" [classifications]="q.classifications" (changed)="refresh()" />
        </section>

        <aside class="flex flex-col gap-4">
          <section hlmCard>
            <div hlmCardHeader>
              <h2 hlmCardTitle>Emitir senha na recepção</h2>
              <p hlmCardDescription>Inclui prioridades restritas, que o totem não oferece.</p>
            </div>
            <div hlmCardContent class="flex flex-col gap-3">
              <div hlmField>
                <label hlmFieldLabel for="r-priority">Prioridade</label>
                <hlm-native-select selectId="r-priority" [value]="priority()" (valueChange)="priority.set($event ?? 'NORMAL')">
                  @for (c of q.classifications; track c.code) {
                    <option hlmNativeSelectOption [value]="c.code">{{ c.label }}{{ c.restricted ? ' (restrita)' : '' }}</option>
                  }
                </hlm-native-select>
              </div>
              <div hlmField>
                <label hlmFieldLabel for="r-ref">Referência externa</label>
                <input hlmInput id="r-ref" placeholder="Prontuário, pedido…" [value]="reference()" (input)="reference.set(value($event))" />
              </div>
            </div>
            <div hlmCardFooter>
              <button hlmBtn class="w-full" [disabled]="issuing() || q.status !== 'ACTIVE'" (click)="issue()">Emitir senha</button>
            </div>
          </section>

          <section hlmCard>
            <div hlmCardHeader>
              <h2 hlmCardTitle>Guichês</h2>
            </div>
            <div hlmCardContent class="flex flex-col gap-1">
              @for (c of counters(); track c.operatorId) {
                <a
                  [routerLink]="['/operador', c.operatorId]"
                  class="-mx-2 flex items-center justify-between rounded-md px-2 py-1.5 text-sm hover:bg-muted"
                >
                  <span class="font-medium">{{ c.servicePointName }}</span>
                  <span class="text-muted-foreground">{{ c.operatorName }}</span>
                </a>
              } @empty {
                <p class="text-sm text-muted-foreground">Cadastre um guichê para começar a chamar.</p>
              }
            </div>
            <div hlmCardFooter class="flex flex-col gap-3">
              <div class="grid w-full grid-cols-2 gap-2">
                <input hlmInput aria-label="Nome do guichê" [value]="counterName()" (input)="counterName.set(value($event))" />
                <input hlmInput aria-label="Nome do atendente" [value]="operatorName()" (input)="operatorName.set(value($event))" />
              </div>
              <button hlmBtn variant="outline" class="w-full" [disabled]="addingCounter()" (click)="addCounter()">Adicionar guichê</button>
            </div>
          </section>
        </aside>
      </div>
    } @else if (queue.error(); as error) {
      <div hlmCard class="max-w-lg">
        <div hlmCardHeader>
          <h1 hlmCardTitle>Não foi possível carregar a fila</h1>
          <p hlmCardDescription>{{ message(error) }}</p>
        </div>
        <div hlmCardFooter class="gap-2">
          <button hlmBtn (click)="refresh()">Tentar de novo</button>
          <a hlmBtn variant="ghost" routerLink="/configuracoes">Revisar ambiente</a>
        </div>
      </div>
    } @else {
      <div class="flex flex-col gap-4">
        <hlm-skeleton class="h-8 w-64" />
        <hlm-skeleton class="h-20 w-full" />
        <hlm-skeleton class="h-64 w-full" />
      </div>
    }
  `,
})
export class AdminPage {
  private readonly store = inject(EnvironmentStore);
  private readonly environment = inject(EnvironmentService);
  private readonly queueApi = inject(QueueApi);

  protected readonly queue = this.queueApi.queueResource(this.store.queueId);
  protected readonly waiting = this.queueApi.waitingResource(this.store.queueId);
  protected readonly metrics = this.queueApi.metricsResource(this.store.queueId);
  protected readonly counters = this.store.counters;

  protected readonly priority = signal('NORMAL');
  protected readonly reference = signal('');
  protected readonly issuing = signal(false);
  protected readonly statusBusy = signal(false);
  protected readonly counterName = signal('');
  protected readonly operatorName = signal('');
  protected readonly addingCounter = signal(false);

  protected readonly policy = computed(() => {
    const q = this.queue.value();
    return q ? policySummary(q.policy) : '';
  });

  protected readonly metricItems = computed(() => {
    const m = this.metrics.value();
    return [
      { label: 'Aguardando', value: m?.waiting ?? '—' },
      { label: 'Chamadas agora', value: m?.called ?? '—' },
      { label: 'Em atendimento', value: m?.inService ?? '—' },
      { label: 'Concluídas hoje', value: m?.completed ?? '—' },
      { label: 'Não compareceram', value: m?.noShow ?? '—' },
      { label: 'Canceladas', value: m?.cancelled ?? '—' },
      { label: 'Espera média', value: formatSeconds(m?.averageWaitSeconds) },
      { label: 'Atendimento médio', value: formatSeconds(m?.averageServiceSeconds) },
    ];
  });

  constructor() {
    injectLiveRefresh(() => this.refresh());
    this.resetCounterForm();
  }

  protected refresh(): void {
    this.queue.reload();
    this.waiting.reload();
    this.metrics.reload();
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected message(error: unknown): string {
    return errorMessage(error);
  }

  protected async changeStatus(status: QueueStatus): Promise<void> {
    this.statusBusy.set(true);
    try {
      const updated = await this.queueApi.changeStatus(this.store.require().queueId, status);
      this.queue.set(updated);
      toast.success(status === 'ACTIVE' ? 'Fila reaberta.' : status === 'PAUSED' ? 'Fila pausada.' : 'Fila encerrada.');
    } catch {
      // erro já exibido
    } finally {
      this.statusBusy.set(false);
    }
  }

  protected async issue(): Promise<void> {
    const env = this.store.require();
    const code = this.priority();
    this.issuing.set(true);
    try {
      const entry = await this.queueApi.enqueue(
        env.queueId,
        {
          serviceId: env.serviceId,
          priorityClassification: code === 'NORMAL' ? null : code,
          externalReference: this.reference().trim() || null,
        },
        'admin',
      );
      this.reference.set('');
      toast.success(`Senha ${entry.ticket} emitida.`);
      this.refresh();
    } catch {
      // erro já exibido
    } finally {
      this.issuing.set(false);
    }
  }

  protected async addCounter(): Promise<void> {
    this.addingCounter.set(true);
    try {
      const counter = await this.environment.addCounter(
        this.counterName().trim() || this.defaultCounterName(),
        this.operatorName().trim() || 'Atendente',
      );
      toast.success(`${counter.servicePointName} cadastrado.`);
      this.resetCounterForm();
    } catch (error) {
      if (!(error instanceof ApiError)) toast.error(errorMessage(error));
    } finally {
      this.addingCounter.set(false);
    }
  }

  private defaultCounterName(): string {
    return `Guichê ${this.counters().length + 1}`;
  }

  private resetCounterForm(): void {
    this.counterName.set(this.defaultCounterName());
    this.operatorName.set(`Atendente ${this.counters().length + 1}`);
  }
}
