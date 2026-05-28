package io.libra.reference.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Data
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "holidays")
public class HolidayEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    // FK to holiday_calendars(id) — kept as plain string column, no @ManyToOne to stay
    // lightweight (calendars are reference data loaded once on startup).
    @Column(name = "holiday_calendar_id", nullable = false, length = 32)
    private String holidayCalendarId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 128)
    private String name;
}
