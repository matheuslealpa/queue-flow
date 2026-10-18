package br.com.matheuslealpa.queueflow.testing;

import br.com.matheuslealpa.queueflow.location.domain.Location;
import br.com.matheuslealpa.queueflow.location.domain.LocationRepository;
import br.com.matheuslealpa.queueflow.location.domain.LocationId;

public final class InMemoryLocationRepository extends InMemoryRepository<Location, LocationId> implements LocationRepository {
}
