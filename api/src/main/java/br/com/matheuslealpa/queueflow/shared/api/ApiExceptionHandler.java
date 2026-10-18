package br.com.matheuslealpa.queueflow.shared.api;

import br.com.matheuslealpa.queueflow.entry.application.IdempotencyConflictException;
import br.com.matheuslealpa.queueflow.entry.domain.DuplicateIdempotencyKeyException;
import br.com.matheuslealpa.queueflow.shared.application.ResourceNotFoundException;
import br.com.matheuslealpa.queueflow.shared.domain.BusinessRuleViolationException;
import br.com.matheuslealpa.queueflow.shared.domain.InvalidStateTransitionException;
import br.com.matheuslealpa.queueflow.shared.domain.NotAuthorizedException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

/**
 * Traduz exceções de domínio e aplicação em respostas RFC 9457 (Problem Details). Erros de
 * validação e de formato de requisição são tratados pela classe base.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(MissingIdentityException.class)
    ProblemDetail missingIdentity(MissingIdentityException e) {
        return problem(HttpStatus.UNAUTHORIZED, "missing-identity", e.getMessage());
    }

    @ExceptionHandler(NotAuthorizedException.class)
    ProblemDetail notAuthorized(NotAuthorizedException e) {
        return problem(HttpStatus.FORBIDDEN, "not-authorized", e.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "not-found", e.getMessage());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    ProblemDetail invalidTransition(InvalidStateTransitionException e) {
        return problem(HttpStatus.CONFLICT, "invalid-state-transition", e.getMessage());
    }

    @ExceptionHandler({IdempotencyConflictException.class, DuplicateIdempotencyKeyException.class})
    ProblemDetail idempotencyConflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, "idempotency-conflict", e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentModification(OptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "concurrent-modification", e.getMessage());
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ProblemDetail businessRule(BusinessRuleViolationException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "business-rule-violation", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String type, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://queueflow.dev/problems/" + type));
        return problem;
    }
}
