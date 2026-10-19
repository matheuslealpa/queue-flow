import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmEmptyImports } from '@spartan-ng/helm/empty';
import { EnvironmentStore } from '../../core/state/environment.store';

@Component({
  selector: 'app-counters-page',
  imports: [RouterLink, HlmButtonImports, HlmCardImports, HlmEmptyImports],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="text-xl font-semibold">Guichês</h1>
    <p class="mt-1 text-sm text-muted-foreground">Abra cada guichê numa aba ou num computador do atendimento.</p>

    @if (counters().length) {
      <div class="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        @for (c of counters(); track c.operatorId) {
          <a hlmCard [routerLink]="['/operador', c.operatorId]" class="transition-colors hover:border-ring">
            <div hlmCardHeader>
              <h2 hlmCardTitle>{{ c.servicePointName }}</h2>
              <p hlmCardDescription>{{ c.operatorName }}</p>
            </div>
            <div hlmCardContent>
              <p class="truncate font-mono text-[11px] text-muted-foreground" [title]="c.operatorId">{{ c.operatorId }}</p>
            </div>
          </a>
        }
      </div>
    } @else {
      <div hlmEmpty class="mt-6 border border-dashed">
        <div hlmEmptyHeader>
          <h2 hlmEmptyTitle>Nenhum guichê cadastrado</h2>
          <p hlmEmptyDescription>Cadastre o primeiro na tela da fila.</p>
        </div>
        <div hlmEmptyContent><a hlmBtn routerLink="/admin">Ir para a fila</a></div>
      </div>
    }
  `,
})
export class CountersPage {
  protected readonly counters = inject(EnvironmentStore).counters;
}
