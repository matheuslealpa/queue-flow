package br.com.matheuslealpa.queueflow.shared.api;

import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

class RequestContextResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return RequestContext.class.equals(parameter.getParameterType());
    }

    @Override
    public RequestContext resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest request, WebDataBinderFactory binderFactory) {
        var organization = uuid(request, RequestContext.ORGANIZATION_HEADER);
        if (organization == null) {
            throw new MissingIdentityException("Cabeçalho " + RequestContext.ORGANIZATION_HEADER + " é obrigatório");
        }
        var operator = uuid(request, RequestContext.OPERATOR_HEADER);
        var channel = request.getHeader(RequestContext.CHANNEL_HEADER);
        return new RequestContext(new OrganizationId(organization), operator == null ? null : new OperatorId(operator),
                channel == null || channel.isBlank() ? null : channel.strip());
    }

    private static UUID uuid(NativeWebRequest request, String header) {
        var value = request.getHeader(header);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException e) {
            throw new MissingIdentityException("Cabeçalho %s inválido".formatted(header));
        }
    }
}
