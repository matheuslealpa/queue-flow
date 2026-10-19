import { HttpClient, httpResource } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { as, type Identity } from '../http/identity';
import type { CreateQueueRequest, EntryResponse, QueueMetrics, QueueResponse, QueueStatus } from './models';

export const API = '/api/v1';

export interface EnqueueRequest {
  serviceId?: string | null;
  priorityClassification?: string | null;
  externalReference?: string | null;
}

@Injectable({ providedIn: 'root' })
export class QueueApi {
  private readonly http = inject(HttpClient);

  /** Leituras reativas: recarregam quando `queueId()` muda e com `reload()` no polling. */
  queueResource(queueId: () => string | undefined) {
    return httpResource<QueueResponse>(() => {
      const id = queueId();
      return id ? `${API}/queues/${id}` : undefined;
    });
  }

  waitingResource(queueId: () => string | undefined, limit = 50) {
    return httpResource<EntryResponse[]>(
      () => {
        const id = queueId();
        return id ? { url: `${API}/queues/${id}/entries`, params: { limit } } : undefined;
      },
      { defaultValue: [] },
    );
  }

  metricsResource(queueId: () => string | undefined) {
    return httpResource<QueueMetrics>(() => {
      const id = queueId();
      return id ? `${API}/queues/${id}/metrics` : undefined;
    });
  }

  waitingList(queueId: string, limit = 200): Promise<EntryResponse[]> {
    return firstValueFrom(this.http.get<EntryResponse[]>(`${API}/queues/${queueId}/entries`, { params: { limit } }));
  }

  get(queueId: string): Promise<QueueResponse> {
    return firstValueFrom(this.http.get<QueueResponse>(`${API}/queues/${queueId}`));
  }

  create(request: CreateQueueRequest): Promise<QueueResponse> {
    return firstValueFrom(this.http.post<QueueResponse>(`${API}/queues`, request));
  }

  changeStatus(queueId: string, status: QueueStatus): Promise<QueueResponse> {
    return firstValueFrom(this.http.put<QueueResponse>(`${API}/queues/${queueId}/status`, { status }));
  }

  enqueue(queueId: string, request: EnqueueRequest, identity: Identity): Promise<EntryResponse> {
    return firstValueFrom(
      this.http.post<EntryResponse>(`${API}/queues/${queueId}/entries`, request, {
        context: as(identity),
        headers: { 'Idempotency-Key': crypto.randomUUID() },
      }),
    );
  }

  /** Resolve `null` quando ninguém está aguardando (204). */
  async callNext(queueId: string, servicePointId: string, operatorId: string): Promise<EntryResponse | null> {
    const response = await firstValueFrom(
      this.http.post<EntryResponse>(
        `${API}/queues/${queueId}/calls/next`,
        { servicePointId },
        { context: as({ operatorId }), observe: 'response' },
      ),
    );
    return response.status === 204 ? null : response.body;
  }
}
