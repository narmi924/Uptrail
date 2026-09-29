package com.uptrail.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.uptrail.application.AbstractApplicationIT;
import com.uptrail.application.service.ApplicationCommandService.Decision;
import com.uptrail.support.Fixtures;

/**
 * Operations page (audit search, outbox, ledger check) and the employee entitlement page with its ledger
 * movements.
 */
class OperationsIT extends AbstractApplicationIT {

    private MockHttpSession adminSession() throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/admin/login").user(admin.username())
                .password(Fixtures.PASSWORD)).andReturn().getRequest().getSession(false);
    }

    @Test
    void auditSearchFindsEventsByRecordAndEvent() throws Exception {
        Long id = submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00")).applicationId();
        commands.decide(manager.actor(), id, Decision.APPROVE, "Relevant to the migration.", version(id));
        MockHttpSession session = adminSession();

        mvc.perform(get("/admin/operations").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Relevant to the migration.")))
                .andExpect(content().string(Matchers.containsString("SUBMITTED")));
        String approvedOnly = mvc.perform(get("/admin/operations").param("type", "APPLICATION")
                        .param("key", id.toString()).param("eventType", "approved").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(approvedOnly).contains("Relevant to the migration.").contains("Showing 1–1 of 1 events");
        mvc.perform(get("/admin/operations").param("from", "2026-10-10").param("to", "2026-10-01").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Choose a period whose end is on or after its start.")));
    }

    @Test
    void theOutboxListsEmailsAndOnlyFailedOnesCanBeRetried() throws Exception {
        submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00"));
        Long outboxId = jdbc.queryForObject("SELECT id FROM email_outbox", Long.class);
        MockHttpSession session = adminSession();

        mvc.perform(get("/admin/operations").param("tab", "outbox").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("APPLICATION_SUBMITTED")))
                .andExpect(content().string(Matchers.containsString("mgr@example.com")));
        mvc.perform(post("/admin/outbox/{id}/retry", outboxId).session(session).with(csrf()))
                .andExpect(flash().attribute("flashError", Matchers.containsString("Only failed emails")));

        jdbc.update("UPDATE email_outbox SET status = 'FAILED', attempts = 5, last_error = 'MailSendException: down'");
        mvc.perform(get("/admin/operations").param("tab", "outbox").param("status", "FAILED").session(session))
                .andExpect(content().string(Matchers.containsString("MailSendException: down")))
                .andExpect(content().string(Matchers.containsString("Email outbox (1 failed)")));
        mvc.perform(post("/admin/outbox/{id}/retry", outboxId).session(session).with(csrf()))
                .andExpect(flash().attributeExists("flashSuccess"));

        assertThat(jdbc.queryForObject("SELECT status FROM email_outbox", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT attempts FROM email_outbox", Integer.class)).isZero();
    }

    @Test
    void theLedgerCheckReportsConsistencyAndMismatches() throws Exception {
        Long id = submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00")).applicationId();
        MockHttpSession session = adminSession();

        mvc.perform(get("/admin/operations").param("tab", "ledger").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Consistent")));

        // Simulate a damaged ledger: the application record says 700 while the ledger reserved 600.
        jdbc.update("UPDATE course_application SET course_fee = 700.00 WHERE id = ?", id);
        mvc.perform(get("/admin/operations").param("tab", "ledger").session(session))
                .andExpect(content().string(Matchers.containsString("1 mismatches")))
                .andExpect(content().string(Matchers.containsString(employee.employee().getFullName())));
    }

    @Test
    void operationsAreForAdministratorsOnly() throws Exception {
        mvc.perform(get("/admin/operations").session(staffSession(employee))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/operations").param("tab", "outbox").param("status", "BOGUS").session(adminSession()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theEntitlementPageShowsTheBalanceAndEveryMovement() throws Exception {
        Long id = submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00")).applicationId();
        commands.decide(manager.actor(), id, Decision.APPROVE, "OK", version(id));
        MockHttpSession session = staffSession(employee);

        String page = mvc.perform(get("/employee/entitlement").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(page).contains("SGD 1,400.00").contains("Reserved for a pending application").contains("Approved")
                .contains("/employee/applications/" + id).contains("Approved +600.00").contains("Pending -600.00");
        mvc.perform(get("/employee/entitlement").param("year", "2025").session(session))
                .andExpect(content().string(Matchers.containsString("No entitlement is configured for 2025")));
        mvc.perform(get("/employee/entitlement").param("year", "1999").session(session))
                .andExpect(content().string(Matchers.containsString("SGD 1,400.00")));
    }
}
