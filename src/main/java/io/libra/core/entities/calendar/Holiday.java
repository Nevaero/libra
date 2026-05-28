package io.libra.core.entities.calendar;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

// Domain record. Persistence handled by HolidayEntity + HolidayMapper.
public record Holiday(UUID id, LocalDate date, String name) {

    public Holiday {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
