package com.uptrail.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.uptrail.service.LedgerReconciliationService;
import com.uptrail.support.AbstractMySqlIT;

/**
 * The sample activity goes through the real services and leaves a ledger that reconciles exactly with the
 * applications and claims.
 */
class SampleActivityIT extends AbstractMySqlIT {

    @Autowired
    private SampleOrganisation organisation;

    @Autowired
    private SampleActivity activity;

    @Autowired
    private SampleClaims claims;

    @Autowired
    private LedgerReconciliationService reconciliation;

    @Test
    void sampleApplicationsAndClaimsCoverEveryStatusAndTheLedgerReconciles() {
        SampleOrganisation.Staff staff = organisation.seed();
        SampleActivity.Created created = activity.seed(staff);
        int claimCount = claims.seed(staff, created.applications());

        List<String> statuses = jdbc.queryForList("SELECT DISTINCT status FROM course_application ORDER BY status",
                String.class);
        assertThat(statuses).containsExactlyInAnyOrder("APPLIED", "UPDATED", "APPROVED", "REJECTED", "DELETED",
                "CANCELLED", "COMPLETED");
        assertThat(created.applications()).hasSizeGreaterThanOrEqualTo(18);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_application a WHERE EXISTS "
                + "(SELECT 1 FROM application_day d WHERE d.application_id = a.id AND YEAR(d.training_date) = 2027) "
                + "AND YEAR(a.start_date) = 2026", Long.class)).isEqualTo(1);

        assertThat(claimCount).isEqualTo(5);
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM course_fee_application", String.class))
                .containsExactlyInAnyOrder("SUBMITTED", "APPROVED", "REJECTED", "REIMBURSED");
        assertThat(jdbc.queryForObject("SELECT MAX(revision) FROM course_fee_application", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM claim_document", Long.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT reimbursement_reference FROM course_fee_application WHERE status = 'REIMBURSED'",
                String.class)).startsWith("SIM-");

        LedgerReconciliationService.Result result = reconciliation.check();

        assertThat(result.accountsChecked()).isGreaterThan(30);
        assertThat(result.mismatches()).isEmpty();
    }
}
