package com.uptrail.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.mock.web.MockHttpSession;

import com.uptrail.application.service.ApplicationCommandService.Decision;
import com.uptrail.application.service.ApplicationCommandService.SubmitResult;
import com.uptrail.entitlement.service.EntitlementService.Balance;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.support.Fixtures.Person;

/**
 * Update, delete, decisions, cancellation and completion with their ledger effects (T26, T28, T29,
 * T35-T41, T44, T45, T49, AC-B, AC-E) and the pages that drive them.
 */
class ApplicationLifecycleIT extends AbstractApplicationIT {

    private static void expectCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    private Balance balance2026() {
        return entitlements.balance(employee.id(), 2026);
    }

    @Test
    void updateReplacesTheReservationInsteadOfAddingToIt() {
        fixtures.account(fixtures.employee("unused"), 2026, 20, "1000.00");
        SubmitResult result = submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00"));

        commands.update(employee.actor(), result.applicationId(), external(MON_12_OCT, MON_12_OCT, "700.00"),
                version(result.applicationId()));

        assertThat(statusOf(result.applicationId())).isEqualTo("UPDATED");
        assertThat(balance2026().reservedAmount()).isEqualByComparingTo("700.00");
        assertThat(balance2026().availableBudget()).isEqualByComparingTo("1300.00");
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE event_type = 'UPDATED' "
                + "AND JSON_EXTRACT(snapshot_json, '$.before.fee') = '600.00' "
                + "AND JSON_EXTRACT(snapshot_json, '$.after.fee') = '700.00'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM email_outbox WHERE template_code = 'APPLICATION_UPDATED'")).isEqualTo(1);
    }

    @Test
    void anUpdateMayOverlapTheApplicationItselfButNotOthers() {
        SubmitResult first = submit(employee, external(MON_12_OCT, MON_12_OCT.plusDays(1), "100.00"));
        SubmitResult second = submit(employee, external(MON_12_OCT.plusDays(3), MON_12_OCT.plusDays(3), "100.00"));

        commands.update(employee.actor(), first.applicationId(), external(MON_12_OCT.plusDays(1),
                MON_12_OCT.plusDays(1), "100.00"), version(first.applicationId()));
        expectCode(() -> commands.update(employee.actor(), second.applicationId(),
                external(MON_12_OCT.plusDays(1), MON_12_OCT.plusDays(3), "100.00"), version(second.applicationId())),
                ErrorCode.PERIOD_OVERLAP);
    }

    @Test
    void movingACrossYearCourseIntoOneYearReleasesTheOtherYear() {
        SubmitResult result = submit(employee, external(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 4), "800.00"));
        assertThat(entitlements.balance(employee.id(), 2027).reservedUnits()).isEqualTo(2);

        commands.update(employee.actor(), result.applicationId(),
                external(LocalDate.of(2026, 12, 28), LocalDate.of(2026, 12, 29), "800.00"), version(result.applicationId()));

        assertThat(entitlements.balance(employee.id(), 2027).reservedUnits()).isZero();
        assertThat(balance2026().reservedUnits()).isEqualTo(4);
        assertThat(balance2026().reservedAmount()).isEqualByComparingTo("800.00");
    }

    @Test
    void deleteReleasesTheReservationKeepsHistoryAndUnblocksThePeriod() {
        SubmitResult result = submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00"));

        commands.delete(employee.actor(), result.applicationId(), version(result.applicationId()));

        assertThat(statusOf(result.applicationId())).isEqualTo("DELETED");
        assertThat(balance2026().reservedUnits()).isZero();
        assertThat(balance2026().reservedAmount()).isEqualByComparingTo("0.00");
        assertThat(submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00")).applicationId()).isNotNull();
        expectCode(() -> commands.delete(employee.actor(), result.applicationId(), version(result.applicationId())),
                ErrorCode.INVALID_STATE);
    }

    @Test
    void approvalConvertsTheReservationAndNotifiesTheApplicantWithTheReason() {
        SubmitResult result = submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00"));

        commands.decide(manager.actor(), result.applicationId(), Decision.APPROVE,
                "Relevant to the platform migration; share notes with the team.", version(result.applicationId()));

        assertThat(statusOf(result.applicationId())).isEqualTo("APPROVED");
        Balance balance = balance2026();
        assertThat(balance.reservedAmount()).isEqualByComparingTo("0.00");
        assertThat(balance.committedAmount()).isEqualByComparingTo("600.00");
        assertThat(balance.committedUnits()).isEqualTo(2);
        assertThat(balance.availableBudget()).isEqualByComparingTo("1400.00");
        assertThat(count("SELECT COUNT(*) FROM email_outbox WHERE template_code = 'APPLICATION_APPROVED' "
                + "AND recipient_employee_id = ? AND JSON_EXTRACT(payload_json, '$.reason') LIKE '%platform migration%'",
                employee.id())).isEqualTo(1);
    }

    @Test
    void rejectionReleasesTheReservation() {
        SubmitResult result = submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00"));

        commands.decide(manager.actor(), result.applicationId(), Decision.REJECT, "Clashes with the release date.",
                version(result.applicationId()));

        assertThat(statusOf(result.applicationId())).isEqualTo("REJECTED");
        assertThat(balance2026().usedAmount()).isEqualByComparingTo("0.00");
        assertThat(count("SELECT COUNT(*) FROM email_outbox WHERE template_code = 'APPLICATION_REJECTED'")).isEqualTo(1);
    }

    @Test
    void bothDecisionsRequireAReason() {
        SubmitResult result = submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00"));
        long version = version(result.applicationId());

        expectCode(() -> commands.decide(manager.actor(), result.applicationId(), Decision.APPROVE, "  ", version),
                ErrorCode.VALIDATION_FAILED);
        expectCode(() -> commands.decide(manager.actor(), result.applicationId(), Decision.REJECT, null, version),
                ErrorCode.VALIDATION_FAILED);
        assertThat(statusOf(result.applicationId())).isEqualTo("APPLIED");
    }

    @Test
    void aDecisionOnAnOutdatedVersionIsRefusedWithoutSideEffects() {
        SubmitResult result = submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00"));
        long seenByManager = version(result.applicationId());
        commands.update(employee.actor(), result.applicationId(), external(MON_12_OCT, MON_12_OCT, "650.00"),
                seenByManager);

        expectCode(() -> commands.decide(manager.actor(), result.applicationId(), Decision.APPROVE, "OK", seenByManager),
                ErrorCode.STALE_VERSION);

        assertThat(statusOf(result.applicationId())).isEqualTo("UPDATED");
        assertThat(count("SELECT COUNT(*) FROM training_ledger WHERE entry_type = 'COMMIT'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM email_outbox WHERE template_code = 'APPLICATION_APPROVED'")).isZero();
    }

    @Test
    void aRepeatedDecisionIsReportedAsAlreadyProcessed() {
        SubmitResult result = submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00"));
        long version = version(result.applicationId());
        commands.decide(manager.actor(), result.applicationId(), Decision.APPROVE, "OK", version);

        expectCode(() -> commands.decide(manager.actor(), result.applicationId(), Decision.APPROVE, "OK", version),
                ErrorCode.ALREADY_PROCESSED);
        assertThat(count("SELECT COUNT(*) FROM training_ledger WHERE entry_type = 'COMMIT'")).isEqualTo(1);
    }

    @Test
    void onlyTheAssignedApproverDecidesAndNobodyApprovesTheirOwnApplication() {
        Person otherManager = fixtures.manager("othermgr");
        Person director = fixtures.manager("director");
        fixtures.route(manager, director);
        fixtures.account(manager, 2026, 20, "2000.00");
        SubmitResult own = submit(manager, external(MON_12_OCT, MON_12_OCT, "100.00"));
        SubmitResult staff = submit(employee, external(MON_12_OCT, MON_12_OCT, "100.00"));

        expectCode(() -> commands.decide(manager.actor(), own.applicationId(), Decision.APPROVE, "Fine",
                version(own.applicationId())), ErrorCode.SELF_APPROVAL);
        assertThatThrownBy(() -> commands.decide(otherManager.actor(), staff.applicationId(), Decision.APPROVE, "Fine",
                version(staff.applicationId()))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> commands.decide(employee.actor(), staff.applicationId(), Decision.APPROVE, "Fine",
                version(staff.applicationId()))).isInstanceOf(NotFoundException.class);

        commands.decide(director.actor(), own.applicationId(), Decision.APPROVE, "Fine", version(own.applicationId()));
        assertThat(statusOf(own.applicationId())).isEqualTo("APPROVED");
    }

    @Test
    void cancellingAnApprovedCourseReleasesTheCommitment() {
        SubmitResult result = submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00"));
        expectCode(() -> commands.cancel(employee.actor(), result.applicationId(), version(result.applicationId()),
                "Cannot attend"), ErrorCode.INVALID_STATE);
        commands.decide(manager.actor(), result.applicationId(), Decision.APPROVE, "OK", version(result.applicationId()));

        commands.cancel(employee.actor(), result.applicationId(), version(result.applicationId()), "Project deadline moved");

        assertThat(statusOf(result.applicationId())).isEqualTo("CANCELLED");
        assertThat(balance2026().usedAmount()).isEqualByComparingTo("0.00");
        assertThat(balance2026().usedUnits()).isZero();
    }

    @Test
    void completionNeedsTheCourseToHaveEndedAndCommentsAndKeepsTheCommitment() {
        SubmitResult result = submit(employee, external(MON_12_OCT, MON_12_OCT.plusDays(1), "600.00"));
        commands.decide(manager.actor(), result.applicationId(), Decision.APPROVE, "OK", version(result.applicationId()));
        long ledgerRows = count("SELECT COUNT(*) FROM training_ledger");

        clock.setDate(MON_12_OCT.plusDays(1));
        expectCode(() -> commands.complete(employee.actor(), result.applicationId(), version(result.applicationId()),
                "Learned a lot"), ErrorCode.COURSE_NOT_ENDED);
        clock.setDate(MON_12_OCT.plusDays(2));
        expectCode(() -> commands.complete(employee.actor(), result.applicationId(), version(result.applicationId()),
                " "), ErrorCode.VALIDATION_FAILED);

        commands.complete(employee.actor(), result.applicationId(), version(result.applicationId()),
                "Learned dependency injection patterns.");

        assertThat(statusOf(result.applicationId())).isEqualTo("COMPLETED");
        assertThat(count("SELECT COUNT(*) FROM training_ledger")).isEqualTo(ledgerRows);
        assertThat(balance2026().committedAmount()).isEqualByComparingTo("600.00");
    }

    @Test
    void approvalIsRefusedWhenTheHolidayCalendarChangedAfterSubmission() {
        SubmitResult result = submit(employee, external(MON_12_OCT, MON_12_OCT.plusDays(1), "600.00"));
        jdbc.update("INSERT INTO public_holiday (holiday_date, name, source_note, updated_at) "
                + "VALUES ('2026-10-13', 'Fixture extra holiday', 'FIXTURE', NOW(6))");

        assertThatThrownBy(() -> commands.decide(manager.actor(), result.applicationId(), Decision.APPROVE, "OK",
                version(result.applicationId()))).isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.SCHEDULE_OUTDATED);
                    assertThat(e.getMessage()).contains("now counts 1 day instead of 2 days");
                });
        assertThat(statusOf(result.applicationId())).isEqualTo("APPLIED");
    }

    @Test
    void ownerPagesShowTheApplicationAndHideOtherPeoplesRecords() throws Exception {
        SubmitResult result = submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00"));
        Person other = fixtures.employee("other");
        MockHttpSession mine = staffSession(employee);

        mvc.perform(get("/employee/applications/" + result.applicationId()).session(mine))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Spring Application Development")))
                .andExpect(content().string(Matchers.containsString("Delete application")));
        mvc.perform(get("/employee/applications/" + result.applicationId() + "/edit").session(mine))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Save changes")))
                .andExpect(content().string(Matchers.containsString("value=\"600.00\"")));
        mvc.perform(get("/employee/applications").session(mine))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Showing 1–1 of 1 applications")));
        mvc.perform(get("/employee/applications/" + result.applicationId()).session(staffSession(other)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/employee/applications/" + result.applicationId() + "/delete").session(staffSession(other))
                        .with(csrf()).param("expectedVersion", String.valueOf(version(result.applicationId()))))
                .andExpect(status().isNotFound());
        assertThat(statusOf(result.applicationId())).isEqualTo("APPLIED");
    }

    @Test
    void managerReviewsFromTheGroupedWorklistAndDecidesThroughThePage() throws Exception {
        Person colleague = fixtures.employee("colleague");
        fixtures.route(colleague, manager);
        fixtures.account(colleague, 2026, 20, "2000.00");
        SubmitResult colleagues = submit(colleague, external(MON_12_OCT, MON_12_OCT, "100.00"));
        commands.decide(manager.actor(), colleagues.applicationId(), Decision.APPROVE, "OK",
                version(colleagues.applicationId()));
        SubmitResult result = submit(employee, external(MON_12_OCT, MON_12_OCT.plusDays(1), "600.00"));
        MockHttpSession session = staffSession(manager);

        mvc.perform(get("/manager/approvals").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(employee.employee().getFullName())))
                .andExpect(content().string(Matchers.containsString("1 waiting")));
        mvc.perform(get("/manager/applications/" + result.applicationId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Your other staff in this period")))
                .andExpect(content().string(Matchers.containsString(colleague.employee().getFullName())))
                .andExpect(content().string(Matchers.containsString("Pending reservations")));

        mvc.perform(post("/manager/applications/" + result.applicationId() + "/decision").session(session).with(csrf())
                        .param("decision", "APPROVE").param("reason", "")
                        .param("expectedVersion", String.valueOf(version(result.applicationId()))))
                .andExpect(redirectedUrl("/manager/applications/" + result.applicationId()))
                .andExpect(flash().attribute("flashError", Matchers.containsString("reason")));
        mvc.perform(post("/manager/applications/" + result.applicationId() + "/decision").session(session).with(csrf())
                        .param("decision", "APPROVE").param("reason", "Supports the migration project.")
                        .param("expectedVersion", String.valueOf(version(result.applicationId()))))
                .andExpect(redirectedUrl("/manager/approvals"));
        assertThat(statusOf(result.applicationId())).isEqualTo("APPROVED");

        mvc.perform(get("/manager/team/history").session(session).param("employeeId", employee.id().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Spring Application Development")));
        Person stranger = fixtures.employee("stranger");
        mvc.perform(get("/manager/team/history").session(session).param("employeeId", stranger.id().toString()))
                .andExpect(status().isNotFound());
    }
}
