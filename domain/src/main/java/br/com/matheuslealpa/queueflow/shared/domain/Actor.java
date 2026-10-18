package br.com.matheuslealpa.queueflow.shared.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Quem está executando uma operação. Usado para autorização no domínio e para rastreabilidade.
 */
public sealed interface Actor {

    boolean has(Permission permission);

    String describe();

    /** Operador autenticado, com as permissões vigentes no momento da operação. */
    record Staff(OperatorId operatorId, Set<Permission> permissions) implements Actor {

        public Staff {
            Objects.requireNonNull(operatorId, "operatorId");
            permissions = Set.copyOf(permissions);
        }

        @Override
        public boolean has(Permission permission) {
            return permissions.contains(permission);
        }

        @Override
        public String describe() {
            return "operator:" + operatorId;
        }
    }

    /** Totem, aplicativo ou sistema integrado que cria entradas em nome de uma pessoa. */
    record SelfService(String channel) implements Actor {

        public SelfService {
            channel = Guards.notBlank(channel, "channel");
        }

        @Override
        public boolean has(Permission permission) {
            return false;
        }

        @Override
        public String describe() {
            return "self-service:" + channel;
        }
    }

    /** Processo automático da própria plataforma (p.ex. expiração de chamadas). */
    record SystemProcess(String component) implements Actor {

        public SystemProcess {
            component = Guards.notBlank(component, "component");
        }

        @Override
        public boolean has(Permission permission) {
            return false;
        }

        @Override
        public String describe() {
            return "system:" + component;
        }
    }
}
