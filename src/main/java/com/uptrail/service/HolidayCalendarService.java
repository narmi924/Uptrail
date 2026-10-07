package com.uptrail.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.model.ExcludedDays;
import com.uptrail.model.TrainingCalendarYear;
import com.uptrail.repo.ExcludedDaysRepo;
import com.uptrail.repo.TrainingCalendarYearRepo;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;

/**
 * Read side of the public holiday calendar used by working-day calculations.
 */
@Service
@Transactional(readOnly = true)
public class HolidayCalendarService {

    private final ExcludedDaysRepo holidays;
    private final TrainingCalendarYearRepo calendarYears;

    public HolidayCalendarService(ExcludedDaysRepo holidays, TrainingCalendarYearRepo calendarYears) {
        this.holidays = holidays;
        this.calendarYears = calendarYears;
    }

    public Map<LocalDate, String> holidaysBetween(LocalDate from, LocalDate to) {
        Map<LocalDate, String> result = new LinkedHashMap<>();
        for (ExcludedDays holiday : holidays.findByHolidayDateBetweenOrderByHolidayDate(from, to)) {
            result.put(holiday.getHolidayDate(), holiday.getName());
        }
        return result;
    }

    public Optional<TrainingCalendarYear> year(int year) {
        return calendarYears.findById(year);
    }

    public boolean isConfirmed(int year) {
        return calendarYears.findById(year).map(TrainingCalendarYear::isConfirmed).orElse(false);
    }

    /**
     * Working days can only be counted for years whose holiday list an administrator has confirmed.
     */
    public void requireConfirmed(LocalDate from, LocalDate to) {
        List<Integer> unconfirmed = IntStream.rangeClosed(from.getYear(), to.getYear())
                .filter(year -> !isConfirmed(year)).boxed().toList();
        if (!unconfirmed.isEmpty()) {
            String years = unconfirmed.stream().map(String::valueOf).collect(Collectors.joining(" and "));
            throw new BusinessException(ErrorCode.HOLIDAY_CALENDAR_UNCONFIRMED, "The public holiday calendar for "
                    + years + " has not been confirmed by an administrator yet, so training days cannot be counted.");
        }
    }
}
