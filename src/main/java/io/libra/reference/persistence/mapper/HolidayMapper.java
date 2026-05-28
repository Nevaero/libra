package io.libra.reference.persistence.mapper;

import io.libra.core.entities.calendar.Holiday;
import io.libra.reference.persistence.entity.HolidayEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface HolidayMapper {

    Holiday toDomain(HolidayEntity entity);

    // `holidayCalendarId` is set by the parent HolidayCalendarMapper after mapping,
    // so we ignore it here to avoid MapStruct complaints about the unmatched property.
    @Mapping(target = "holidayCalendarId", ignore = true)
    HolidayEntity toEntity(Holiday domain);
}
