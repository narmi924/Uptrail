package com.uptrail.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import com.uptrail.service.CourseApplicationService;
import com.uptrail.service.ClaimCommandService.Decision;
import com.uptrail.service.DocumentStorage.Upload;
import com.uptrail.service.EntitlementService.Balance;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.support.Fixtures.Person;

/**
 * Fee claim rules, revisions, decisions and reimbursement registration with their ledger effects, and the
 * private document store.
 */
class ClaimLifecycleIT extends AbstractClaimIT {

    private static void expectCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    private static void expectField(ThrowingCallable call, ErrorCode code, String field) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.code()).isEqualTo(code);
            assertThat(e.fieldErrors()).containsKey(field);
        });
    }

    private Balance balance2026() {
        return entitlements.balance(employee.id(), 2026);
    }

    // ---- eligibility -------------------------------------------------------------------------

    @Test
    void submittingAClaimRecordsDocumentsAuditAndNotifiesTheApproverWithoutTouchingTheLedger() {
        long ledgerRows = count("SELECT COUNT(*) FROM training_ledger");

        Long claimId = submitClaim(externalId, "550.00");

        assertThat(claimStatus(claimId)).isEqualTo("SUBMITTED");
        assertThat(count("SELECT COUNT(*) FROM claim_document WHERE claim_id = ? AND claim_revision = 1", claimId))
                .isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE aggregate_type = 'CLAIM' AND event_type = 'SUBMITTED'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM email_outbox WHERE template_code = 'CLAIM_SUBMITTED' "
                + "AND recipient_employee_id = ?", manager.id())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM training_ledger")).isEqualTo(ledgerRows);
        assertThat(jdbc.queryForObject("SELECT approver_id FROM course_fee_application WHERE id = ?", Long.class, claimId))
                .isEqualTo(manager.id());
    }

    @Test
    void onlyCompletedFeePayingCoursesCanBeClaimed() {
        Long approvedOnly = submit(employee, external(FRI_16_OCT.plusDays(3), FRI_16_OCT.plusDays(3), "300.00"))
                .applicationId();
        commands.decide(manager.actor(), approvedOnly, CourseApplicationService.Decision.APPROVE, "OK",
                version(approvedOnly));

        expectCode(() -> submitClaim(approvedOnly, "300.00"), ErrorCode.CLAIM_NOT_ELIGIBLE);
        expectCode(() -> submitClaim(internalId, "10.00"), ErrorCode.CLAIM_NOT_ELIGIBLE);
        assertThat(count("SELECT COUNT(*) FROM course_fee_application")).isZero();
    }

    @Test
    void theFeeMustHaveBeenPaidByTheEmployee() {
        expectField(() -> claims.submit(employee.actor(), externalId, new BigDecimal("600.00"), false,
                pdf("receipt.pdf"), pdf("certificate.pdf")), ErrorCode.CLAIM_NOT_ELIGIBLE, "paidByEmployee");
    }

    @Test
    void bothDocumentsAreRequired() {
        expectField(() -> claims.submit(employee.actor(), externalId, new BigDecimal("600.00"), true, null,
                pdf("certificate.pdf")), ErrorCode.INVALID_DOCUMENT, "receipt");
        assertThatThrownBy(() -> claims.submit(employee.actor(), externalId, new BigDecimal("600.00"), true, null,
                null)).isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.fieldErrors()).containsKeys("receipt", "certificate"));
        assertThat(count("SELECT COUNT(*) FROM course_fee_application")).isZero();
    }

    @Test
    void theAmountMustBePositiveAndAtMostTheApprovedFee() {
        expectField(() -> submitClaim(externalId, "600.01"), ErrorCode.VALIDATION_FAILED, "amount");
        expectField(() -> submitClaim(externalId, "0"), ErrorCode.VALIDATION_FAILED, "amount");
        expectField(() -> submitClaim(externalId, "10.005"), ErrorCode.VALIDATION_FAILED, "amount");
        expectField(() -> claims.submit(employee.actor(), externalId, null, true, pdf("r.pdf"), pdf("c.pdf")),
                ErrorCode.VALIDATION_FAILED, "amount");
    }

    @Test
    void anApplicationHasAtMostOneClaim() {
        submitClaim(externalId, "600.00");

        expectCode(() -> submitClaim(externalId, "600.00"), ErrorCode.CLAIM_NOT_ELIGIBLE);
        assertThat(count("SELECT COUNT(*) FROM course_fee_application")).isEqualTo(1);
    }

    @Test
    void anotherEmployeesApplicationIsNotFound() {
        Person other = fixtures.employee("other");
        fixtures.route(other, manager);

        assertThatThrownBy(() -> claims.submit(other.actor(), externalId, new BigDecimal("600.00"), true,
                pdf("r.pdf"), pdf("c.pdf"))).isInstanceOf(NotFoundException.class);
    }

    // ---- revisions and decisions ------------------------------------------------------------

    @Test
    void aRejectedClaimIsRevisedAsTheSameClaimWithTheNextRevision() {
        Long claimId = submitClaim(externalId, "600.00");
        claims.decide(manager.actor(), claimId, Decision.REJECT, "Receipt is unreadable.", claimVersion(claimId));
        expectCode(() -> claims.resubmit(employee.actor(), claimId, claimVersion(claimId) - 1,
                new BigDecimal("580.00"), true, pdf("receipt-2.pdf"), pdf("certificate-2.pdf")), ErrorCode.STALE_VERSION);

        claims.resubmit(employee.actor(), claimId, claimVersion(claimId), new BigDecimal("580.00"), true,
                pdf("receipt-2.pdf"), pdf("certificate-2.pdf"));

        assertThat(claimStatus(claimId)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT revision FROM course_fee_application WHERE id = ?", Integer.class, claimId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT amount FROM course_fee_application WHERE id = ?", BigDecimal.class, claimId))
                .isEqualByComparingTo("580.00");
        assertThat(count("SELECT COUNT(*) FROM claim_document WHERE claim_id = ?", claimId)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT decision_reason FROM course_fee_application WHERE id = ?", String.class, claimId))
                .isNull();
        assertThat(count("SELECT COUNT(*) FROM course_fee_application")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM email_outbox WHERE template_code = 'CLAIM_SUBMITTED'")).isEqualTo(2);
        expectCode(() -> claims.resubmit(employee.actor(), claimId, claimVersion(claimId), new BigDecimal("580.00"),
                true, pdf("r.pdf"), pdf("c.pdf")), ErrorCode.INVALID_STATE);
    }

    @Test
    void decisionsNeedAReasonTheAssignedManagerAndTheCurrentVersion() {
        Long claimId = submitClaim(externalId, "600.00");
        Person otherManager = fixtures.manager("mgr2");

        expectCode(() -> claims.decide(manager.actor(), claimId, Decision.APPROVE, " ", claimVersion(claimId)),
                ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> claims.decide(otherManager.actor(), claimId, Decision.APPROVE, "OK",
                claimVersion(claimId))).isInstanceOf(NotFoundException.class);
        expectCode(() -> claims.decide(manager.actor(), claimId, Decision.APPROVE, "OK", claimVersion(claimId) + 1),
                ErrorCode.STALE_VERSION);

        claims.decide(manager.actor(), claimId, Decision.APPROVE, "Documents match.", claimVersion(claimId));

        assertThat(claimStatus(claimId)).isEqualTo("APPROVED");
        assertThat(count("SELECT COUNT(*) FROM email_outbox WHERE template_code = 'CLAIM_APPROVED' "
                + "AND recipient_employee_id = ?", employee.id())).isEqualTo(1);
        expectCode(() -> claims.decide(manager.actor(), claimId, Decision.REJECT, "Late", claimVersion(claimId)),
                ErrorCode.ALREADY_PROCESSED);
    }

    @Test
    void aManagerCannotDecideTheirOwnClaim() {
        Person director = fixtures.manager("director");
        fixtures.route(manager, director);
        fixtures.account(manager, 2026, 20, "2000.00");
        clock.setDate(MON_12_OCT.minusDays(7));
        Long own = submit(manager, external(MON_12_OCT, MON_12_OCT, "400.00")).applicationId();
        commands.decide(director.actor(), own, CourseApplicationService.Decision.APPROVE, "OK", version(own));
        clock.setDate(FRI_16_OCT);
        commands.complete(manager.actor(), own, version(own), "Good.");
        Long claimId = claims.submit(manager.actor(), own, new BigDecimal("400.00"), true, pdf("r.pdf"),
                pdf("c.pdf"));

        expectCode(() -> claims.decide(manager.actor(), claimId, Decision.APPROVE, "Mine", claimVersion(claimId)),
                ErrorCode.SELF_APPROVAL);
        assertThat(jdbc.queryForObject("SELECT approver_id FROM course_fee_application WHERE id = ?", Long.class, claimId))
                .isEqualTo(director.id());
    }

    // ---- reimbursement registration -----------------------------------------------------

    @Test
    void registeringAReimbursementAddsToTheReimbursedTotalWithoutChargingTheBudgetAgain() {
        Long claimId = approvedClaim();
        Balance before = balance2026();

        String reference = claims.registerReimbursement(admin.actor(), claimId, claimVersion(claimId));

        assertThat(reference).isEqualTo(String.format("SIM-20261016-%06d", claimId));
        Balance after = balance2026();
        assertThat(after.reimbursedAmount()).isEqualByComparingTo("600.00");
        assertThat(after.committedAmount()).isEqualByComparingTo(before.committedAmount());
        assertThat(after.availableBudget()).isEqualByComparingTo(before.availableBudget());
        assertThat(count("SELECT COUNT(*) FROM training_ledger WHERE entry_type = 'REIMBURSE' AND claim_id = ? "
                + "AND reimbursed_amount_delta = 600.00 AND committed_amount_delta = 0", claimId)).isEqualTo(1);
        assertThat(claimStatus(claimId)).isEqualTo("REIMBURSED");
        assertThat(count("SELECT COUNT(*) FROM email_outbox WHERE template_code = 'CLAIM_REIMBURSED'")).isEqualTo(1);
    }

    @Test
    void aSecondRegistrationIsRefusedAndPostsNothing() {
        Long claimId = approvedClaim();
        long version = claimVersion(claimId);
        claims.registerReimbursement(admin.actor(), claimId, version);
        long ledgerRows = count("SELECT COUNT(*) FROM training_ledger");

        expectCode(() -> claims.registerReimbursement(admin.actor(), claimId, version), ErrorCode.ALREADY_PROCESSED);
        expectCode(() -> claims.registerReimbursement(admin.actor(), claimId, claimVersion(claimId)),
                ErrorCode.ALREADY_PROCESSED);
        assertThat(count("SELECT COUNT(*) FROM training_ledger")).isEqualTo(ledgerRows);
    }

    @Test
    void onlyApprovedClaimsCanBeRegisteredAndNeverByTheClaimant() {
        Long claimId = submitClaim(externalId, "600.00");
        assertThatThrownBy(() -> claims.registerReimbursement(admin.actor(), claimId, claimVersion(claimId)))
                .isInstanceOf(NotFoundException.class);

        Person hybrid = fixtures.person("hybrid", com.uptrail.model.Designation.PROFESSIONAL,
                com.uptrail.model.Role.ADMIN, com.uptrail.model.Role.STAFF);
        fixtures.route(hybrid, manager);
        fixtures.account(hybrid, 2026, 20, "2000.00");
        clock.setDate(MON_12_OCT.minusDays(7));
        Long own = submit(hybrid, external(MON_12_OCT, MON_12_OCT, "200.00")).applicationId();
        commands.decide(manager.actor(), own, CourseApplicationService.Decision.APPROVE, "OK", version(own));
        clock.setDate(FRI_16_OCT);
        commands.complete(hybrid.actor(), own, version(own), "Good.");
        Long ownClaim = claims.submit(hybrid.actor(), own, new BigDecimal("200.00"), true, pdf("r.pdf"), pdf("c.pdf"));
        claims.decide(manager.actor(), ownClaim, Decision.APPROVE, "OK", claimVersion(ownClaim));

        expectCode(() -> claims.registerReimbursement(hybrid.actor(), ownClaim, claimVersion(ownClaim)),
                ErrorCode.SELF_APPROVAL);
    }

    @Test
    void concurrentRegistrationsOfTheSameClaimPostOnce() throws Exception {
        Long claimId = approvedClaim();
        long version = claimVersion(claimId);

        List<Object> results = race(() -> claims.registerReimbursement(admin.actor(), claimId, version),
                () -> claims.registerReimbursement(admin.actor(), claimId, version));

        assertThat(results.stream().filter(String.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(BusinessException.class::isInstance)
                .map(r -> ((BusinessException) r).code()).toList()).containsExactly(ErrorCode.ALREADY_PROCESSED);
        assertThat(count("SELECT COUNT(*) FROM training_ledger WHERE entry_type = 'REIMBURSE'")).isEqualTo(1);
        assertThat(balance2026().reimbursedAmount()).isEqualByComparingTo("600.00");
    }

    private static List<Object> race(Callable<Object> first, Callable<Object> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    try {
                        return task.call();
                    } catch (BusinessException e) {
                        return e;
                    }
                }));
            }
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- document store ------------------------------------------------------------------------

    @Test
    void documentsMustBeRealPdfPngOrJpegFilesWithAMatchingNameAndAtMostFiveMegabytes() {
        byte[] text = "just text".getBytes();
        byte[] large = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, large, 0, PDF.length);

        expectField(() -> claims.submit(employee.actor(), externalId, new BigDecimal("600.00"), true,
                new Upload("receipt.pdf", text), pdf("c.pdf")), ErrorCode.INVALID_DOCUMENT, "receipt");
        expectField(() -> claims.submit(employee.actor(), externalId, new BigDecimal("600.00"), true,
                new Upload("receipt.pdf", PNG), pdf("c.pdf")), ErrorCode.INVALID_DOCUMENT, "receipt");
        expectField(() -> claims.submit(employee.actor(), externalId, new BigDecimal("600.00"), true,
                new Upload("receipt.exe", PDF), pdf("c.pdf")), ErrorCode.INVALID_DOCUMENT, "receipt");
        expectField(() -> claims.submit(employee.actor(), externalId, new BigDecimal("600.00"), true,
                pdf("r.pdf"), new Upload("certificate.pdf", large)), ErrorCode.INVALID_DOCUMENT, "certificate");

        Long claimId = claims.submit(employee.actor(), externalId, new BigDecimal("600.00"), true,
                new Upload("receipt.png", PNG), new Upload("certificate.JPEG",
                        new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16}));
        assertThat(jdbc.queryForList("SELECT detected_content_type FROM claim_document WHERE claim_id = ? "
                + "ORDER BY document_type", String.class, claimId)).containsExactly("image/jpeg", "image/png");
    }

    @Test
    void theBrowserFileNameNeverChoosesTheStoragePath() {
        Long claimId = claims.submit(employee.actor(), externalId, new BigDecimal("600.00"), true,
                new Upload("../../../etc/passwd.pdf", PDF), new Upload("C:\\Windows\\system.ini.pdf", PDF));

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT original_name, storage_key, sha256 "
                + "FROM claim_document WHERE claim_id = ? ORDER BY document_type", claimId);
        assertThat(rows).extracting(r -> r.get("original_name")).containsExactly("system.ini.pdf", "passwd.pdf");
        for (Map<String, Object> row : rows) {
            String key = (String) row.get("storage_key");
            assertThat(key).matches("\\d{4}/\\d{2}/[0-9a-f-]{36}\\.pdf");
            assertThat(storage.exists(key)).isTrue();
            assertThat((String) row.get("sha256")).hasSize(64);
        }
    }

    @Test
    void filesOfARolledBackTransactionAreRemoved() {
        String[] keys = new String[1];
        transactionTemplate.executeWithoutResult(status -> {
            Long claimId = submitClaim(externalId, "600.00");
            keys[0] = jdbc.queryForObject("SELECT storage_key FROM claim_document WHERE claim_id = ? LIMIT 1",
                    String.class, claimId);
            assertThat(storage.exists(keys[0])).isTrue();
            status.setRollbackOnly();
        });

        assertThat(storage.exists(keys[0])).isFalse();
        assertThat(count("SELECT COUNT(*) FROM course_fee_application")).isZero();
        assertThat(count("SELECT COUNT(*) FROM claim_document")).isZero();
    }

    @Test
    void theSweepRemovesOldUnreferencedFilesOnly() throws IOException {
        Long claimId = submitClaim(externalId, "600.00");
        List<String> known = jdbc.queryForList("SELECT storage_key FROM claim_document WHERE claim_id = ?",
                String.class, claimId);
        Path root = Path.of("target/test-documents").toAbsolutePath();
        Path orphan = root.resolve("2026/10/" + UUID.randomUUID() + ".pdf");
        Path recentOrphan = root.resolve("2026/10/" + UUID.randomUUID() + ".pdf");
        Files.createDirectories(orphan.getParent());
        Files.write(orphan, PDF);
        Files.write(recentOrphan, PDF);
        FileTime old = FileTime.from(Instant.now().minus(Duration.ofHours(3)));
        Files.setLastModifiedTime(orphan, old);
        for (String key : known) {
            Files.setLastModifiedTime(root.resolve(key), old);
        }

        storage.sweepOrphans(new HashSet<>(jdbc.queryForList("SELECT storage_key FROM claim_document", String.class)),
                Duration.ofHours(1));

        assertThat(orphan).doesNotExist();
        assertThat(recentOrphan).exists();
        known.forEach(key -> assertThat(storage.exists(key)).isTrue());
        Files.deleteIfExists(recentOrphan);
    }
}
