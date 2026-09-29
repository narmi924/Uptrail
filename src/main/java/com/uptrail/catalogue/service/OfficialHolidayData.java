package com.uptrail.catalogue.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Singapore public holidays bundled with the application (classpath {@code holidays/sg-public-holidays.csv}),
 * with the source and retrieval date recorded in {@link #SOURCE_NOTE}. Administrators can import a year from
 * this list and must still confirm it; nothing is fetched from the internet at runtime.
 */
@Component
public class OfficialHolidayData {

    public static final String SOURCE_NOTE = "Ministry of Manpower public holidays page "
            + "(mom.gov.sg/employment-practices/public-holidays), retrieved 2026-09-28; bundled list.";

    public record Entry(LocalDate date, String name) {
    }

    private final List<Entry> entries;

    public OfficialHolidayData() {
        this.entries = load();
    }

    public List<Entry> forYear(int year) {
        return entries.stream().filter(e -> e.date().getYear() == year).toList();
    }

    public Set<Integer> years() {
        Set<Integer> years = new TreeSet<>();
        entries.forEach(e -> years.add(e.date().getYear()));
        return years;
    }

    private static List<Entry> load() {
        ClassPathResource resource = new ClassPathResource("holidays/sg-public-holidays.csv");
        List<Entry> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("date,")) {
                    continue;
                }
                int comma = line.indexOf(',');
                result.add(new Entry(LocalDate.parse(line.substring(0, comma)), line.substring(comma + 1).strip()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the bundled public holiday list", e);
        }
        return List.copyOf(result);
    }
}
