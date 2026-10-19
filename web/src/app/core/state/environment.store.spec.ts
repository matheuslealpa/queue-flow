import { TestBed } from '@angular/core/testing';
import { EnvironmentStore, type RecentCall } from './environment.store';

const call = (n: number): RecentCall => ({
  entryId: `e${n}`,
  ticket: `A00${n}`,
  servicePointName: 'Guichê 1',
  calledAt: `2026-10-18T12:00:0${n}Z`,
});

describe('EnvironmentStore', () => {
  let store: EnvironmentStore;

  beforeEach(() => {
    localStorage.clear();
    store = TestBed.inject(EnvironmentStore);
  });

  it('só considera configurado com uma fila', () => {
    expect(store.configured()).toBe(false);
    store.connect({ organizationId: 'o', adminOperatorId: 'a', queueId: '', locationId: null, serviceId: null, counters: [] });
    expect(store.configured()).toBe(false);
    store.environment.update((env) => env && { ...env, queueId: 'q' });
    expect(store.configured()).toBe(true);
  });

  it('mantém as chamadas mais recentes primeiro e descarta as antigas', () => {
    for (let n = 1; n <= 8; n++) store.announce(call(n));
    expect(store.recentCalls().map((c) => c.ticket)).toEqual(['A008', 'A007', 'A006', 'A005', 'A004', 'A003']);
  });

  it('grava no localStorage para as outras abas', () => {
    store.announce(call(1));
    TestBed.tick();
    expect(JSON.parse(localStorage.getItem('queueflow.web.recent-calls')!)).toEqual([call(1)]);
  });

  it('esquecer o ambiente limpa senhas em mãos e chamadas', () => {
    store.hold('op', 'e1');
    store.announce(call(1));
    store.forget();
    expect(store.currentEntries()).toEqual({});
    expect(store.recentCalls()).toEqual([]);
  });
});
