package com.uptrail.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.uptrail.application.domain.ApplicationDetails;

/**
 * SHA-256 of the canonical form of a submitted application. With the client request id it detects a
 * repeated submission (same key, same content) versus a reused key with different content.
 */
final class RequestFingerprint {

    private RequestFingerprint() {
    }

    static String of(ApplicationDetails details) {
        String canonical = Stream.of(details.category(), details.catalogueId(), details.courseTitle(),
                details.providerName(), details.startDate(), details.endDate(), details.startSession(),
                details.endSession(), details.courseFee() == null ? null : details.courseFee().toPlainString(),
                details.justification(), details.workDissemination())
                .map(value -> Objects.toString(value, ""))
                .collect(Collectors.joining("\u001f"));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
