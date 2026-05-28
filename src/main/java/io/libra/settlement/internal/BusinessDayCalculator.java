package io.libra.settlement.internal;

import io.libra.core.entities.calendar.HolidayCalendar;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

// Pure T+N business-day arithmetic. A day is a business day if it is not a weekend and not a
// holiday in any of the supplied calendars (a cross-currency trade joins e.g. US ∪ JP). Phase 1
// callers pass no calendars → weekends-only ; real per-currency calendars wire in later.
//
// Property-tested (jqwik) : the result is always a business day, never before `from`, monotone.
@Component
public class BusinessDayCalculator {

    public boolean isBusinessDay(LocalDate date, HolidayCalendar... calendars) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        for (HolidayCalendar calendar : calendars) {
            boolean holiday = calendar.holidays().stream().anyMatch(h -> h.date().equals(date));
            if (holiday) {
                return false;
            }
        }
        return true;
    }

    // Adds `days` business days to `from` (T+N). `from` itself is not counted ; the result is the
    // n-th business day strictly after it.
    public LocalDate addBusinessDays(LocalDate from, int days, HolidayCalendar... calendars) {
        if (days < 0) {
            throw new IllegalArgumentException("days must be >= 0, got: " + days);
        }
        LocalDate result = from;
        int added = 0;
        while (added < days) {
            result = result.plusDays(1);
            if (isBusinessDay(result, calendars)) {
                added++;
            }
        }
        return result;
    }
}
