import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EnvironmentStore } from '../state/environment.store';
import { SettingsStore } from '../state/settings.store';
import { as, identityInterceptor } from './identity';

describe('identityInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([identityInterceptor])), provideHttpClientTesting()],
    });
    TestBed.inject(EnvironmentStore).connect({
      organizationId: 'org-1',
      adminOperatorId: 'admin-1',
      queueId: 'queue-1',
      locationId: null,
      serviceId: null,
      counters: [],
    });
    TestBed.inject(SettingsStore).update({ kioskChannel: 'totem-recepcao' });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('usa o operador administrador por padrão', () => {
    http.get('/x').subscribe();
    const req = backend.expectOne('/x');
    expect(req.request.headers.get('X-Organization-Id')).toBe('org-1');
    expect(req.request.headers.get('X-Operator-Id')).toBe('admin-1');
  });

  it('identifica o totem pelo canal configurado, sem operador', () => {
    http.post('/x', {}, { context: as('kiosk') }).subscribe();
    const req = backend.expectOne('/x');
    expect(req.request.headers.get('X-Client-Channel')).toBe('totem-recepcao');
    expect(req.request.headers.has('X-Operator-Id')).toBe(false);
  });

  it('identifica o atendente do guichê', () => {
    http.post('/x', {}, { context: as({ operatorId: 'op-7' }) }).subscribe();
    expect(backend.expectOne('/x').request.headers.get('X-Operator-Id')).toBe('op-7');
  });

  it('não envia identidade ao criar a organização', () => {
    http.post('/x', {}, { context: as('anonymous') }).subscribe();
    expect(backend.expectOne('/x').request.headers.keys()).toEqual([]);
  });
});
