import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HlmToasterImports } from '@spartan-ng/helm/sonner';
import { SettingsStore } from './core/state/settings.store';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, HlmToasterImports],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <router-outlet />
    <hlm-toaster position="bottom-right" richColors />
  `,
})
export class App {
  // instancia cedo para aplicar o tema antes da primeira tela
  protected readonly settings = inject(SettingsStore);
}
