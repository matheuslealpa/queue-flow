package br.com.matheuslealpa.queueflow.operator.domain;

import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.util.Optional;

public interface OperatorRepository {

    Optional<Operator> findById(OrganizationId organizationId, OperatorId id);

    void save(Operator operator);
}
