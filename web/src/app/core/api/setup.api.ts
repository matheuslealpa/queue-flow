import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { as } from '../http/identity';
import type {
  CreatedOrganization,
  LocationResponse,
  OperatorResponse,
  Permission,
  ServicePointResponse,
  ServiceResponse,
} from './models';
import { API } from './queue.api';

/** Cadastros da organização (todos exigem o operador administrador, exceto criar a organização). */
@Injectable({ providedIn: 'root' })
export class SetupApi {
  private readonly http = inject(HttpClient);

  createOrganization(name: string, adminName: string): Promise<CreatedOrganization> {
    return firstValueFrom(
      this.http.post<CreatedOrganization>(`${API}/organizations`, { name, adminName }, { context: as('anonymous') }),
    );
  }

  createLocation(name: string, timeZone: string): Promise<LocationResponse> {
    return firstValueFrom(this.http.post<LocationResponse>(`${API}/locations`, { name, timeZone }));
  }

  createService(code: string, name: string): Promise<ServiceResponse> {
    return firstValueFrom(this.http.post<ServiceResponse>(`${API}/services`, { code, name }));
  }

  createServicePoint(locationId: string, name: string): Promise<ServicePointResponse> {
    return firstValueFrom(this.http.post<ServicePointResponse>(`${API}/service-points`, { locationId, name }));
  }

  createOperator(name: string, permissions: Permission[]): Promise<OperatorResponse> {
    return firstValueFrom(this.http.post<OperatorResponse>(`${API}/operators`, { name, permissions }));
  }

  assign(operatorId: string, queueId: string, servicePointId?: string): Promise<OperatorResponse> {
    return firstValueFrom(
      this.http.post<OperatorResponse>(`${API}/operators/${operatorId}/assignments`, {
        queueId,
        servicePointId: servicePointId ?? null,
      }),
    );
  }
}
