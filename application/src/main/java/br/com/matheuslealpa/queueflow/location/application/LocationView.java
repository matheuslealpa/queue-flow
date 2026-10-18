package br.com.matheuslealpa.queueflow.location.application;

import br.com.matheuslealpa.queueflow.location.domain.Location;

import java.util.UUID;

public record LocationView(UUID id, String name, String timeZone, boolean active) {

    public static LocationView of(Location location) {
        return new LocationView(location.id().value(), location.name(), location.timeZone().getId(),
                location.isActive());
    }
}
