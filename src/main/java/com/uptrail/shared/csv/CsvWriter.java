package com.uptrail.shared.csv;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 CSV writer for report exports. Text cells that a spreadsheet would treat as a formula
 * (starting with =, +, -, @, tab or carriage return) are prefixed with an apostrophe; numeric cells are
 * written as plain numbers. This reduces, but cannot fully rule out, formula injection in every spreadsheet
 * program, so exports are still opened as data rather than trusted content.
 */
public final class CsvWriter {

    private static final String BOM = "﻿";

    private final StringBuilder out = new StringBuilder(BOM);
    private final List<String> row = new ArrayList<>();

    public CsvWriter text(String value) {
        row.add(quote(neutralise(value == null ? "" : value)));
        return this;
    }

    public CsvWriter number(BigDecimal value) {
        row.add(value == null ? "" : value.toPlainString());
        return this;
    }

    public CsvWriter number(long value) {
        row.add(Long.toString(value));
        return this;
    }

    /** Training days from half-day units, e.g. 3 units -> 1.5. */
    public CsvWriter days(int units) {
        row.add(units % 2 == 0 ? Integer.toString(units / 2) : (units / 2) + ".5");
        return this;
    }

    public CsvWriter endRow() {
        out.append(String.join(",", row)).append("\r\n");
        row.clear();
        return this;
    }

    public CsvWriter header(String... names) {
        for (String name : names) {
            text(name);
        }
        return endRow();
    }

    public byte[] toBytes() {
        if (!row.isEmpty()) {
            endRow();
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String neutralise(String value) {
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    static String quote(String value) {
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n")
                || value.contains("\r") || value.startsWith(" ") || value.endsWith(" ");
        if (!needsQuotes) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
