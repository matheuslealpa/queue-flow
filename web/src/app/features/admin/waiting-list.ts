import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { toast } from '@spartan-ng/brain/sonner';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmEmptyImports } from '@spartan-ng/helm/empty';
import { HlmNativeSelectImports } from '@spartan-ng/helm/native-select';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { EntryApi } from '../../core/api/entry.api';
import type { Classification, EntryResponse } from '../../core/api/models';
import { elapsedSince } from '../../core/i18n/labels';
import { injectNow } from '../../core/state/polling';
import { Ticket } from '../../shared/components/ticket';

/** Senhas aguardando, já na ordem em que serão chamadas (a API projeta a intercalação). */
@Component({
  selector: 'app-waiting-list',
  imports: [HlmTableImports, HlmButtonImports, HlmNativeSelectImports, HlmEmptyImports, Ticket],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (entries().length) {
      <div hlmTableContainer>
        <table hlmTable>
          <thead hlmTHead>
            <tr hlmTr>
              <th hlmTh class="w-10">#</th>
              <th hlmTh>Senha</th>
              <th hlmTh>Prioridade</th>
              <th hlmTh>Aguardando</th>
              <th hlmTh class="text-center" title="Chamadas sem comparecimento">Perdidas</th>
              <th hlmTh class="text-end"><span class="sr-only">Ações</span></th>
            </tr>
          </thead>
          <tbody hlmTBody>
            @for (entry of entries(); track entry.id; let i = $index) {
              <tr hlmTr>
                <td hlmTd class="font-mono text-xs text-muted-foreground">{{ i + 1 }}</td>
                <td hlmTd><app-ticket class="text-lg" [code]="entry.ticket" /></td>
                <td hlmTd>
                  <span class="text-sm" [class.font-semibold]="entry.priority.level > 0">{{ label(entry) }}</span>
                  @if (entry.priority.level > 0) {
                    <span class="ms-1 font-mono text-xs text-muted-foreground">nível {{ entry.priority.level }}</span>
                  }
                </td>
                <td hlmTd class="font-mono text-xs tabular-nums">{{ elapsed(entry) }}</td>
                <td hlmTd class="text-center font-mono text-xs" [class.text-called]="entry.missedCalls > 0">{{ entry.missedCalls }}</td>
                <td hlmTd>
                  <div class="flex items-center justify-end gap-2">
                    <hlm-native-select
                      size="sm"
                      class="w-40"
                      [value]="''"
                      [disabled]="busy() === entry.id"
                      [attr.aria-label]="'Alterar prioridade de ' + entry.ticket"
                      (valueChange)="changePriority(entry, $event)"
                    >
                      <option hlmNativeSelectOption value="">Prioridade…</option>
                      @for (c of classifications(); track c.code) {
                        @if (c.code !== entry.priority.classification) {
                          <option hlmNativeSelectOption [value]="c.code">{{ c.label }}</option>
                        }
                      }
                    </hlm-native-select>
                    <button hlmBtn variant="destructive" size="sm" [disabled]="busy() === entry.id" (click)="cancel(entry)">
                      Cancelar
                    </button>
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    } @else {
      <div hlmEmpty class="border border-dashed py-6">
        <div hlmEmptyHeader>
          <h3 hlmEmptyTitle>Ninguém aguardando</h3>
          <p hlmEmptyDescription>Emita uma senha pela recepção ao lado ou pelo totem.</p>
        </div>
      </div>
    }
  `,
})
export class WaitingList {
  private readonly entryApi = inject(EntryApi);

  readonly entries = input.required<EntryResponse[]>();
  readonly classifications = input.required<Classification[]>();
  readonly changed = output<void>();

  protected readonly now = injectNow();
  protected readonly busy = signal<string | null>(null);

  protected label(entry: EntryResponse): string {
    return this.classifications().find((c) => c.code === entry.priority.classification)?.label
      ?? entry.priority.classification;
  }

  protected elapsed(entry: EntryResponse): string {
    return elapsedSince(entry.enqueuedAt, this.now());
  }

  protected async changePriority(entry: EntryResponse, code: string | null | undefined): Promise<void> {
    if (!code) return;
    await this.run(entry, async () => {
      await this.entryApi.changePriority(entry.id, code);
      toast.success(`Prioridade de ${entry.ticket} alterada.`);
    });
  }

  protected async cancel(entry: EntryResponse): Promise<void> {
    await this.run(entry, async () => {
      await this.entryApi.act(entry.id, 'cancel', 'admin', 'Cancelada pela recepção');
      toast.success(`Senha ${entry.ticket} cancelada.`);
    });
  }

  private async run(entry: EntryResponse, action: () => Promise<void>): Promise<void> {
    this.busy.set(entry.id);
    try {
      await action();
    } catch {
      // o interceptor já mostrou o erro
    } finally {
      this.busy.set(null);
      this.changed.emit();
    }
  }
}
