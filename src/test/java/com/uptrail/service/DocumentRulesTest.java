package com.uptrail.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.uptrail.service.DocumentTypeDetector.Format;

/**
 * Format detection, file-name handling and amount rules for claims.
 */
class DocumentRulesTest {

    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xDB};

    @Test
    void recognisesTheAcceptedFormatsByContent() {
        assertThat(DocumentTypeDetector.detect("a.pdf", PDF)).contains(Format.PDF);
        assertThat(DocumentTypeDetector.detect("a.PNG", PNG)).contains(Format.PNG);
        assertThat(DocumentTypeDetector.detect("a.jpeg", JPEG)).contains(Format.JPEG);
        assertThat(DocumentTypeDetector.detect("a.jpg", JPEG)).contains(Format.JPEG);
    }

    @Test
    void refusesUnknownContentAndMismatchedNames() {
        assertThat(DocumentTypeDetector.detect("a.pdf", "<html>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(DocumentTypeDetector.detect("a.pdf", PNG)).isEmpty();
        assertThat(DocumentTypeDetector.detect("a.png.exe", PNG)).isEmpty();
        assertThat(DocumentTypeDetector.detect("a", PDF)).isEmpty();
        assertThat(DocumentTypeDetector.detect("a.pdf", new byte[] {'%', 'P'})).isEmpty();
        assertThat(DocumentTypeDetector.detect("a.pdf", null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "'../../etc/passwd.pdf', passwd.pdf",
            "'C:\\Users\\me\\receipt.pdf', receipt.pdf",
            "'..', document",
            "'', document",
            "'  spaced name.png  ', spaced name.png"})
    void displayNamesKeepOnlyTheLastPathSegment(String original, String expected) {
        assertThat(DocumentStorage.displayName(original)).isEqualTo(expected);
    }

    @Test
    void displayNamesDropControlCharactersAndStayWithinTheColumnLimit() {
        assertThat(DocumentStorage.displayName("re\u0000ce\nipt.pdf")).isEqualTo("receipt.pdf");
        String longName = "x".repeat(300) + ".pdf";
        assertThat(DocumentStorage.displayName(longName)).hasSize(200).endsWith(".pdf");
    }

    @ParameterizedTest
    @CsvSource({"600.00, true", "0.01, true", "600.001, false", "600.01, false", "0, false", "-5, false"})
    void amountsArePositiveWithTwoDecimalsAndAtMostTheFee(String amount, boolean accepted) {
        assertThat(ClaimPolicy.amountProblem(new BigDecimal(amount), new BigDecimal("600.00")).isEmpty())
                .isEqualTo(accepted);
    }

    @Test
    void aMissingAmountIsReported() {
        assertThat(ClaimPolicy.amountProblem(null, new BigDecimal("600.00"))).isPresent();
    }
}
