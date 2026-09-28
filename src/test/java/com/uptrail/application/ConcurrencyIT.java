package com.uptrail.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.uptrail.application.domain.ApplicationDetails;
import com.uptrail.application.service.ApplicationCommandService.Decision;
import com.uptrail.application.service.ApplicationCommandService.SubmitResult;
import com.uptrail.entitlement.service.EntitlementService.Balance;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;
import com.uptrail.support.Fixtures.Person;

/**
 * Real concurrent transactions against MySQL. Two threads wait on a barrier and then call the service at
 * the same moment, each in its own transaction and connection (T42, T43, T44, AC-C).
 */
class ConcurrencyIT extends AbstractApplicationIT {

    /** Runs two tasks simultaneously and returns their results or exceptions. */
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

    private static long successes(List<Object> results) {
        return results.stream().filter(r -> !(r instanceof BusinessException)).count();
    }

    private static List<ErrorCode> failures(List<Object> results) {
        return results.stream().filter(BusinessException.class::isInstance)
                .map(r -> ((BusinessException) r).code()).toList();
    }

    @Test
    void twoApplicationsCompetingForTheSameBudgetCannotBothSucceed() throws Exception {
        Person racer = fixtures.employee("racer");
        fixtures.route(racer, manager);
        fixtures.account(racer, 2026, 20, "1000.00");
        ApplicationDetails first = external(MON_12_OCT, MON_12_OCT, "700.00");
        ApplicationDetails second = external(MON_12_OCT.plusDays(7), MON_12_OCT.plusDays(7), "700.00");

        List<Object> results = race(
                () -> commands.submit(racer.actor(), first, UUID.randomUUID().toString()),
                () -> commands.submit(racer.actor(), second, UUID.randomUUID().toString()));

        assertThat(successes(results)).isEqualTo(1);
        assertThat(failures(results)).containsExactly(ErrorCode.INSUFFICIENT_BUDGET);
        Balance balance = entitlements.balance(racer.id(), 2026);
        assertThat(balance.reservedAmount()).isEqualByComparingTo("700.00");
        assertThat(balance.availableBudget()).isEqualByComparingTo("300.00");
        assertThat(count("SELECT COUNT(*) FROM course_application WHERE employee_id = ?", racer.id())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM training_ledger")).isEqualTo(1);
    }

    @Test
    void twoApplicationsForTheSamePeriodCannotBothSucceed() throws Exception {
        ApplicationDetails first = external(MON_12_OCT, MON_12_OCT.plusDays(1), "100.00");
        ApplicationDetails second = external(MON_12_OCT.plusDays(1), MON_12_OCT.plusDays(1), "100.00");

        List<Object> results = race(
                () -> commands.submit(employee.actor(), first, UUID.randomUUID().toString()),
                () -> commands.submit(employee.actor(), second, UUID.randomUUID().toString()));

        assertThat(successes(results)).isEqualTo(1);
        assertThat(failures(results)).containsExactly(ErrorCode.PERIOD_OVERLAP);
        assertThat(count("SELECT COUNT(*) FROM course_application")).isEqualTo(1);
    }

    @Test
    void theSameDecisionSentTwiceAtOnceIsAppliedOnce() throws Exception {
        SubmitResult result = submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00"));
        long version = version(result.applicationId());

        List<Object> results = race(
                () -> {
                    commands.decide(manager.actor(), result.applicationId(), Decision.APPROVE, "OK", version);
                    return "approved";
                },
                () -> {
                    commands.decide(manager.actor(), result.applicationId(), Decision.APPROVE, "OK", version);
                    return "approved";
                });

        assertThat(successes(results)).isEqualTo(1);
        assertThat(failures(results)).containsExactly(ErrorCode.ALREADY_PROCESSED);
        assertThat(count("SELECT COUNT(*) FROM training_ledger WHERE entry_type = 'COMMIT'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM email_outbox WHERE template_code = 'APPLICATION_APPROVED'")).isEqualTo(1);
        assertThat(entitlements.balance(employee.id(), 2026).committedAmount()).isEqualByComparingTo("600.00");
    }

    @Test
    void anApprovalRacingAnUpdateNeverApprovesOutdatedData() throws Exception {
        SubmitResult result = submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00"));
        long version = version(result.applicationId());

        List<Object> results = race(
                () -> {
                    commands.update(employee.actor(), result.applicationId(),
                            external(MON_12_OCT, MON_12_OCT, "650.00"), version);
                    return "updated";
                },
                () -> {
                    commands.decide(manager.actor(), result.applicationId(), Decision.APPROVE, "OK", version);
                    return "approved";
                });

        assertThat(successes(results)).isEqualTo(1);
        Balance balance = entitlements.balance(employee.id(), 2026);
        if (results.contains("approved")) {
            assertThat(failures(results)).containsExactly(ErrorCode.INVALID_STATE);
            assertThat(balance.committedAmount()).isEqualByComparingTo("600.00");
        } else {
            assertThat(failures(results)).containsExactly(ErrorCode.STALE_VERSION);
            assertThat(balance.reservedAmount()).isEqualByComparingTo("650.00");
            assertThat(balance.committedAmount()).isEqualByComparingTo("0.00");
        }
    }
}
