package com.uptrail.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import com.uptrail.claim.service.ClaimCommandService.Decision;
import com.uptrail.support.Fixtures;
import com.uptrail.support.Fixtures.Person;

/**
 * Claim pages for employees, managers and administrators, multipart submission with CSRF, and authorised
 * document downloads.
 */
class ClaimWebIT extends AbstractClaimIT {

    private MockHttpSession adminSession() throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/admin/login").user(admin.username())
                .password(Fixtures.PASSWORD)).andReturn().getRequest().getSession(false);
    }

    private static MockMultipartFile file(String field, String name) {
        return new MockMultipartFile(field, name, "application/pdf", PDF);
    }

    private Long documentOf(Long claimId) {
        return jdbc.queryForObject("SELECT id FROM claim_document WHERE claim_id = ? AND document_type = 'RECEIPT' "
                + "ORDER BY claim_revision DESC LIMIT 1", Long.class, claimId);
    }

    @Test
    void theEmployeeSubmitsAClaimThroughTheForm() throws Exception {
        MockHttpSession session = staffSession(employee);

        mvc.perform(get("/employee/claims").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Spring Application Development")))
                .andExpect(content().string(Matchers.containsString("Claim fee")));
        mvc.perform(get("/employee/applications/{id}", externalId).session(session))
                .andExpect(content().string(Matchers.containsString("Claim course fee")));
        mvc.perform(get("/employee/claims/new").param("applicationId", externalId.toString()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("enctype=\"multipart/form-data\"")));

        mvc.perform(multipart("/employee/claims").file(file("receipt", "receipt.pdf"))
                        .file(file("certificate", "certificate.pdf")).param("applicationId", externalId.toString())
                        .param("amount", "600.00").param("paidByEmployee", "true").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/employee/claims").file(file("receipt", "receipt.pdf"))
                        .file(file("certificate", "certificate.pdf")).param("applicationId", externalId.toString())
                        .param("amount", "600.00").param("paidByEmployee", "true").session(session).with(csrf()))
                .andExpect(redirectedUrlPattern("/employee/claims/*"))
                .andExpect(flash().attribute("flashSuccess", Matchers.containsString("Claim submitted")));

        Long claimId = jdbc.queryForObject("SELECT id FROM course_claim", Long.class);
        mvc.perform(get("/employee/claims/{id}", claimId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("receipt.pdf")))
                .andExpect(content().string(Matchers.containsString("Waiting for")));
        mvc.perform(get("/employee/applications/{id}", externalId).session(session))
                .andExpect(content().string(Matchers.containsString("View fee claim")));
    }

    @Test
    void aRefusedSubmissionShowsTheReasonsNextToTheFieldsAndKeepsTheAmount() throws Exception {
        MockHttpSession session = staffSession(employee);

        mvc.perform(multipart("/employee/claims").file(file("certificate", "certificate.pdf"))
                        .param("applicationId", externalId.toString()).param("amount", "999.00")
                        .param("paidByEmployee", "true").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("cannot be more than the approved course fee")))
                .andExpect(content().string(Matchers.containsString("Receipt: Attach the file.")))
                .andExpect(content().string(Matchers.containsString("value=\"999.00\"")));
        mvc.perform(multipart("/employee/claims").param("applicationId", externalId.toString())
                        .param("amount", "abc").param("paidByEmployee", "true").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Enter the amount as a number")));
        mvc.perform(get("/employee/claims/new").param("applicationId", internalId.toString()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("This course cannot be claimed")));
        assertThat(count("SELECT COUNT(*) FROM course_claim")).isZero();
    }

    @Test
    void theEmployeeRevisesARejectedClaimOnItsPage() throws Exception {
        Long claimId = submitClaim(externalId, "600.00");
        claims.decide(manager.actor(), claimId, Decision.REJECT, "Receipt unreadable.", claimVersion(claimId));
        MockHttpSession session = staffSession(employee);

        mvc.perform(get("/employee/claims/{id}", claimId).session(session))
                .andExpect(content().string(Matchers.containsString("Revise and resubmit")))
                .andExpect(content().string(Matchers.containsString("Receipt unreadable.")));
        mvc.perform(multipart("/employee/claims/{id}/resubmit", claimId).file(file("receipt", "receipt-2.pdf"))
                        .file(file("certificate", "certificate-2.pdf")).param("amount", "550.00")
                        .param("paidByEmployee", "true").param("expectedVersion", String.valueOf(claimVersion(claimId)))
                        .session(session).with(csrf()))
                .andExpect(redirectedUrl("/employee/claims/" + claimId));

        assertThat(claimStatus(claimId)).isEqualTo("SUBMITTED");
        mvc.perform(get("/employee/claims/{id}", claimId).session(session))
                .andExpect(content().string(Matchers.containsString("receipt-2.pdf")))
                .andExpect(content().string(Matchers.containsString("(current)")));
    }

    @Test
    void theManagerDecidesFromTheClaimsTab() throws Exception {
        Long claimId = submitClaim(externalId, "600.00");
        MockHttpSession session = staffSession(manager);

        mvc.perform(get("/manager/approvals").session(session))
                .andExpect(content().string(Matchers.matchesPattern("(?s).*Fee claims\\s*<span class=\"ut-tab-count\">1</span>.*")));
        mvc.perform(get("/manager/claims").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Spring Application Development")));
        mvc.perform(get("/manager/claims/{id}", claimId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Record decision")));
        mvc.perform(post("/manager/claims/{id}/decision", claimId).param("decision", "APPROVE").param("reason", " ")
                        .param("expectedVersion", String.valueOf(claimVersion(claimId))).session(session).with(csrf()))
                .andExpect(redirectedUrl("/manager/claims/" + claimId))
                .andExpect(flash().attributeExists("flashError"));
        mvc.perform(post("/manager/claims/{id}/decision", claimId).param("decision", "APPROVE")
                        .param("reason", "Documents match.")
                        .param("expectedVersion", String.valueOf(claimVersion(claimId))).session(session).with(csrf()))
                .andExpect(redirectedUrl("/manager/claims"));

        assertThat(claimStatus(claimId)).isEqualTo("APPROVED");
        Person stranger = fixtures.manager("stranger");
        mvc.perform(get("/manager/claims/{id}", claimId).session(staffSession(stranger))).andExpect(status().isNotFound());
        mvc.perform(get("/manager/claims").session(staffSession(employee))).andExpect(status().isForbidden());
    }

    @Test
    void theAdministratorRegistersTheReimbursementFromTheQueue() throws Exception {
        Long claimId = approvedClaim();
        MockHttpSession session = adminSession();

        mvc.perform(get("/admin/reimbursements").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.matchesPattern("(?s).*Awaiting registration\\s*<span class=\"ut-tab-count\">1</span>.*")))
                .andExpect(content().string(Matchers.containsString("Documents match.")));
        mvc.perform(get("/admin/claims/{id}", claimId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("no payment is initiated")));
        mvc.perform(post("/admin/claims/{id}/reimburse", claimId)
                        .param("expectedVersion", String.valueOf(claimVersion(claimId))).session(session).with(csrf()))
                .andExpect(redirectedUrl("/admin/reimbursements"))
                .andExpect(flash().attribute("flashSuccess", Matchers.containsString("SIM-20261016-")));
        mvc.perform(post("/admin/claims/{id}/reimburse", claimId)
                        .param("expectedVersion", String.valueOf(claimVersion(claimId))).session(session).with(csrf()))
                .andExpect(redirectedUrl("/admin/claims/" + claimId))
                .andExpect(flash().attribute("flashError", Matchers.containsString("already registered")));

        mvc.perform(get("/admin/reimbursements").param("tab", "registered").session(session))
                .andExpect(content().string(Matchers.containsString("SIM-20261016-")));
        mvc.perform(get("/employee/claims/{id}", claimId).session(staffSession(employee)))
                .andExpect(content().string(Matchers.containsString("Recorded in demo; no payment is initiated.")));
    }

    @Test
    void administratorsSeeOnlyApprovedOrReimbursedClaims() throws Exception {
        Long claimId = submitClaim(externalId, "600.00");
        MockHttpSession session = adminSession();

        mvc.perform(get("/admin/claims/{id}", claimId).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/admin/claims/documents/{id}", documentOf(claimId)).session(session))
                .andExpect(status().isNotFound());
        mvc.perform(get("/admin/reimbursements").session(staffSession(employee)))
                .andExpect(status().isForbidden());
    }

    @Test
    void documentsDownloadOnlyForTheClaimantTheAssignedManagerAndAdministratorsAfterApproval() throws Exception {
        Long claimId = submitClaim(externalId, "600.00");
        Long documentId = documentOf(claimId);
        Person colleague = fixtures.employee("colleague");
        fixtures.route(colleague, manager);
        Person otherManager = fixtures.manager("mgr2");

        mvc.perform(get("/claims/documents/{id}", documentId).session(staffSession(employee)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment;")))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("receipt.pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")))
                .andExpect(content().bytes(PDF));
        mvc.perform(get("/claims/documents/{id}", documentId).session(staffSession(manager)))
                .andExpect(status().isOk());
        mvc.perform(get("/claims/documents/{id}", documentId).session(staffSession(colleague)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/claims/documents/{id}", documentId).session(staffSession(otherManager)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/claims/documents/{id}", 999999L).session(staffSession(employee)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/claims/documents/{id}", documentId)).andExpect(status().is3xxRedirection());

        claims.decide(manager.actor(), claimId, Decision.APPROVE, "OK", claimVersion(claimId));
        mvc.perform(get("/admin/claims/documents/{id}", documentId).session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF));
        mvc.perform(get("/claims/documents/{id}", documentId).session(adminSession()))
                .andExpect(status().isForbidden());
    }
}
