package io.libra.reference.persistence.mapper;

import io.libra.core.entities.calendar.HolidayCalendar;
import io.libra.reference.persistence.entity.HolidayCalendarEntity;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = HolidayMapper.class)
public abstract class HolidayCalendarMapper {

    public abstract HolidayCalendar toDomain(HolidayCalendarEntity entity);

    public abstract HolidayCalendarEntity toEntity(HolidayCalendar domain);

    // After MapStruct fills `holidays`, propagate the calendar id into each child so the
    // FK column is populated when the aggregate is persisted.
    @AfterMapping
    protected void linkCalendarId(HolidayCalendar domain, @MappingTarget HolidayCalendarEntity entity) {
        if (entity.getHolidays() != null) {
            entity.getHolidays().forEach(h -> h.setHolidayCalendarId(domain.id()));
        }
    }
}
