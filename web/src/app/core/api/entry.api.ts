import { HttpClient, httpResource } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { as, type Identity } from '../http/identity';
import type { EntryResponse } from './models';
import { API } from './queue.api';

/** Ações de atendimento sobre uma senha já chamada. */
export type EntryAction = 'start' | 'complete' | 'no-show' | 'return' | 'cancel';

@Injectable({ providedIn: 'root' })
export class EntryApi {
  private readonly http = inject(HttpClient);

  entryResource(entryId: () => string | undefined) {
    return httpResource<EntryResponse>(() => {
      const id = entryId();
      return id ? `${API}/entries/${id}` : undefined;
    });
  }

  act(entryId: string, action: EntryAction, identity: Identity, reason?: string): Promise<EntryResponse> {
    const body = action === 'return' || action === 'cancel' ? { reason: reason ?? null } : null;
    return firstValueFrom(
      this.http.post<EntryResponse>(`${API}/entries/${entryId}/${action}`, body, { context: as(identity) }),
    );
  }

  changePriority(entryId: string, priorityClassification: string): Promise<EntryResponse> {
    return firstValueFrom(
      this.http.post<EntryResponse>(`${API}/entries/${entryId}/priority`, { priorityClassification }),
    );
  }
}
