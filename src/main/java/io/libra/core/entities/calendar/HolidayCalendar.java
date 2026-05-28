package io.libra.core.entities.calendar;

import java.util.List;
import java.util.Objects;

// Domain record. `id` = identifiant logique (e.g. "CH-BANKING", "US-NYSE", "EUR-TARGET2").
// Persistence handled by HolidayCalendarEntity + HolidayCalendarMapper.
public record HolidayCalendar(String id, String name, List<Holiday> holidays) {

    public HolidayCalendar {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        holidays = holidays == null ? List.of() : List.copyOf(holidays);
    }
}
