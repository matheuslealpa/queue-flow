import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { EntryStatus, QueueStatus } from '../../core/api/models';
import { ENTRY_STATUS_LABEL, QUEUE_STATUS_LABEL } from '../../core/i18n/labels';

const TONE: Record<EntryStatus | QueueStatus, string> = {
  WAITING: 'bg-muted text-muted-foreground',
  CALLED: 'bg-called/12 text-called ring-called/30',
  IN_SERVICE: 'bg-serving/12 text-serving ring-serving/30',
  COMPLETED: 'bg-done/15 text-done ring-done/30',
  CANCELLED: 'bg-done/15 text-done ring-done/30 line-through',
  NO_SHOW: 'bg-called/8 text-called/80 ring-called/20',
  ACTIVE: 'bg-serving/12 text-serving ring-serving/30',
  PAUSED: 'bg-signal/25 text-signal-foreground ring-signal/50 dark:text-signal',
  CLOSED: 'bg-done/15 text-done ring-done/30',
};

/** Pílula de estado de senha ou de fila, nas cores do painel. */
@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex' },
  template: `
    <span
      class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-transparent ring-inset"
      [class]="tone()"
    >
      <span class="size-1.5 rounded-full bg-current" aria-hidden="true"></span>
      {{ label() }}
    </span>
  `,
})
export class StatusBadge {
  readonly status = input.required<EntryStatus | QueueStatus>();

  protected readonly tone = computed(() => TONE[this.status()]);
  protected readonly label = computed(() => {
    const status = this.status();
    return status in ENTRY_STATUS_LABEL
      ? ENTRY_STATUS_LABEL[status as EntryStatus]
      : QUEUE_STATUS_LABEL[status as QueueStatus];
  });
}
