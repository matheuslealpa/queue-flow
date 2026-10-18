package br.com.matheuslealpa.queueflow.testing;

import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePoint;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointRepository;
import br.com.matheuslealpa.queueflow.servicepoint.domain.ServicePointId;

public final class InMemoryServicePointRepository extends InMemoryRepository<ServicePoint, ServicePointId> implements ServicePointRepository {
}
