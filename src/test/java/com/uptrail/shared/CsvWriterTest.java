package com.uptrail.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.uptrail.shared.csv.CsvWriter;

/**
 * CSV escaping and formula-injection guard.
 */
class CsvWriterTest {

    private static String write(CsvWriter writer) {
        return new String(writer.toBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void startsWithABomAndUsesCrLfRows() {
        String csv = write(new CsvWriter().header("Name", "Fee").text("Siti").number(new BigDecimal("600.00")).endRow());

        assertThat(csv).startsWith("﻿");
        assertThat(csv.substring(1)).isEqualTo("Name,Fee\r\nSiti,600.00\r\n");
    }

    @Test
    void quotesCommasQuotesAndLineBreaks() {
        String csv = write(new CsvWriter().text("Lee, Marcus").text("He said \"yes\"").text("two\nlines").endRow());

        assertThat(csv.substring(1)).isEqualTo("\"Lee, Marcus\",\"He said \"\"yes\"\"\",\"two\nlines\"\r\n");
    }

    @Test
    void keepsNonAsciiText() {
        String csv = write(new CsvWriter().text("数据隐私 Données").endRow());

        assertThat(csv).contains("数据隐私 Données");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=HYPERLINK(\"http://x\")", "+1+1", "-2+3", "@SUM(A1)", "\tcmd"})
    void neutralisesTextThatLooksLikeAFormula(String value) {
        String csv = write(new CsvWriter().text(value).endRow()).substring(1);

        assertThat(csv).doesNotStartWith("=").doesNotStartWith("+").doesNotStartWith("-").doesNotStartWith("@");
        assertThat(csv).contains("'");
    }

    @Test
    void numbersAreNotAltered() {
        String csv = write(new CsvWriter().number(new BigDecimal("-15.50")).number(3).days(3).endRow());

        assertThat(csv.substring(1)).isEqualTo("-15.50,3,1.5\r\n");
    }
}
