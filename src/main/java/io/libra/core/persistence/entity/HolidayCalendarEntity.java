package io.libra.core.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

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
