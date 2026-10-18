package br.com.matheuslealpa.queueflow.testing;

import br.com.matheuslealpa.queueflow.service.domain.ServiceOffering;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingRepository;
import br.com.matheuslealpa.queueflow.service.domain.ServiceOfferingId;

public final class InMemoryServiceOfferingRepository extends InMemoryRepository<ServiceOffering, ServiceOfferingId> implements ServiceOfferingRepository {
}
