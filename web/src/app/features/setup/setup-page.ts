import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { toast } from '@spartan-ng/brain/sonner';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';
import { errorMessage } from '../../core/http/api-error';
import { EnvironmentService } from '../../core/state/environment.service';

@Component({
  selector: 'app-setup-page',
  imports: [HlmButtonImports, HlmCardImports, HlmFieldImports, HlmInputImports, HlmSpinnerImports],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="min-h-svh bg-board px-4 py-12 text-[#C9D2DD] md:py-20">
      <div class="mx-auto max-w-4xl">
        <p class="font-display text-5xl font-black tracking-[0.12em] text-signal md:text-7xl" aria-hidden="true">A001</p>
        <h1 class="mt-6 text-2xl font-semibold text-white md:text-3xl">Monte o ambiente da fila</h1>
        <p class="mt-2 max-w-xl text-sm leading-relaxed">
          A API ainda não lista organizações nem filas, então este navegador guarda os IDs que você criar ou informar.
          O painel, o totem e os guichês abertos em outras abas usam o mesmo ambiente.
        </p>

        <div class="mt-10 grid gap-6 md:grid-cols-2">
          <section hlmCard>
            <div hlmCardHeader>
              <h2 hlmCardTitle>Criar demonstração</h2>
              <p hlmCardDescription>
                Cria organização, unidade, serviço, a fila A com intercalação 2 prioritárias : 1 normal e dois guichês.
              </p>
            </div>
            <div hlmCardContent>
              <div hlmField>
                <label hlmFieldLabel for="org-name">Nome da organização</label>
                <input hlmInput id="org-name" [value]="organizationName()" (input)="organizationName.set(value($event))" />
              </div>
            </div>
            <div hlmCardFooter>
              <button hlmBtn class="w-full" [disabled]="busy()" (click)="createDemo()">
                @if (busy() === 'demo') { <hlm-spinner /> Criando ambiente… } @else { Criar ambiente }
              </button>
            </div>
          </section>

          <section hlmCard>
            <div hlmCardHeader>
              <h2 hlmCardTitle>Conectar a uma fila existente</h2>
              <p hlmCardDescription>Informe os IDs devolvidos pela API. Guichês serão cadastrados depois, na tela da fila.</p>
            </div>
            <div hlmCardContent class="flex flex-col gap-4">
              <div hlmField>
                <label hlmFieldLabel for="ex-org">Organização (X-Organization-Id)</label>
                <input hlmInput id="ex-org" class="font-mono text-xs" [value]="organizationId()" (input)="organizationId.set(value($event))" />
              </div>
              <div hlmField>
                <label hlmFieldLabel for="ex-admin">Operador administrador</label>
                <input hlmInput id="ex-admin" class="font-mono text-xs" [value]="adminOperatorId()" (input)="adminOperatorId.set(value($event))" />
              </div>
              <div hlmField>
                <label hlmFieldLabel for="ex-queue">Fila</label>
                <input hlmInput id="ex-queue" class="font-mono text-xs" [value]="queueId()" (input)="queueId.set(value($event))" />
              </div>
            </div>
            <div hlmCardFooter>
              <button hlmBtn variant="outline" class="w-full" [disabled]="busy()" (click)="connect()">
                @if (busy() === 'connect') { <hlm-spinner /> Conectando… } @else { Conectar }
              </button>
            </div>
          </section>
        </div>
      </div>
    </div>
  `,
})
export class SetupPage {
  private readonly environment = inject(EnvironmentService);
  private readonly router = inject(Router);

  protected readonly organizationName = signal('Clínica Demonstração');
  protected readonly organizationId = signal('');
  protected readonly adminOperatorId = signal('');
  protected readonly queueId = signal('');
  protected readonly busy = signal<'demo' | 'connect' | null>(null);

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected async createDemo(): Promise<void> {
    await this.run('demo', () => this.environment.createDemo(this.organizationName().trim() || 'Clínica Demonstração'));
  }

  protected async connect(): Promise<void> {
    const ids = [this.organizationId(), this.adminOperatorId(), this.queueId()].map((id) => id.trim());
    if (ids.some((id) => !id)) {
      toast.error('Preencha os três IDs para conectar.');
      return;
    }
    await this.run('connect', () => this.environment.connectExisting(ids[0], ids[1], ids[2]));
  }

  private async run(kind: 'demo' | 'connect', action: () => Promise<void>): Promise<void> {
    this.busy.set(kind);
    try {
      await action();
      toast.success(kind === 'demo' ? 'Ambiente criado.' : 'Fila conectada.');
      await this.router.navigateByUrl('/admin');
    } catch (error) {
      // falhas de POST já viraram toast no interceptor
      if (kind === 'connect') toast.error(errorMessage(error));
    } finally {
      this.busy.set(null);
    }
  }
}
