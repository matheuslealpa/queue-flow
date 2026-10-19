import { inject, Injectable } from '@angular/core';
import { QueueApi } from '../api/queue.api';
import { SetupApi } from '../api/setup.api';
import { EnvironmentStore, type Counter } from './environment.store';

/** Fluxos de montagem do ambiente que encadeiam vários cadastros da API. */
@Injectable({ providedIn: 'root' })
export class EnvironmentService {
  private readonly store = inject(EnvironmentStore);
  private readonly setupApi = inject(SetupApi);
  private readonly queueApi = inject(QueueApi);

  /** Mesma sequência do console de demonstração: unidade, serviço, fila com intercalação 2:1 e dois guichês. */
  async createDemo(organizationName: string): Promise<void> {
    const org = await this.setupApi.createOrganization(organizationName, 'Recepção');
    // o interceptor já precisa da organização para os próximos cadastros
    this.store.connect({
      organizationId: org.organizationId,
      adminOperatorId: org.adminOperatorId,
      queueId: '',
      locationId: null,
      serviceId: null,
      counters: [],
    });
    try {
      const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'America/Sao_Paulo';
      const location = await this.setupApi.createLocation('Unidade Centro', timeZone);
      const service = await this.setupApi.createService('ATENDIMENTO', 'Atendimento geral');
      const queue = await this.queueApi.create({
        locationId: location.id,
        name: 'Atendimento geral',
        ticketPrefix: 'A',
        policy: {
          ordering: 'PRIORITY_FIFO',
          callTimeoutSeconds: 60,
          maxCallAttempts: 2,
          returnPosition: 'KEEP_ORIGINAL',
          allowReturnFromService: true,
          maxConsecutivePriorityCalls: 2,
        },
        acceptedServices: [service.id],
        classifications: [
          { code: 'PREFERENCIAL', label: 'Preferencial', level: 10, restricted: false },
          { code: 'URGENTE', label: 'Urgente', level: 50, restricted: true },
        ],
      });
      this.store.environment.update(
        (env) => env && { ...env, queueId: queue.id, locationId: location.id, serviceId: service.id },
      );
      await this.addCounter('Guichê 1', 'Bruno');
      await this.addCounter('Guichê 2', 'Carla');
    } catch (error) {
      this.store.forget();
      throw error;
    }
  }

  /** Conecta a uma fila existente. Os guichês precisam ser criados de novo, já que a API não os lista. */
  async connectExisting(organizationId: string, adminOperatorId: string, queueId: string): Promise<void> {
    this.store.connect({ organizationId, adminOperatorId, queueId, locationId: null, serviceId: null, counters: [] });
    try {
      const queue = await this.queueApi.get(queueId);
      this.store.environment.update(
        (env) => env && { ...env, locationId: queue.locationId, serviceId: queue.acceptedServices[0] ?? null },
      );
    } catch (error) {
      this.store.forget();
      throw error;
    }
  }

  /** Cria um ponto de atendimento e um atendente com permissão de chamar, atribuído a ele na fila. */
  async addCounter(servicePointName: string, operatorName: string): Promise<Counter> {
    const env = this.store.require();
    if (!env.locationId) throw new Error('A fila não tem unidade conhecida.');
    const servicePoint = await this.setupApi.createServicePoint(env.locationId, servicePointName);
    const operator = await this.setupApi.createOperator(operatorName, ['CALL_ENTRIES']);
    await this.setupApi.assign(operator.id, env.queueId, servicePoint.id);
    const counter: Counter = {
      operatorId: operator.id,
      operatorName,
      servicePointId: servicePoint.id,
      servicePointName,
    };
    this.store.addCounter(counter);
    return counter;
  }
}
