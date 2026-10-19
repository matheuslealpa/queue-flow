import { inject } from '@angular/core';
import { Router, type CanActivateFn, type Routes } from '@angular/router';
import { EnvironmentStore } from './core/state/environment.store';

/** Sem ambiente (organização + fila) não há o que mostrar: manda para o setup. */
const requireEnvironment: CanActivateFn = () =>
  inject(EnvironmentStore).configured() || inject(Router).createUrlTree(['/setup']);

export const routes: Routes = [
  {
    path: 'setup',
    title: 'Configurar ambiente · Queue Flow',
    loadComponent: () => import('./features/setup/setup-page').then((m) => m.SetupPage),
  },
  {
    path: 'totem',
    title: 'Totem · Queue Flow',
    canActivate: [requireEnvironment],
    loadComponent: () => import('./features/kiosk/kiosk-page').then((m) => m.KioskPage),
  },
  {
    path: 'painel',
    title: 'Painel · Queue Flow',
    canActivate: [requireEnvironment],
    loadComponent: () => import('./features/board/board-page').then((m) => m.BoardPage),
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'admin' },
      {
        path: 'admin',
        title: 'Fila · Queue Flow',
        canActivate: [requireEnvironment],
        loadComponent: () => import('./features/admin/admin-page').then((m) => m.AdminPage),
      },
      {
        path: 'operador',
        title: 'Guichês · Queue Flow',
        canActivate: [requireEnvironment],
        loadComponent: () => import('./features/operator/counters-page').then((m) => m.CountersPage),
      },
      {
        path: 'operador/:operatorId',
        title: 'Guichê · Queue Flow',
        canActivate: [requireEnvironment],
        loadComponent: () => import('./features/operator/operator-page').then((m) => m.OperatorPage),
      },
      {
        path: 'configuracoes',
        title: 'Configurações · Queue Flow',
        loadComponent: () => import('./features/settings/settings-page').then((m) => m.SettingsPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
