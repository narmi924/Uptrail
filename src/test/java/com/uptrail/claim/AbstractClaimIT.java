package com.uptrail.claim;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import com.uptrail.application.AbstractApplicationIT;
import com.uptrail.service.CourseApplicationService.Decision;
import com.uptrail.service.ClaimCommandService;
import com.uptrail.service.DocumentStorage;
import com.uptrail.service.DocumentStorage.Upload;
import com.uptrail.model.Session;
import com.uptrail.support.Fixtures.Person;

/**
 * Claim tests start from a completed external course of SGD 600 (Mon 12 Oct 2026) and a completed internal
 * half day (Tue 13 Oct 2026) of the fixture employee. Business date: Friday 16 October 2026.
 */
abstract class AbstractClaimIT extends AbstractApplicationIT {

    static final LocalDate FRI_16_OCT = LocalDate.of(2026, 10, 16);
    static final byte[] PDF = "%PDF-1.4\n% Uptrail test document\n".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};

    @Autowired
    protected ClaimCommandService claims;

    @Autowired
    protected DocumentStorage storage;

    protected Long externalId;
    protected Long internalId;

    @BeforeEach
    void completedCourses() {
        externalId = completed(employee, external(MON_12_OCT, MON_12_OCT, "600.00"));
        internalId = completed(employee, internal(MON_12_OCT.plusDays(1), Session.PM, MON_12_OCT.plusDays(1),
                Session.PM));
        clock.setDate(FRI_16_OCT);
        commands.complete(employee.actor(), externalId, version(externalId), "Useful course.");
        commands.complete(employee.actor(), internalId, version(internalId), "Useful session.");
    }

    private Long completed(Person person, com.uptrail.model.ApplicationDetails details) {
        Long id = submit(person, details).applicationId();
        commands.decide(manager.actor(), id, Decision.APPROVE, "OK", version(id));
        return id;
    }

    static Upload pdf(String name) {
        return new Upload(name, PDF);
    }

    Long submitClaim(Long applicationId, String amount) {
        return claims.submit(employee.actor(), applicationId, new BigDecimal(amount), true, pdf("receipt.pdf"),
                pdf("certificate.pdf"));
    }

    long claimVersion(Long claimId) {
        return jdbc.queryForObject("SELECT version FROM course_fee_application WHERE id = ?", Long.class, claimId);
    }

    String claimStatus(Long claimId) {
        return jdbc.queryForObject("SELECT status FROM course_fee_application WHERE id = ?", String.class, claimId);
    }

    Long approvedClaim() {
        Long claimId = submitClaim(externalId, "600.00");
        claims.decide(manager.actor(), claimId, ClaimCommandService.Decision.APPROVE, "Documents match.",
                claimVersion(claimId));
        return claimId;
    }
}
