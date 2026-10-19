import type { EntryStatus, QueuePolicy, QueueStatus } from '../api/models';

export const ENTRY_STATUS_LABEL: Record<EntryStatus, string> = {
  WAITING: 'Aguardando',
  CALLED: 'Chamada',
  IN_SERVICE: 'Em atendimento',
  COMPLETED: 'Concluída',
  CANCELLED: 'Cancelada',
  NO_SHOW: 'Não compareceu',
};

export const QUEUE_STATUS_LABEL: Record<QueueStatus, string> = {
  ACTIVE: 'Recebendo senhas',
  PAUSED: 'Pausada',
  CLOSED: 'Encerrada',
};

export function policySummary(policy: QueuePolicy): string {
  if (policy.ordering === 'FIFO') return 'Ordem de chegada';
  return policy.maxConsecutivePriorityCalls > 0
    ? `Intercala ${policy.maxConsecutivePriorityCalls} prioritárias : 1 normal`
    : 'Prioridade estrita';
}

export function formatSeconds(seconds: number | null | undefined): string {
  if (seconds == null) return '—';
  const s = Math.round(seconds);
  if (s < 60) return `${s}s`;
  const m = Math.floor(s / 60);
  return m >= 60 ? `${Math.floor(m / 60)}h${String(m % 60).padStart(2, '0')}` : `${m}min ${String(s % 60).padStart(2, '0')}s`;
}

export function elapsedSince(iso: string, now: number): string {
  return formatSeconds(Math.max(0, (now - Date.parse(iso)) / 1000));
}

export function timeOf(iso: string): string {
  return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}
