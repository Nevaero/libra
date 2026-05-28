package io.libra.settlement.internal;

import io.libra.core.entities.calendar.Holiday;
import io.libra.core.entities.calendar.HolidayCalendar;
import io.libra.util.Uuids;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Signature test of the settlement module : the T+N calculator, proven over thousands of random
// dates rather than a few examples.
class BusinessDayCalculatorTest {

    private final BusinessDayCalculator calc = new BusinessDayCalculator();

    @Property(tries = 2000)
    void addBusinessDaysLandsOnABusinessDayAfterFrom(
            @ForAll @LongRange(min = 18_262L, max = 22_280L) long epochDay,   // 2020-01-01 .. 2030-12-31
            @ForAll @IntRange(min = 1, max = 10) int days) {

        LocalDate from = LocalDate.ofEpochDay(epochDay);
        LocalDate result = calc.addBusinessDays(from, days);

        assertThat(calc.isBusinessDay(result)).as("result is a business day").isTrue();
        assertThat(result).as("result is strictly after from").isAfter(from);

        // exactly `days` business days lie in the half-open range (from, result].
        long businessDays = from.datesUntil(result.plusDays(1))
                .filter(d -> d.isAfter(from))
                .filter(calc::isBusinessDay)
                .count();
        assertThat(businessDays).isEqualTo(days);
    }

    @Property(tries = 1000)
    void monotoneInTheNumberOfDays(
            @ForAll @LongRange(min = 18_262L, max = 22_280L) long epochDay,
            @ForAll @IntRange(min = 0, max = 9) int days) {

        LocalDate from = LocalDate.ofEpochDay(epochDay);
        assertThat(calc.addBusinessDays(from, days))
                .isBeforeOrEqualTo(calc.addBusinessDays(from, days + 1));
    }

    @Test
    void skipsWeekends() {
        LocalDate friday = LocalDate.of(2026, 1, 9);   // 2026-01-01 is a Thursday → 01-09 is Friday
        assertThat(friday.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);

        // T+1 from Friday is the next Monday ; T+2 is Tuesday.
        assertThat(calc.addBusinessDays(friday, 1)).isEqualTo(LocalDate.of(2026, 1, 12));
        assertThat(calc.addBusinessDays(friday, 2)).isEqualTo(LocalDate.of(2026, 1, 13));
        assertThat(calc.isBusinessDay(LocalDate.of(2026, 1, 10))).isFalse();   // Saturday
    }

    @Test
    void skipsHolidaysFromTheCalendar() {
        LocalDate friday = LocalDate.of(2026, 1, 9);
        HolidayCalendar calendar = new HolidayCalendar("TEST", "Test",
                List.of(new Holiday(Uuids.newId(), LocalDate.of(2026, 1, 12), "Made-up Monday")));

        // Monday 01-12 is a holiday, so T+1 from Friday skips Sat/Sun + that Monday → Tuesday 01-13.
        assertThat(calc.addBusinessDays(friday, 1, calendar)).isEqualTo(LocalDate.of(2026, 1, 13));
    }
}
