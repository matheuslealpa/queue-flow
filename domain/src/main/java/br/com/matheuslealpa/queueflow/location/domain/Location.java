package br.com.matheuslealpa.queueflow.location.domain;

import br.com.matheuslealpa.queueflow.shared.domain.AggregateRoot;
import br.com.matheuslealpa.queueflow.shared.domain.Guards;
import br.com.matheuslealpa.queueflow.shared.domain.OrganizationId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Unidade operacional (filial, campus, estabelecimento). O fuso horário define o
 * dia operacional usado na numeração das senhas.
 */
public final class Location extends AggregateRoot<LocationId> {

    private String name;
    private ZoneId timeZone;
    private boolean active;

    private Location(LocationId id, OrganizationId organizationId, String name, ZoneId timeZone,
                     boolean active, long version) {
        super(id, organizationId, version);
        this.name = Guards.maxLength(Guards.notBlank(name, "name"), 150, "name");
        this.timeZone = Guards.required(timeZone, "timeZone");
        this.active = active;
    }

    public static Location create(OrganizationId organizationId, String name, ZoneId timeZone) {
        return new Location(LocationId.newId(), organizationId, name, timeZone, true, 0);
    }

    public static Location restore(LocationId id, OrganizationId organizationId, String name, ZoneId timeZone,
                                   boolean active, long version) {
        return new Location(id, organizationId, name, timeZone, active, version);
    }

    public LocalDate operationalDate(Instant instant) {
        return LocalDate.ofInstant(instant, timeZone);
    }

    public void rename(String newName) {
        this.name = Guards.maxLength(Guards.notBlank(newName, "name"), 150, "name");
    }

    public void changeTimeZone(ZoneId newTimeZone) {
        this.timeZone = Guards.required(newTimeZone, "timeZone");
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public String name() {
        return name;
    }

    public ZoneId timeZone() {
        return timeZone;
    }

    public boolean isActive() {
        return active;
    }
}
