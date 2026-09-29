package com.uptrail.entitlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.uptrail.entitlement.domain.DaySession;
import com.uptrail.entitlement.domain.Session;
import com.uptrail.entitlement.domain.TrainingDayCalculator;
import com.uptrail.entitlement.domain.TrainingDayCalculator.Problem;
import com.uptrail.entitlement.domain.TrainingDayCalculator.Request;
import com.uptrail.entitlement.domain.TrainingDayCalculator.Result;

/**
 * Working-day and half-day rules. Holidays here are synthetic fixtures.
 * 12 October 2026 is a Monday.
 */
class TrainingDayCalculatorTest {

    private static final LocalDate MON = LocalDate.of(2026, 10, 12);
    private static final LocalDate FIXTURE_HOLIDAY = LocalDate.of(2026, 10, 14);
    private static final Map<LocalDate, String> HOLIDAYS = Map.of(
            FIXTURE_HOLIDAY, "Fixture Day",
            LocalDate.of(2027, 1, 1), "Fixture New Year");

    private final TrainingDayCalculator calculator = new TrainingDayCalculator();

    private Result calc(LocalDate start, LocalDate end, Session s, Session e, boolean halfDayAllowed) {
        return calculator.calculate(new Request(start, end, s, e, halfDayAllowed), HOLIDAYS);
    }

    @Test
    void singleFullDayCourseCountsTwoUnits() {
        Result result = calc(MON, MON, Session.AM, Session.PM, false);

        assertThat(result.valid()).isTrue();
        assertThat(result.totalUnits()).isEqualTo(2);
        assertThat(result.days()).singleElement().extracting(d -> d.session()).isEqualTo(DaySession.BOTH);
    }

    @ParameterizedTest
    @CsvSource({"AM,AM,AM", "PM,PM,PM"})
    void internalTrainingAllowsSingleHalfDays(Session start, Session end, DaySession expected) {
        Result result = calc(MON, MON, start, end, true);

        assertThat(result.valid()).isTrue();
        assertThat(result.totalUnits()).isEqualTo(1);
        assertThat(result.days().get(0).session()).isEqualTo(expected);
    }

    @Test
    void afternoonToMorningOnTheSameDayIsInvalid() {
        Result result = calc(MON, MON, Session.PM, Session.AM, true);

        assertThat(result.valid()).isFalse();
        assertThat(result.problems()).contains(Problem.SESSION_ORDER);
    }

    @ParameterizedTest
    @CsvSource({"AM,AM", "PM,PM", "PM,AM"})
    void feePayingCategoriesRejectHalfDays(Session start, Session end) {
        Result result = calc(MON, MON.plusDays(1), start, end, false);

        assertThat(result.problems()).contains(Problem.HALF_DAY_NOT_ALLOWED);
    }

    @Test
    void weekendsInsideThePeriodAreExcluded() {
        Result result = calc(MON.minusDays(3), MON.plusDays(1), Session.AM, Session.PM, false);

        assertThat(result.valid()).isTrue();
        assertThat(result.totalUnits()).isEqualTo(6);
        assertThat(result.excluded()).extracting(TrainingDayCalculator.ExcludedDate::reason)
                .containsExactly("Weekend", "Weekend");
    }

    @Test
    void publicHolidaysInsideThePeriodAreExcludedWithTheirName() {
        Result result = calc(MON, MON.plusDays(4), Session.AM, Session.PM, false);

        assertThat(result.totalUnits()).isEqualTo(8);
        assertThat(result.excluded()).singleElement().satisfies(excluded -> {
            assertThat(excluded.date()).isEqualTo(FIXTURE_HOLIDAY);
            assertThat(excluded.reason()).isEqualTo("Public holiday: Fixture Day");
        });
    }

    @Test
    void periodMustStartAndEndOnWorkingDays() {
        Result weekendStart = calc(MON.minusDays(2), MON, Session.AM, Session.PM, false);
        Result holidayEnd = calc(MON, FIXTURE_HOLIDAY, Session.AM, Session.PM, false);

        assertThat(weekendStart.problems()).containsExactly(Problem.START_NOT_WORKING_DAY);
        assertThat(weekendStart.startReason()).isEqualTo("Weekend");
        assertThat(holidayEnd.problems()).containsExactly(Problem.END_NOT_WORKING_DAY);
        assertThat(holidayEnd.endReason()).isEqualTo("Public holiday: Fixture Day");
    }

    @Test
    void endBeforeStartIsRejected() {
        Result result = calc(MON, MON.minusDays(1), Session.AM, Session.PM, false);

        assertThat(result.problems()).containsExactly(Problem.DATE_ORDER);
        assertThat(result.days()).isEmpty();
    }

    @Test
    void internalMultiDayCourseCountsHalfDaysOnlyAtTheEnds() {
        Result result = calc(MON, MON.plusDays(1), Session.PM, Session.AM, true);

        assertThat(result.valid()).isTrue();
        assertThat(result.days()).extracting(d -> d.session()).containsExactly(DaySession.PM, DaySession.AM);
        assertThat(result.totalUnits()).isEqualTo(2);
    }

    @Test
    void crossYearCourseAllocatesUnitsToEachYear() {
        Result result = calc(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 4), Session.AM, Session.PM, false);

        assertThat(result.valid()).isTrue();
        assertThat(result.unitsByYear()).containsExactly(Map.entry(2026, 4), Map.entry(2027, 2));
        assertThat(result.excluded()).extracting(TrainingDayCalculator.ExcludedDate::reason)
                .containsExactly("Public holiday: Fixture New Year", "Weekend", "Weekend");
    }

    @Test
    void periodsLongerThanAYearAreRejected() {
        Result result = calc(MON, MON.plusDays(TrainingDayCalculator.MAX_PERIOD_DAYS), Session.AM, Session.PM, false);

        assertThat(result.problems()).containsExactly(Problem.PERIOD_TOO_LONG);
    }
}
