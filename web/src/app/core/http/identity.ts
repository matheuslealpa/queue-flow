import { HttpContext, HttpContextToken, type HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { EnvironmentStore, type Environment } from '../state/environment.store';
import { SettingsStore } from '../state/settings.store';

/**
 * Quem está fazendo a requisição. A API identifica por headers (ainda não há autenticação):
 * `admin` usa o operador administrador da organização, `kiosk` o canal de autoatendimento,
 * `{ operatorId }` o atendente de um guichê e `anonymous` não envia identidade.
 */
export type Identity = 'admin' | 'kiosk' | 'anonymous' | { operatorId: string };

export const IDENTITY = new HttpContextToken<Identity>(() => 'admin');

export const as = (identity: Identity) => new HttpContext().set(IDENTITY, identity);

/** Headers de identidade; também usado fora do HttpClient (o fluxo SSE é lido com `fetch`). */
export function identityHeaders(identity: Identity, env: Environment | null, kioskChannel: string): Record<string, string> {
  if (identity === 'anonymous' || !env) return {};
  const headers: Record<string, string> = { 'X-Organization-Id': env.organizationId };
  if (identity === 'admin') headers['X-Operator-Id'] = env.adminOperatorId;
  else if (identity === 'kiosk') headers['X-Client-Channel'] = kioskChannel;
  else headers['X-Operator-Id'] = identity.operatorId;
  return headers;
}

export const identityInterceptor: HttpInterceptorFn = (req, next) => {
  const headers = identityHeaders(
    req.context.get(IDENTITY),
    inject(EnvironmentStore).environment(),
    inject(SettingsStore).kioskChannel(),
  );
  return next(req.clone({ setHeaders: headers }));
};
