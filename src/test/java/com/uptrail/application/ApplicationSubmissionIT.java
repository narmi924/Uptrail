package com.uptrail.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import com.uptrail.application.domain.ApplicationDetails;
import com.uptrail.application.service.ApplicationCommandService.SubmitResult;
import com.uptrail.catalogue.domain.CategoryCode;
import com.uptrail.entitlement.domain.Session;
import com.uptrail.entitlement.service.EntitlementService.Balance;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;
import com.uptrail.support.Fixtures.Person;

/**
 * Submission rules and their side effects (T10-T15, T18-T25, T27, T30, T31, T46-T48, AC-A) and the
 * eligibility preview API.
 */
class ApplicationSubmissionIT extends AbstractApplicationIT {

    private void expectError(Person person, ApplicationDetails details, ErrorCode code, String messagePart) {
        assertThatThrownBy(() -> submit(person, details))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(code);
                    assertThat(e.getMessage()).contains(messagePart);
                });
    }

    @Test
    void submissionReservesDaysAndBudgetWritesAuditAndQueuesManagerEmail() {
        SubmitResult result = submit(employee, external(MON_12_OCT, MON_12_OCT.plusDays(1), "600.00"));

        assertThat(result.referenceNo()).matches("CATS-2026-\\d{6}");
        assertThat(statusOf(result.applicationId())).isEqualTo("APPLIED");
        assertThat(count("SELECT COUNT(*) FROM application_day WHERE application_id = ?", result.applicationId()))
                .isEqualTo(2);
        Balance balance = entitlements.balance(employee.id(), 2026);
        assertThat(balance.reservedUnits()).isEqualTo(4);
        assertThat(balance.reservedAmount()).isEqualByComparingTo("600.00");
        assertThat(balance.availableUnits()).isEqualTo(16);
        assertThat(balance.availableBudget()).isEqualByComparingTo("1400.00");
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE aggregate_type = 'APPLICATION' AND event_type = "
                + "'SUBMITTED' AND aggregate_key = ?", result.applicationId().toString())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM email_outbox WHERE template_code = 'APPLICATION_SUBMITTED' "
                + "AND recipient_employee_id = ? AND status = 'PENDING'", manager.id())).isEqualTo(1);
    }

    @Test
    void budgetExactlyEnoughIsAccepted() {
        submit(employee, external(MON_12_OCT, MON_12_OCT, "2000.00"));

        assertThat(entitlements.balance(employee.id(), 2026).availableBudget()).isEqualByComparingTo("0.00");
    }

    @Test
    void insufficientBudgetIsRefusedWithoutAnySideEffect() {
        expectError(employee, external(MON_12_OCT, MON_12_OCT, "2000.01"), ErrorCode.INSUFFICIENT_BUDGET,
                "SGD 2000.01 is required; SGD 2000.00 remains");

        assertThat(count("SELECT COUNT(*) FROM course_application")).isZero();
        assertThat(count("SELECT COUNT(*) FROM training_ledger")).isZero();
        assertThat(count("SELECT COUNT(*) FROM audit_event")).isZero();
        assertThat(count("SELECT COUNT(*) FROM email_outbox")).isZero();
    }

    @Test
    void insufficientTrainingDaysAreRefused() {
        Person shortOfDays = fixtures.employee("short");
        fixtures.route(shortOfDays, manager);
        fixtures.account(shortOfDays, 2026, 2, "2000.00");

        expectError(shortOfDays, external(MON_12_OCT, MON_12_OCT.plusDays(1), "100.00"), ErrorCode.INSUFFICIENT_DAYS,
                "2 days of training in 2026 are required; 1 day remain");
    }

    @Test
    void requiredFieldsAreCheckedOnTheServer() {
        ApplicationDetails blank = new ApplicationDetails(CategoryCode.EXTERNAL, null, "  ", "Provider", MON_12_OCT,
                MON_12_OCT, Session.AM, Session.PM, new BigDecimal("100.00"), " ", null);

        assertThatThrownBy(() -> submit(employee, blank)).isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.fieldErrors()).containsKeys("courseTitle", "justification"));
    }

    @Test
    void courseMustStartInTheFutureAndEndAfterItStarts() {
        expectError(employee, external(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), "100.00"),
                ErrorCode.VALIDATION_FAILED, "must start on a future date");
        expectError(employee, external(MON_12_OCT, MON_12_OCT.minusDays(1), "100.00"),
                ErrorCode.VALIDATION_FAILED, "end date must be on or after the start date");
    }

    @Test
    void boundariesMustBeWorkingDays() {
        expectError(employee, external(LocalDate.of(2026, 10, 10), MON_12_OCT, "100.00"),
                ErrorCode.NON_WORKING_BOUNDARY, "(Weekend)");
        expectError(employee, external(MON_12_OCT, FIXTURE_HOLIDAY, "100.00"),
                ErrorCode.NON_WORKING_BOUNDARY, "Public holiday");
    }

    @Test
    void halfDaysOnlyForFreeInternalTraining() {
        SubmitResult halfDay = submit(employee, internal(MON_12_OCT, Session.AM, MON_12_OCT, Session.AM));
        assertThat(count("SELECT SUM(units) FROM application_day WHERE application_id = ?", halfDay.applicationId()))
                .isEqualTo(1);

        ApplicationDetails externalHalfDay = new ApplicationDetails(CategoryCode.EXTERNAL, null, "Course",
                "Provider", MON_12_OCT.plusDays(3), MON_12_OCT.plusDays(3), Session.AM, Session.AM,
                new BigDecimal("50.00"), "Reason", null);
        expectError(employee, externalHalfDay, ErrorCode.VALIDATION_FAILED, "Half-day sessions are allowed");

        ApplicationDetails paidInternal = new ApplicationDetails(CategoryCode.INTERNAL, null, "Course", null,
                MON_12_OCT.plusDays(3), MON_12_OCT.plusDays(3), Session.AM, Session.PM, new BigDecimal("10.00"),
                "Reason", null);
        expectError(employee, paidInternal, ErrorCode.VALIDATION_FAILED, "Internal training carries no course fee");
    }

    @Test
    void overlappingActiveApplicationsAreRefusedEvenForDifferentHalfDays() {
        SubmitResult first = submit(employee, internal(MON_12_OCT, Session.AM, MON_12_OCT, Session.AM));
        String reference = jdbc.queryForObject("SELECT reference_no FROM course_application WHERE id = ?",
                String.class, first.applicationId());

        expectError(employee, internal(MON_12_OCT, Session.PM, MON_12_OCT, Session.PM), ErrorCode.PERIOD_OVERLAP,
                reference);
        expectError(employee, external(MON_12_OCT.minusDays(3), MON_12_OCT.plusDays(1), "100.00"),
                ErrorCode.PERIOD_OVERLAP, reference);
    }

    @Test
    void anotherEmployeesApplicationDoesNotBlock() {
        Person colleague = fixtures.employee("colleague");
        fixtures.route(colleague, manager);
        fixtures.account(colleague, 2026, 20, "2000.00");
        submit(colleague, external(MON_12_OCT, MON_12_OCT, "100.00"));

        assertThat(submit(employee, external(MON_12_OCT, MON_12_OCT, "100.00")).applicationId()).isNotNull();
    }

    @Test
    void missingConfigurationBlocksSubmission() {
        Person noApprover = fixtures.employee("lonely");
        fixtures.account(noApprover, 2026, 20, "2000.00");
        expectError(noApprover, external(MON_12_OCT, MON_12_OCT, "100.00"), ErrorCode.MISSING_APPROVER,
                "No approving manager is configured");

        Person noAccount = fixtures.employee("unfunded");
        fixtures.route(noAccount, manager);
        expectError(noAccount, external(MON_12_OCT, MON_12_OCT, "100.00"), ErrorCode.MISSING_ANNUAL_ACCOUNT,
                "No training entitlement is configured for 2026");
    }

    @Test
    void unconfirmedHolidayCalendarAndClosedYearsBlockSubmission() {
        fixtures.draftYear(2028);
        clock.setDate(LocalDate.of(2027, 11, 1));
        expectError(employee, external(LocalDate.of(2028, 1, 4), LocalDate.of(2028, 1, 4), "100.00"),
                ErrorCode.HOLIDAY_CALENDAR_UNCONFIRMED, "for 2028 has not been confirmed");

        clock.setDate(LocalDate.of(2026, 10, 5));
        expectError(employee, external(LocalDate.of(2028, 1, 4), LocalDate.of(2028, 1, 4), "100.00"),
                ErrorCode.YEAR_NOT_OPEN, "can only cover 2026 and 2027");
    }

    @Test
    void crossYearCourseReservesDaysInEachYearAndTheFeeInTheStartYear() {
        SubmitResult result = submit(employee, external(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 4), "800.00"));

        Balance year2026 = entitlements.balance(employee.id(), 2026);
        Balance year2027 = entitlements.balance(employee.id(), 2027);
        assertThat(year2026.reservedUnits()).isEqualTo(4);
        assertThat(year2026.reservedAmount()).isEqualByComparingTo("800.00");
        assertThat(year2027.reservedUnits()).isEqualTo(2);
        assertThat(year2027.reservedAmount()).isEqualByComparingTo("0.00");
        assertThat(count("SELECT COUNT(*) FROM training_ledger WHERE application_id = ?", result.applicationId()))
                .isEqualTo(2);
    }

    @Test
    void resubmittingTheSameFormIsIdempotentAndReusingItsKeyWithOtherDataIsRefused() {
        String key = UUID.randomUUID().toString();
        SubmitResult first = commands.submit(employee.actor(), external(MON_12_OCT, MON_12_OCT, "300.00"), key);
        SubmitResult again = commands.submit(employee.actor(), external(MON_12_OCT, MON_12_OCT, "300.00"), key);

        assertThat(again.applicationId()).isEqualTo(first.applicationId());
        assertThat(again.replayed()).isTrue();
        assertThat(count("SELECT COUNT(*) FROM training_ledger")).isEqualTo(1);
        assertThatThrownBy(() -> commands.submit(employee.actor(), external(MON_12_OCT, MON_12_OCT, "350.00"), key))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    void previewExplainsTheResultAndWritesNothing() throws Exception {
        MockHttpSession session = staffSession(employee);
        String body = """
                {"category":"EXTERNAL","courseTitle":"Spring","providerName":"P","startDate":"2026-10-09",
                 "endDate":"2026-10-15","startSession":"AM","endSession":"PM","courseFee":"2500.00",
                 "justification":"Needed"}
                """;

        mvc.perform(post("/api/v1/applications/preview").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.trainingUnits").value(8))
                .andExpect(jsonPath("$.excludedDates.length()").value(3))
                .andExpect(jsonPath("$.excludedDates[2].reason").value(Matchers.startsWith("Public holiday")))
                .andExpect(jsonPath("$.annualAllocations[0].budgetAvailableBefore").value(2000.00))
                .andExpect(jsonPath("$.problems[0].code").value("INSUFFICIENT_BUDGET"))
                .andExpect(jsonPath("$.approverName").value(manager.employee().getFullName()));

        assertThat(count("SELECT COUNT(*) FROM course_application")).isZero();
        assertThat(count("SELECT COUNT(*) FROM training_ledger")).isZero();
        assertThat(count("SELECT COUNT(*) FROM email_outbox")).isZero();
    }

    @Test
    void previewOfSomeoneElsesApplicationReturnsNotFoundJson() throws Exception {
        Person other = fixtures.employee("other");
        fixtures.route(other, manager);
        fixtures.account(other, 2026, 20, "2000.00");
        SubmitResult theirs = submit(other, external(MON_12_OCT, MON_12_OCT, "100.00"));

        mvc.perform(post("/api/v1/applications/preview").session(staffSession(employee)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationId\":" + theirs.applicationId() + ",\"category\":\"EXTERNAL\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void malformedPreviewJsonGetsA400Body() throws Exception {
        mvc.perform(post("/api/v1/applications/preview").session(staffSession(employee)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startDate\":\"not-a-date\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void theApplicationFormSubmitsAndRedirectsToTheNewApplication() throws Exception {
        MockHttpSession session = staffSession(employee);

        mvc.perform(post("/employee/applications").session(session).with(csrf())
                        .param("category", "EXTERNAL").param("courseTitle", "Cloud Architecture Foundations")
                        .param("providerName", "Cloud Guild Academy").param("startDate", "2026-10-19")
                        .param("endDate", "2026-10-20").param("startSession", "AM").param("endSession", "PM")
                        .param("courseFee", "850.00").param("justification", "Design reviews for our platform.")
                        .param("clientRequestId", UUID.randomUUID().toString()))
                .andExpect(redirectedUrlPattern("/employee/applications/*"));

        mvc.perform(post("/employee/applications").session(session).with(csrf())
                        .param("category", "EXTERNAL").param("courseTitle", "Too expensive")
                        .param("providerName", "P").param("startDate", "2026-11-02").param("endDate", "2026-11-02")
                        .param("courseFee", "5000.00").param("justification", "Reason")
                        .param("clientRequestId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("SGD 5000.00 is required")))
                .andExpect(content().string(Matchers.containsString("value=\"Too expensive\"")));
    }
}
