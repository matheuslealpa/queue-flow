import type { QueuePolicy } from '../api/models';
import { elapsedSince, formatSeconds, policySummary } from './labels';

const policy = (patch: Partial<QueuePolicy>): QueuePolicy => ({
  ordering: 'PRIORITY_FIFO',
  callTimeoutSeconds: 60,
  maxCallAttempts: 2,
  returnPosition: 'KEEP_ORIGINAL',
  allowReturnFromService: true,
  maxConsecutivePriorityCalls: 2,
  ...patch,
});

describe('labels', () => {
  it('descreve a política de chamada', () => {
    expect(policySummary(policy({ ordering: 'FIFO' }))).toBe('Ordem de chegada');
    expect(policySummary(policy({ maxConsecutivePriorityCalls: 2 }))).toBe('Intercala 2 prioritárias : 1 normal');
    expect(policySummary(policy({ maxConsecutivePriorityCalls: 0 }))).toBe('Prioridade estrita');
  });

  it('formata durações', () => {
    expect(formatSeconds(null)).toBe('—');
    expect(formatSeconds(42.4)).toBe('42s');
    expect(formatSeconds(125)).toBe('2min 05s');
    expect(formatSeconds(3 * 3600 + 7 * 60)).toBe('3h07');
  });

  it('nunca mostra tempo negativo quando o relógio local está atrasado', () => {
    const now = Date.parse('2026-10-18T12:00:00Z');
    expect(elapsedSince('2026-10-18T12:00:30Z', now)).toBe('0s');
    expect(elapsedSince('2026-10-18T11:59:00Z', now)).toBe('1min 00s');
  });
});
