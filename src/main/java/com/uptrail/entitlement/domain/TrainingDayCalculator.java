package com.uptrail.entitlement.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Counts the training units of a course period. Weekends and public holidays inside the period are not
 * counted; the first and last day must be working days. Units are half days: a full working day is 2 units.
 * Only categories that allow half days may start in the afternoon or end in the morning.
 */
public final class TrainingDayCalculator {

    /** Longest course period accepted, to keep schedules and ledgers reasonable. */
    public static final int MAX_PERIOD_DAYS = 366;

    public enum Problem {
        DATE_ORDER,
        PERIOD_TOO_LONG,
        START_NOT_WORKING_DAY,
        END_NOT_WORKING_DAY,
        HALF_DAY_NOT_ALLOWED,
        SESSION_ORDER
    }

    public record Request(LocalDate start, LocalDate end, Session startSession, Session endSession,
            boolean halfDayAllowed) {

        public Request {
            Objects.requireNonNull(start);
            Objects.requireNonNull(end);
            Objects.requireNonNull(startSession);
            Objects.requireNonNull(endSession);
        }
    }

    public record DayAllocation(LocalDate date, DaySession session) {

        public int units() {
            return session.units();
        }
    }

    public record ExcludedDate(LocalDate date, String reason) {
    }

    public record Result(List<DayAllocation> days, List<ExcludedDate> excluded, List<Problem> problems,
            String startReason, String endReason) {

        public boolean valid() {
            return problems.isEmpty() && !days.isEmpty();
        }

        public int totalUnits() {
            return days.stream().mapToInt(DayAllocation::units).sum();
        }

        /** Units per calendar year of the actual training dates, in ascending year order. */
        public SortedMap<Integer, Integer> unitsByYear() {
            SortedMap<Integer, Integer> result = new TreeMap<>();
            for (DayAllocation day : days) {
                result.merge(day.date().getYear(), day.units(), Integer::sum);
            }
            return result;
        }
    }

    public Result calculate(Request request, Map<LocalDate, String> holidays) {
        List<Problem> problems = new ArrayList<>();
        LocalDate start = request.start();
        LocalDate end = request.end();
        if (end.isBefore(start)) {
            problems.add(Problem.DATE_ORDER);
            return new Result(List.of(), List.of(), problems, null, null);
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_PERIOD_DAYS) {
            problems.add(Problem.PERIOD_TOO_LONG);
            return new Result(List.of(), List.of(), problems, null, null);
        }
        if (!request.halfDayAllowed()
                && (request.startSession() != Session.AM || request.endSession() != Session.PM)) {
            problems.add(Problem.HALF_DAY_NOT_ALLOWED);
        }
        boolean sameDay = start.equals(end);
        if (sameDay && request.startSession() == Session.PM && request.endSession() == Session.AM) {
            problems.add(Problem.SESSION_ORDER);
        }
        String startReason = nonWorkingReason(start, holidays);
        String endReason = nonWorkingReason(end, holidays);
        if (startReason != null) {
            problems.add(Problem.START_NOT_WORKING_DAY);
        }
        if (endReason != null) {
            problems.add(Problem.END_NOT_WORKING_DAY);
        }

        List<DayAllocation> days = new ArrayList<>();
        List<ExcludedDate> excluded = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String reason = nonWorkingReason(date, holidays);
            if (reason != null) {
                excluded.add(new ExcludedDate(date, reason));
                continue;
            }
            DaySession session = sessionFor(date, start, end, request.startSession(), request.endSession());
            if (session != null) {
                days.add(new DayAllocation(date, session));
            }
        }
        return new Result(Collections.unmodifiableList(days), Collections.unmodifiableList(excluded),
                Collections.unmodifiableList(problems), startReason, endReason);
    }

    private static DaySession sessionFor(LocalDate date, LocalDate start, LocalDate end, Session startSession,
            Session endSession) {
        if (start.equals(end)) {
            if (startSession == endSession) {
                return startSession == Session.AM ? DaySession.AM : DaySession.PM;
            }
            return startSession == Session.AM ? DaySession.BOTH : null;
        }
        if (date.equals(start)) {
            return startSession == Session.AM ? DaySession.BOTH : DaySession.PM;
        }
        if (date.equals(end)) {
            return endSession == Session.PM ? DaySession.BOTH : DaySession.AM;
        }
        return DaySession.BOTH;
    }

    /** Returns why a date is not a working day, or {@code null} if it is one. */
    public static String nonWorkingReason(LocalDate date, Map<LocalDate, String> holidays) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return "Weekend";
        }
        String holiday = holidays.get(date);
        return holiday == null ? null : "Public holiday: " + holiday;
    }
}
