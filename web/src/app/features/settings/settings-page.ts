import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { toast } from '@spartan-ng/brain/sonner';
import { HlmAlertDialogImports } from '@spartan-ng/helm/alert-dialog';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmNativeSelectImports } from '@spartan-ng/helm/native-select';
import { HlmSwitchImports } from '@spartan-ng/helm/switch';
import { EnvironmentStore, type Environment } from '../../core/state/environment.store';
import { SettingsStore, type Theme } from '../../core/state/settings.store';

const POLLING_OPTIONS = [2, 3, 5, 10, 30];

function parseEnvironment(json: string): Environment {
  const data = JSON.parse(json) as Partial<Environment>;
  const required = ['organizationId', 'adminOperatorId', 'queueId'] as const;
  for (const key of required) {
    if (typeof data[key] !== 'string' || !data[key]) throw new Error(`Campo "${key}" ausente.`);
  }
  return {
    organizationId: data.organizationId!,
    adminOperatorId: data.adminOperatorId!,
    queueId: data.queueId!,
    locationId: data.locationId ?? null,
    serviceId: data.serviceId ?? null,
    counters: Array.isArray(data.counters) ? data.counters : [],
  };
}

@Component({
  selector: 'app-settings-page',
  imports: [
    HlmAlertDialogImports,
    HlmButtonImports,
    HlmCardImports,
    HlmFieldImports,
    HlmInputImports,
    HlmNativeSelectImports,
    HlmSwitchImports,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="text-xl font-semibold">Configurações</h1>
    <p class="mt-1 text-sm text-muted-foreground">Valem só para este navegador.</p>

    <div class="mt-4 grid gap-4 lg:grid-cols-2">
      <section hlmCard>
        <div hlmCardHeader>
          <h2 hlmCardTitle>Telas</h2>
        </div>
        <div hlmCardContent class="flex flex-col gap-4">
          <div hlmField>
            <label hlmFieldLabel for="s-polling">Sem tempo real, consultar a cada</label>
            <hlm-native-select selectId="s-polling" [value]="'' + settings().pollingSeconds" (valueChange)="store.update({ pollingSeconds: +($event ?? 3) })">
              @for (s of pollingOptions; track s) {
                <option hlmNativeSelectOption [value]="'' + s">{{ s }} segundos</option>
              }
            </hlm-native-select>
            <p hlmFieldDescription>As telas recebem as mudanças da fila na hora. Se a conexão cair, consultam nesse intervalo até ela voltar.</p>
          </div>

          <div hlmField>
            <label hlmFieldLabel for="s-theme">Tema</label>
            <hlm-native-select selectId="s-theme" [value]="settings().theme" (valueChange)="setTheme($event)">
              <option hlmNativeSelectOption value="system">Igual ao sistema</option>
              <option hlmNativeSelectOption value="light">Claro</option>
              <option hlmNativeSelectOption value="dark">Escuro</option>
            </hlm-native-select>
          </div>

          <div hlmField>
            <label hlmFieldLabel for="s-channel">Canal do totem</label>
            <input hlmInput id="s-channel" class="font-mono" [value]="settings().kioskChannel" (change)="store.update({ kioskChannel: value($event) })" />
            <p hlmFieldDescription>Enviado em X-Client-Channel nas senhas retiradas no totem.</p>
          </div>

          <div hlmField orientation="horizontal" class="flex items-center justify-between gap-4">
            <div>
              <label hlmFieldLabel for="s-sound">Som no painel</label>
              <p hlmFieldDescription>Toca um aviso a cada nova chamada.</p>
            </div>
            <hlm-switch inputId="s-sound" [checked]="settings().boardSound" (checkedChange)="store.update({ boardSound: $event })" />
          </div>
        </div>
      </section>

      <section hlmCard>
        <div hlmCardHeader>
          <h2 hlmCardTitle>Ambiente</h2>
          <p hlmCardDescription>Copie este JSON para abrir os mesmos guichês, totem e painel em outro navegador.</p>
        </div>
        <div hlmCardContent class="flex flex-col gap-3">
          <textarea
            class="min-h-56 w-full rounded-md border border-input bg-transparent p-3 font-mono text-xs leading-relaxed focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
            aria-label="Ambiente em JSON"
            spellcheck="false"
            [value]="draft()"
            (input)="draft.set(value($event))"
          ></textarea>
        </div>
        <div hlmCardFooter class="flex flex-wrap gap-2">
          <button hlmBtn [disabled]="!dirty()" (click)="importEnvironment()">Aplicar JSON</button>
          <button hlmBtn variant="outline" [disabled]="!environment()" (click)="copy()">Copiar</button>
          @if (environment()) {
            <hlm-alert-dialog>
              <button hlmBtn variant="destructive" hlmAlertDialogTrigger class="ms-auto">Esquecer ambiente</button>
              <hlm-alert-dialog-content *hlmAlertDialogPortal="let ctx">
                <hlm-alert-dialog-header>
                  <h2 hlmAlertDialogTitle>Esquecer este ambiente?</h2>
                  <p hlmAlertDialogDescription>
                    Os dados continuam na API, mas este navegador perde os IDs e a lista de guichês.
                    Copie o JSON antes se quiser voltar a ele.
                  </p>
                </hlm-alert-dialog-header>
                <hlm-alert-dialog-footer>
                  <button hlmAlertDialogCancel>Manter</button>
                  <button hlmAlertDialogAction variant="destructive" (click)="ctx.close(); forget()">Esquecer ambiente</button>
                </hlm-alert-dialog-footer>
              </hlm-alert-dialog-content>
            </hlm-alert-dialog>
          }
        </div>
      </section>
    </div>
  `,
})
export class SettingsPage {
  protected readonly store = inject(SettingsStore);
  private readonly environmentStore = inject(EnvironmentStore);
  private readonly router = inject(Router);

  protected readonly pollingOptions = POLLING_OPTIONS;
  protected readonly settings = this.store.settings;
  protected readonly environment = this.environmentStore.environment;

  private readonly saved = computed(() => {
    const env = this.environment();
    return env ? JSON.stringify(env, null, 2) : '';
  });
  protected readonly draft = signal(this.saved());
  protected readonly dirty = computed(() => this.draft().trim() !== this.saved().trim() && !!this.draft().trim());

  protected value(event: Event): string {
    return (event.target as HTMLInputElement | HTMLTextAreaElement).value;
  }

  protected setTheme(theme: string | null | undefined): void {
    this.store.update({ theme: (theme ?? 'system') as Theme });
  }

  protected importEnvironment(): void {
    try {
      const env = parseEnvironment(this.draft());
      this.environmentStore.connect(env);
      this.draft.set(this.saved());
      toast.success('Ambiente aplicado.');
    } catch (error) {
      toast.error(`JSON inválido: ${error instanceof Error ? error.message : error}`);
    }
  }

  protected async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.saved());
      toast.success('JSON copiado.');
    } catch {
      toast.error('O navegador bloqueou a área de transferência. Selecione o texto e copie manualmente.');
    }
  }

  protected forget(): void {
    this.environmentStore.forget();
    this.draft.set('');
    toast.success('Ambiente esquecido.');
    void this.router.navigateByUrl('/setup');
  }
}
