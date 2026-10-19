import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Código da senha em fonte de matriz de pontos, o mesmo traço do painel. */
@Component({
  selector: 'app-ticket',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'font-display font-black tracking-[0.08em] tabular-nums leading-none',
    '[attr.aria-label]': '"Senha " + code()',
  },
  template: `{{ code() }}`,
})
export class Ticket {
  readonly code = input.required<string>();
}
