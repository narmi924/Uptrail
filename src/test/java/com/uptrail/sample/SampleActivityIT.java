package com.uptrail.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.uptrail.admin.service.LedgerReconciliationService;
import com.uptrail.support.AbstractMySqlIT;

/**
 * The sample activity goes through the real services and leaves a ledger that reconciles exactly with the
 * applications (T50 over a realistic data set).
 */
class SampleActivityIT extends AbstractMySqlIT {

    @Autowired
    private SampleOrganisation organisation;

    @Autowired
    private SampleActivity activity;

    @Autowired
    private LedgerReconciliationService reconciliation;

    @Test
    void sampleApplicationsCoverEveryStatusAndTheLedgerReconciles() {
        SampleActivity.Created created = activity.seed(organisation.seed());

        List<String> statuses = jdbc.queryForList("SELECT DISTINCT status FROM course_application ORDER BY status",
                String.class);
        assertThat(statuses).containsExactlyInAnyOrder("APPLIED", "UPDATED", "APPROVED", "REJECTED", "DELETED",
                "CANCELLED", "COMPLETED");
        assertThat(created.applications()).hasSizeGreaterThanOrEqualTo(18);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_application a WHERE EXISTS "
                + "(SELECT 1 FROM application_day d WHERE d.application_id = a.id AND YEAR(d.training_date) = 2027) "
                + "AND YEAR(a.start_date) = 2026", Long.class)).isEqualTo(1);

        LedgerReconciliationService.Result result = reconciliation.check();

        assertThat(result.accountsChecked()).isGreaterThan(30);
        assertThat(result.mismatches()).isEmpty();
    }
}
