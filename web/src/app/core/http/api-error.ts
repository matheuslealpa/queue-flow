import { HttpErrorResponse, type HttpInterceptorFn } from '@angular/common/http';
import { toast } from '@spartan-ng/brain/sonner';
import { catchError, throwError } from 'rxjs';
import type { ProblemDetail } from '../api/models';

const STATUS_PREFIX: Record<number, string> = {
  400: 'Dados inválidos',
  401: 'Sem identificação',
  403: 'Sem permissão',
  404: 'Não encontrado',
  409: 'Conflito',
  422: 'Regra de negócio',
};

/** Erro da API já traduzido a partir do Problem Details (RFC 9457). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem: ProblemDetail | null,
  ) {
    super(ApiError.describe(status, problem));
  }

  private static describe(status: number, problem: ProblemDetail | null): string {
    if (status === 0) return 'A API não respondeu. Confira se ela está rodando em localhost:8080.';
    const text = problem?.detail || problem?.title || `HTTP ${status}`;
    const prefix = STATUS_PREFIX[status];
    return prefix ? `${prefix}: ${text}` : text;
  }
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

/**
 * Converte falhas em {@link ApiError}. Falhas de comandos (POST/PUT) viram toast; as de leitura
 * ficam para a tela mostrar, porque o polling repetiria o aviso a cada poucos segundos.
 */
export const apiErrorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) return throwError(() => error);
      const problem = typeof error.error === 'object' ? (error.error as ProblemDetail | null) : null;
      const apiError = new ApiError(error.status, problem);
      if (req.method !== 'GET') toast.error(apiError.message);
      return throwError(() => apiError);
    }),
  );
