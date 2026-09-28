package com.uptrail.shared.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.uptrail.shared.time.BusinessClock;

/**
 * Display formats used by all templates ({@code ${@formats.money(x)}}), so amounts, dates and training days
 * look the same on every page, in CSV exports and in emails.
 */
@Component("formats")
public class Formats {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm",
            Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    public String money(BigDecimal amount) {
        return amount == null ? "" : "SGD " + amount(amount);
    }

    /** Amount without currency, for columns whose header states the currency. */
    public String amount(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    /** Training time from half-day units: 3 units -> "1.5 days". */
    public String days(int units) {
        String value = units % 2 == 0 ? String.valueOf(units / 2) : (units / 2) + ".5";
        if (units < 0 && units % 2 != 0) {
            value = "-" + (Math.abs(units) / 2) + ".5";
        }
        return value + (Math.abs(units) == 2 ? " day" : " days");
    }

    /** Training days as a bare number for columns whose header states the unit: 3 units -> "1.5". */
    public String dayValue(int units) {
        String value = (Math.abs(units) / 2) + (units % 2 != 0 ? ".5" : "");
        return units < 0 ? "-" + value : value;
    }

    /** Signed training time for ledger movements. */
    public String signedDays(int units) {
        return (units > 0 ? "+" : "") + days(units);
    }

    public String signedAmount(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return (amount.signum() > 0 ? "+" : "") + amount(amount);
    }

    public String date(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    public String dateTime(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant.atZone(BusinessClock.ZONE));
    }

    public String month(LocalDate date) {
        return date == null ? "" : MONTH.format(date);
    }

    public String period(LocalDate start, String startSession, LocalDate end, String endSession) {
        if (start == null || end == null) {
            return "";
        }
        String first = DATE.format(start) + sessionSuffix(startSession, "AM");
        if (start.equals(end)) {
            if ("AM".equals(startSession) && "PM".equals(endSession)) {
                return DATE.format(start);
            }
            return DATE.format(start) + " (" + ("AM".equals(startSession) ? "morning" : "afternoon") + ")";
        }
        return first + " – " + DATE.format(end) + sessionSuffix(endSession, "PM");
    }

    private static String sessionSuffix(String session, String fullDaySession) {
        if (session == null || session.equals(fullDaySession)) {
            return "";
        }
        return "AM".equals(session) ? " (morning)" : " (afternoon)";
    }
}
