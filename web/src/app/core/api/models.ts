/** Espelho dos DTOs de /api/v1 (pacotes *.api do módulo api). */

export type QueueStatus = 'ACTIVE' | 'PAUSED' | 'CLOSED';
export type Ordering = 'FIFO' | 'PRIORITY_FIFO';
export type ReturnPosition = 'KEEP_ORIGINAL' | 'END_OF_QUEUE';
export type EntryStatus = 'WAITING' | 'CALLED' | 'IN_SERVICE' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
export type Permission =
  | 'CALL_ENTRIES'
  | 'ASSIGN_RESTRICTED_PRIORITY'
  | 'CHANGE_PRIORITY'
  | 'CANCEL_ENTRY'
  | 'MANAGE_QUEUES'
  | 'MANAGE_ORGANIZATION';

export const TERMINAL_STATUSES: ReadonlySet<EntryStatus> = new Set(['COMPLETED', 'CANCELLED', 'NO_SHOW']);

export interface QueuePolicy {
  ordering: Ordering;
  callTimeoutSeconds: number;
  maxCallAttempts: number;
  returnPosition: ReturnPosition;
  allowReturnFromService: boolean;
  maxConsecutivePriorityCalls: number;
}

export interface Classification {
  code: string;
  label: string;
  level: number;
  restricted: boolean;
}

export interface QueueResponse {
  id: string;
  locationId: string;
  name: string;
  ticketPrefix: string;
  status: QueueStatus;
  policy: QueuePolicy;
  acceptedServices: string[];
  classifications: Classification[];
}

export interface QueueMetrics {
  queueId: string;
  operationalDate: string;
  waiting: number;
  called: number;
  inService: number;
  completed: number;
  cancelled: number;
  noShow: number;
  averageWaitSeconds: number | null;
  averageServiceSeconds: number | null;
}

export interface EntryCall {
  operatorId: string;
  servicePointId: string;
  calledAt: string;
  expiresAt: string;
}

export interface EntryResponse {
  id: string;
  queueId: string;
  ticket: string;
  operationalDate: string;
  status: EntryStatus;
  priority: { classification: string; level: number };
  serviceId: string | null;
  externalReference: string | null;
  createdAt: string;
  enqueuedAt: string;
  call: EntryCall | null;
  missedCalls: number;
  serviceSessions: number;
}

export interface CreatedOrganization {
  organizationId: string;
  adminOperatorId: string;
}

export interface LocationResponse {
  id: string;
  name: string;
  timeZone: string;
  active: boolean;
}

export interface ServiceResponse {
  id: string;
  code: string;
  name: string;
  active: boolean;
}

export interface ServicePointResponse {
  id: string;
  locationId: string;
  name: string;
  active: boolean;
}

export interface OperatorResponse {
  id: string;
  name: string;
  active: boolean;
  permissions: Permission[];
  assignments: { queueId: string; servicePointId: string | null }[];
}

export interface CreateQueueRequest {
  locationId: string;
  name: string;
  ticketPrefix: string;
  policy: QueuePolicy;
  acceptedServices?: string[];
  classifications?: Classification[];
}

/** RFC 9457 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  [extension: string]: unknown;
}
