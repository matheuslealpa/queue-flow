package br.com.matheuslealpa.queueflow.testing;

import br.com.matheuslealpa.queueflow.operator.domain.Operator;
import br.com.matheuslealpa.queueflow.operator.domain.OperatorRepository;
import br.com.matheuslealpa.queueflow.shared.domain.OperatorId;

public final class InMemoryOperatorRepository extends InMemoryRepository<Operator, OperatorId> implements OperatorRepository {
}
