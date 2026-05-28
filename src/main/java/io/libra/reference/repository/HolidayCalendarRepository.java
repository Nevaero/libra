package io.libra.reference.repository;

import io.libra.reference.persistence.entity.HolidayCalendarEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HolidayCalendarRepository extends JpaRepository<HolidayCalendarEntity, String> {
}
