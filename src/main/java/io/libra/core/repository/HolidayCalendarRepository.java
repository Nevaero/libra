package io.libra.core.repository;

import io.libra.core.persistence.entity.HolidayCalendarEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HolidayCalendarRepository extends JpaRepository<HolidayCalendarEntity, String> {
}
