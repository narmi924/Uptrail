package com.uptrail.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.uptrail.application.AbstractApplicationIT;
import com.uptrail.application.service.ApplicationCommandService.Decision;
import com.uptrail.claim.service.ClaimCommandService;
import com.uptrail.claim.service.DocumentStorage.Upload;
import com.uptrail.support.Fixtures;

/**
 * Role and workspace matrix over every page, API, CSV export and download: each cell is
 * the status a signed-out visitor, an employee, a manager or an administrator gets. Record-level scope
 * (another person's record gives 404) is covered by the feature tests; here the owner of each record is the
 * employee and the approver is the manager.
 */
class AuthorizationMatrixIT extends AbstractApplicationIT {

    private static final int OK = 200;
    private static final int LOGIN = 302;
    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;
    private static final int UNAUTHORIZED = 401;

    private enum Who { ANONYMOUS, EMPLOYEE, MANAGER, ADMIN }

    private record Cell(String path, int anonymous, int employee, int manager, int admin) {
        int expected(Who who) {
            return switch (who) {
                case ANONYMOUS -> anonymous;
                case EMPLOYEE -> employee;
                case MANAGER -> manager;
                case ADMIN -> admin;
            };
        }
    }

    @Autowired
    private ClaimCommandService claims;

    private final Map<Who, MockHttpSession> sessions = new LinkedHashMap<>();
    private Long applicationId;
    private Long claimId;
    private Long documentId;

    @BeforeEach
    void recordsAndSessions() throws Exception {
        applicationId = submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00")).applicationId();
        commands.decide(manager.actor(), applicationId, Decision.APPROVE, "OK", version(applicationId));
        clock.setDate(LocalDate.of(2026, 10, 16));
        commands.complete(employee.actor(), applicationId, version(applicationId), "Good course.");
        byte[] pdf = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        claimId = claims.submit(employee.actor(), applicationId, new BigDecimal("600.00"), true,
                new Upload("receipt.pdf", pdf), new Upload("certificate.pdf", pdf));
        claims.decide(manager.actor(), claimId, ClaimCommandService.Decision.APPROVE, "OK",
                jdbc.queryForObject("SELECT version FROM course_claim WHERE id = ?", Long.class, claimId));
        documentId = jdbc.queryForObject("SELECT MIN(id) FROM claim_document", Long.class);

        sessions.put(Who.ANONYMOUS, new MockHttpSession());
        sessions.put(Who.EMPLOYEE, staffSession(employee));
        sessions.put(Who.MANAGER, staffSession(manager));
        sessions.put(Who.ADMIN, (MockHttpSession) mvc.perform(formLogin("/admin/login").user(admin.username())
                .password(Fixtures.PASSWORD)).andReturn().getRequest().getSession(false));
    }

    private List<Cell> pages() {
        return List.of(
                // Employee workspace (managers hold the employee role as well)
                new Cell("/employee/dashboard", LOGIN, OK, OK, FORBIDDEN),
                new Cell("/employee/applications", LOGIN, OK, OK, FORBIDDEN),
                new Cell("/employee/applications/new", LOGIN, OK, OK, FORBIDDEN),
                new Cell("/employee/applications/" + applicationId, LOGIN, OK, NOT_FOUND, FORBIDDEN),
                new Cell("/employee/claims", LOGIN, OK, OK, FORBIDDEN),
                new Cell("/employee/claims/" + claimId, LOGIN, OK, NOT_FOUND, FORBIDDEN),
                new Cell("/employee/entitlement", LOGIN, OK, OK, FORBIDDEN),
                new Cell("/claims/documents/" + documentId, LOGIN, OK, OK, FORBIDDEN),
                // Manager workspace
                new Cell("/manager/approvals", LOGIN, FORBIDDEN, OK, FORBIDDEN),
                new Cell("/manager/applications/" + applicationId, LOGIN, FORBIDDEN, OK, FORBIDDEN),
                new Cell("/manager/claims", LOGIN, FORBIDDEN, OK, FORBIDDEN),
                new Cell("/manager/claims/" + claimId, LOGIN, FORBIDDEN, OK, FORBIDDEN),
                new Cell("/manager/team/history", LOGIN, FORBIDDEN, OK, FORBIDDEN),
                new Cell("/manager/team/applications/" + applicationId, LOGIN, FORBIDDEN, OK, FORBIDDEN),
                new Cell("/manager/reports", LOGIN, FORBIDDEN, OK, FORBIDDEN),
                new Cell("/manager/reports?tab=budget", LOGIN, FORBIDDEN, OK, FORBIDDEN),
                new Cell("/manager/reports/training.csv", LOGIN, FORBIDDEN, OK, FORBIDDEN),
                new Cell("/manager/reports/budget.csv", LOGIN, FORBIDDEN, OK, FORBIDDEN),
                // Shared
                new Cell("/calendar", LOGIN, OK, OK, OK),
                // Administration workspace (a staff session is not an administration session)
                new Cell("/admin/dashboard", LOGIN, FORBIDDEN, FORBIDDEN, OK),
                new Cell("/admin/staff", LOGIN, FORBIDDEN, FORBIDDEN, OK),
                new Cell("/admin/staff/new", LOGIN, FORBIDDEN, FORBIDDEN, OK),
                new Cell("/admin/staff/" + employee.id(), LOGIN, FORBIDDEN, FORBIDDEN, OK),
                new Cell("/admin/routing", LOGIN, FORBIDDEN, FORBIDDEN, OK),
                new Cell("/admin/entitlements", LOGIN, FORBIDDEN, FORBIDDEN, OK),
                new Cell("/admin/catalogue", LOGIN, FORBIDDEN, FORBIDDEN, OK),
                new Cell("/admin/holidays", LOGIN, FORBIDDEN, FORBIDDEN, OK),
                new Cell("/admin/reimbursements", LOGIN, FORBIDDEN, FORBIDDEN, OK),
                new Cell("/admin/claims/" + claimId, LOGIN, FORBIDDEN, FORBIDDEN, OK),
                new Cell("/admin/claims/documents/" + documentId, LOGIN, FORBIDDEN, FORBIDDEN, OK),
                new Cell("/admin/operations", LOGIN, FORBIDDEN, FORBIDDEN, OK),
                new Cell("/admin/operations?tab=outbox", LOGIN, FORBIDDEN, FORBIDDEN, OK),
                new Cell("/admin/operations?tab=ledger", LOGIN, FORBIDDEN, FORBIDDEN, OK),
                // JSON APIs answer 401/403 as JSON instead of redirecting
                new Cell("/api/v1/calendar", UNAUTHORIZED, OK, OK, OK),
                new Cell("/api/v1/catalogue", UNAUTHORIZED, OK, OK, FORBIDDEN));
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request, Who who) throws Exception {
        return mvc.perform(request.session(sessions.get(who))).andReturn().getResponse();
    }

    @Test
    void everyPageApiExportAndDownloadAnswersEachRoleAsSpecified() throws Exception {
        List<String> mismatches = new ArrayList<>();
        for (Cell cell : pages()) {
            for (Who who : Who.values()) {
                MockHttpServletResponse response = perform(get(cell.path()), who);
                if (response.getStatus() != cell.expected(who)) {
                    mismatches.add(who + " GET " + cell.path() + " -> " + response.getStatus() + ", expected "
                            + cell.expected(who));
                }
                String contentType = response.getContentType();
                if (response.getStatus() == FORBIDDEN && !cell.path().startsWith("/api/")
                        && contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
                    mismatches.add(who + " GET " + cell.path() + " answered a page request with JSON");
                }
                if (response.getStatus() == LOGIN) {
                    String target = response.getRedirectedUrl();
                    String expectedLogin = cell.path().startsWith("/admin/") ? "/admin/login" : "/login";
                    if (target == null || !target.endsWith(expectedLogin)) {
                        mismatches.add(who + " GET " + cell.path() + " redirected to " + target);
                    }
                }
            }
        }
        assertThat(mismatches).isEmpty();
    }

    @Test
    void apiErrorsAreJsonAndNeverTheLoginPage() throws Exception {
        MockHttpServletResponse anonymous = perform(get("/api/v1/catalogue"), Who.ANONYMOUS);
        MockHttpServletResponse admin = perform(get("/api/v1/catalogue"), Who.ADMIN);
        MockHttpServletResponse preview = perform(post("/api/v1/applications/preview").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"), Who.ADMIN);

        assertThat(anonymous.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(anonymous.getContentAsString()).contains("\"code\":\"AUTHENTICATION_REQUIRED\"");
        assertThat(admin.getStatus()).isEqualTo(FORBIDDEN);
        assertThat(admin.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
        assertThat(preview.getStatus()).isEqualTo(FORBIDDEN);
    }

    @Test
    void stateChangesNeedPostWithACsrfToken() throws Exception {
        String approve = "/manager/applications/" + applicationId + "/decision";
        String reimburse = "/admin/claims/" + claimId + "/reimburse";
        String delete = "/employee/applications/" + applicationId + "/delete";

        assertThat(perform(post(approve).param("decision", "APPROVE").param("reason", "x"), Who.MANAGER).getStatus())
                .isEqualTo(FORBIDDEN);
        assertThat(perform(post(reimburse), Who.ADMIN).getStatus()).isEqualTo(FORBIDDEN);
        assertThat(perform(post(delete), Who.EMPLOYEE).getStatus()).isEqualTo(FORBIDDEN);
        assertThat(perform(post("/logout"), Who.EMPLOYEE).getStatus()).isEqualTo(FORBIDDEN);
        assertThat(perform(post(delete), Who.EMPLOYEE).getContentType())
                .as("a page form without a token gets the error page, not JSON").isNull();
        for (String path : List.of(approve, reimburse, delete, "/employee/applications/" + applicationId + "/cancel",
                "/manager/claims/" + claimId + "/decision")) {
            Who who = path.startsWith("/admin/") ? Who.ADMIN : path.startsWith("/manager/") ? Who.MANAGER : Who.EMPLOYEE;
            assertThat(perform(get(path), who).getStatus()).as("GET " + path).isEqualTo(405);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM course_claim WHERE id = ?", String.class, claimId))
                .isEqualTo("APPROVED");
    }

    @Test
    void theWrongRoleCannotPostEvenWithAValidToken() throws Exception {
        assertThat(perform(post("/admin/claims/" + claimId + "/reimburse").with(csrf())
                .param("expectedVersion", "0"), Who.MANAGER).getStatus()).isEqualTo(FORBIDDEN);
        assertThat(perform(post("/manager/claims/" + claimId + "/decision").with(csrf())
                .param("decision", "REJECT").param("reason", "x").param("expectedVersion", "0"), Who.EMPLOYEE)
                .getStatus()).isEqualTo(FORBIDDEN);
        assertThat(perform(post("/admin/staff/new").with(csrf()).param("username", "intruder"), Who.EMPLOYEE)
                .getStatus()).isEqualTo(FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT status FROM course_claim WHERE id = ?", String.class, claimId))
                .isEqualTo("APPROVED");
    }
}
