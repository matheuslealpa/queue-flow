import { computed, Injectable } from '@angular/core';
import { persistedSignal } from './persisted';

/** Um guichê: ponto de atendimento com o atendente atribuído à fila. */
export interface Counter {
  operatorId: string;
  operatorName: string;
  servicePointId: string;
  servicePointName: string;
}

/**
 * IDs criados ou informados no setup. A API ainda não lista organizações, filas nem guichês,
 * então o front guarda o que conhece.
 */
export interface Environment {
  organizationId: string;
  adminOperatorId: string;
  queueId: string;
  locationId: string | null;
  serviceId: string | null;
  counters: Counter[];
}

export interface RecentCall {
  entryId: string;
  ticket: string;
  servicePointName: string;
  calledAt: string;
}

const RECENT_CALLS_KEPT = 6;

@Injectable({ providedIn: 'root' })
export class EnvironmentStore {
  readonly environment = persistedSignal<Environment | null>('queueflow.web.environment', null);
  /** operatorId → entryId da senha em mãos daquele guichê */
  readonly currentEntries = persistedSignal<Record<string, string>>('queueflow.web.current', {});
  readonly recentCalls = persistedSignal<RecentCall[]>('queueflow.web.recent-calls', []);

  readonly configured = computed(() => !!this.environment()?.queueId);
  readonly queueId = computed(() => this.environment()?.queueId || undefined);
  readonly counters = computed(() => this.environment()?.counters ?? []);

  require(): Environment {
    const env = this.environment();
    if (!env) throw new Error('Ambiente não configurado');
    return env;
  }

  connect(environment: Environment): void {
    this.environment.set(environment);
    this.currentEntries.set({});
    this.recentCalls.set([]);
  }

  forget(): void {
    this.environment.set(null);
    this.currentEntries.set({});
    this.recentCalls.set([]);
  }

  addCounter(counter: Counter): void {
    this.environment.update((env) => env && { ...env, counters: [...env.counters, counter] });
  }

  counter(operatorId: string): Counter | undefined {
    return this.counters().find((c) => c.operatorId === operatorId);
  }

  servicePointName(servicePointId: string): string {
    return this.counters().find((c) => c.servicePointId === servicePointId)?.servicePointName ?? 'Guichê';
  }

  hold(operatorId: string, entryId: string): void {
    this.currentEntries.update((current) => ({ ...current, [operatorId]: entryId }));
  }

  /** Idempotente: vários painéis abertos recebem o mesmo evento e gravam no mesmo histórico. */
  announce(call: RecentCall): void {
    this.recentCalls.update((calls) =>
      calls.some((c) => c.entryId === call.entryId && c.calledAt === call.calledAt)
        ? calls
        : [call, ...calls].slice(0, RECENT_CALLS_KEPT),
    );
  }
}
