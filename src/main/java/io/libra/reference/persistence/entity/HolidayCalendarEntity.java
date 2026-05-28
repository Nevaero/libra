package io.libra.reference.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "holiday_calendars")
public class HolidayCalendarEntity {

    @Id
    @Column(nullable = false, length = 32)
    private String id;

    @Column(nullable = false, length = 128)
    private String name;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "holiday_calendar_id")
    private List<HolidayEntity> holidays = new ArrayList<>();
}
