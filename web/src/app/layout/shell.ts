import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideExternalLink,
  lucideListOrdered,
  lucideMonitor,
  lucideSettings2,
  lucideTicket,
  lucideUserRound,
} from '@ng-icons/lucide';
import { HlmSidebarImports } from '@spartan-ng/helm/sidebar';
import { QueueEvents } from '../core/realtime/queue-events';
import { EnvironmentStore } from '../core/state/environment.store';

/** Moldura das telas da operação. Totem e painel ficam de fora: rodam em tela cheia. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgIcon, HlmSidebarImports],
  providers: [
    provideIcons({
      lucideListOrdered,
      lucideUserRound,
      lucideSettings2,
      lucideTicket,
      lucideMonitor,
      lucideExternalLink,
    }),
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <hlm-sidebar-wrapper>
      <hlm-sidebar>
        <hlm-sidebar-header class="px-4 pt-5 pb-3">
          <a routerLink="/" class="flex items-baseline gap-2 text-sidebar-accent-foreground">
            <span class="font-display text-2xl font-black tracking-widest text-signal">QF</span>
            <span class="text-sm font-semibold tracking-wide">Queue Flow</span>
          </a>
        </hlm-sidebar-header>

        <hlm-sidebar-content>
          <hlm-sidebar-group>
            <div hlmSidebarGroupLabel>Operação</div>
            <div hlmSidebarGroupContent>
              <ul hlmSidebarMenu>
                <li hlmSidebarMenuItem>
                  <a hlmSidebarMenuButton routerLink="/admin" routerLinkActive #fila="routerLinkActive" [isActive]="fila.isActive">
                    <ng-icon name="lucideListOrdered" /> Fila
                  </a>
                </li>
                <li hlmSidebarMenuItem>
                  <a hlmSidebarMenuButton routerLink="/operador" routerLinkActive #guiches="routerLinkActive" [isActive]="guiches.isActive">
                    <ng-icon name="lucideUserRound" /> Guichês
                  </a>
                  @if (counters().length) {
                    <ul hlmSidebarMenuSub>
                      @for (counter of counters(); track counter.operatorId) {
                        <li hlmSidebarMenuSubItem>
                          <a hlmSidebarMenuSubButton [routerLink]="['/operador', counter.operatorId]" routerLinkActive #c="routerLinkActive" [isActive]="c.isActive">
                            {{ counter.servicePointName }}
                          </a>
                        </li>
                      }
                    </ul>
                  }
                </li>
              </ul>
            </div>
          </hlm-sidebar-group>

          <hlm-sidebar-group>
            <div hlmSidebarGroupLabel>Telas públicas</div>
            <div hlmSidebarGroupContent>
              <ul hlmSidebarMenu>
                <li hlmSidebarMenuItem>
                  <a hlmSidebarMenuButton routerLink="/totem" target="_blank">
                    <ng-icon name="lucideTicket" /> Totem
                    <ng-icon name="lucideExternalLink" class="ms-auto opacity-60" />
                  </a>
                </li>
                <li hlmSidebarMenuItem>
                  <a hlmSidebarMenuButton routerLink="/painel" target="_blank">
                    <ng-icon name="lucideMonitor" /> Painel
                    <ng-icon name="lucideExternalLink" class="ms-auto opacity-60" />
                  </a>
                </li>
              </ul>
            </div>
          </hlm-sidebar-group>
        </hlm-sidebar-content>

        <hlm-sidebar-footer>
          <p class="flex items-center gap-2 px-2 text-xs text-sidebar-foreground/70" role="status">
            <span
              class="size-2 rounded-full"
              [class]="realtime() === 'live' ? 'bg-serving' : realtime() === 'connecting' ? 'bg-signal animate-pulse' : 'bg-called'"
              aria-hidden="true"
            ></span>
            {{ realtime() === 'live' ? 'Tempo real' : realtime() === 'connecting' ? 'Conectando…' : 'Sem conexão · atualizando a cada poucos segundos' }}
          </p>
          <ul hlmSidebarMenu>
            <li hlmSidebarMenuItem>
              <a hlmSidebarMenuButton routerLink="/configuracoes" routerLinkActive #cfg="routerLinkActive" [isActive]="cfg.isActive">
                <ng-icon name="lucideSettings2" /> Configurações
              </a>
            </li>
          </ul>
        </hlm-sidebar-footer>
      </hlm-sidebar>

      <main hlmSidebarInset>
        <header class="flex h-12 items-center gap-2 px-4 md:hidden">
          <button hlmSidebarTrigger></button>
          <span class="text-sm font-semibold">Queue Flow</span>
        </header>
        <div class="mx-auto w-full max-w-6xl px-4 py-4 md:px-6 md:py-5">
          <router-outlet />
        </div>
      </main>
    </hlm-sidebar-wrapper>
  `,
})
export class Shell {
  protected readonly counters = inject(EnvironmentStore).counters;
  protected readonly realtime = inject(QueueEvents).state;
}
