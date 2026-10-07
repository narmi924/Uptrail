package com.uptrail.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import com.uptrail.application.AbstractApplicationIT;
import com.uptrail.service.CourseApplicationService.Decision;
import com.uptrail.service.CourseApplicationService.SubmitResult;
import com.uptrail.model.CategoryCode;
import com.uptrail.model.Session;
import com.uptrail.service.ReportQueryService;
import com.uptrail.service.ReportQueryService.BudgetFilter;
import com.uptrail.service.ReportQueryService.BudgetReport;
import com.uptrail.service.ReportQueryService.TrainingFilter;
import com.uptrail.service.ReportQueryService.TrainingReport;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.support.Fixtures.Person;

/**
 * Training calendar, manager reports, CSV export and pagination: what is shown, the scope of each view and
 * that the downloads equal the pages.
 */
class CalendarAndReportIT extends AbstractApplicationIT {

    private static final LocalDate FRI_30_OCT = LocalDate.of(2026, 10, 30);
    private static final LocalDate MON_2_NOV = LocalDate.of(2026, 11, 2);

    @Autowired
    private ReportQueryService reports;

    private Person otherManager;
    private Person outsider;

    @BeforeEach
    void createSecondTeam() {
        otherManager = fixtures.manager("mgr2");
        outsider = fixtures.employee("outsider");
        fixtures.route(outsider, otherManager);
        fixtures.account(outsider, 2026, 20, "2000.00");
    }

    private Long approved(Person person, Person approver, SubmitResult result) {
        commands.decide(approver.actor(), result.applicationId(), Decision.APPROVE, "OK", version(result.applicationId()));
        return result.applicationId();
    }

    // ---- Training calendar -------------------------------------------------------------------------------

    @Test
    void calendarShowsApprovedAndCompletedCoursesOnlyWithMinimalFields() throws Exception {
        Long completed = approved(employee, manager, submit(employee, external(MON_12_OCT, MON_12_OCT.plusDays(1), "600.00")));
        Long scheduled = approved(employee, manager, submit(employee, internal(MON_12_OCT.plusDays(8), Session.AM,
                MON_12_OCT.plusDays(8), Session.AM)));
        submit(employee, external(MON_12_OCT.plusDays(15), MON_12_OCT.plusDays(15), "100.00"));
        Long cancelled = approved(employee, manager, submit(employee, external(MON_12_OCT.plusDays(10),
                MON_12_OCT.plusDays(10), "100.00")));
        commands.cancel(employee.actor(), cancelled, version(cancelled), "Plans changed");
        clock.setDate(MON_12_OCT.plusDays(3));
        commands.complete(employee.actor(), completed, version(completed), "Useful course.");

        mvc.perform(get("/api/v1/calendar").param("month", "2026-10").session(staffSession(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.previousMonth").value("2026-09"))
                .andExpect(jsonPath("$.nextMonth").value("2026-11"))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].employeeName").value(employee.employee().getName()))
                .andExpect(jsonPath("$.entries[0].courseTitle").value("Spring Application Development"))
                .andExpect(jsonPath("$.entries[0].startDate").value("2026-10-12"))
                .andExpect(jsonPath("$.entries[1].category").value("INTERNAL"))
                .andExpect(jsonPath("$.entries[1].startSession").value("AM"))
                .andExpect(jsonPath("$.entries[1].endSession").value("AM"))
                .andExpect(jsonPath("$.entries[0].fee").doesNotExist())
                .andExpect(jsonPath("$.entries[0].status").doesNotExist())
                .andExpect(jsonPath("$.entries[0].referenceNo").doesNotExist())
                .andExpect(jsonPath("$.entries[0].reason").doesNotExist())
                .andExpect(jsonPath("$.holidays[0].date").value(FIXTURE_HOLIDAY.toString()));
        assertThat(scheduled).isNotNull();
    }

    @Test
    void calendarIncludesCoursesOverlappingTheMonthAndFiltersByCategory() throws Exception {
        approved(employee, manager, submit(employee, external(FRI_30_OCT, MON_2_NOV, "300.00")));
        approved(employee, manager, submit(employee, internal(MON_2_NOV.plusDays(1), Session.AM,
                MON_2_NOV.plusDays(1), Session.PM)));
        MockHttpSession session = staffSession(employee);

        mvc.perform(get("/api/v1/calendar").param("month", "2026-11").session(session))
                .andExpect(jsonPath("$.entries.length()").value(2));
        mvc.perform(get("/api/v1/calendar").param("month", "2026-11").param("category", "INTERNAL").session(session))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].category").value("INTERNAL"));
        mvc.perform(get("/api/v1/calendar").param("month", "2026-10").session(session))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].endDate").value("2026-11-02"));
    }

    @Test
    void calendarNeedsASignedInUserAndAValidMonth() throws Exception {
        mvc.perform(get("/api/v1/calendar").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        MockHttpSession session = staffSession(employee);
        mvc.perform(get("/api/v1/calendar").param("month", "2026-13").session(session))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/calendar").param("category", "WORKSHOP").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calendarPageRendersTheMonthWithoutScriptsAndBoundsTheMonthPicker() throws Exception {
        approved(employee, manager, submit(employee, external(MON_12_OCT, MON_12_OCT, "100.00")));
        MockHttpSession session = staffSession(employee);

        mvc.perform(get("/calendar").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("October 2026")))
                .andExpect(content().string(Matchers.containsString("Spring Application Development")));
        String farAway = mvc.perform(get("/calendar").param("month", "1990-01").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(farAway).contains("January 1990");
        assertThat(farAway.split("<option ", -1).length - 1).isLessThan(40);
    }

    // ---- Training participation report -----------------------------------------------------------------

    @Test
    void trainingReportCountsOnlyDaysInsideThePeriodForDirectReports() {
        approved(employee, manager, submit(employee, external(FRI_30_OCT, MON_2_NOV, "300.00")));
        approved(employee, manager, submit(employee, internal(MON_12_OCT, Session.PM, MON_12_OCT, Session.PM)));
        submit(employee, external(MON_12_OCT.plusDays(2 + 7), MON_12_OCT.plusDays(2 + 7), "50.00"));
        approved(outsider, otherManager, submit(outsider, external(MON_12_OCT, MON_12_OCT, "999.00")));

        TrainingReport october = reports.training(manager.actor(),
                new TrainingFilter(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, null));
        TrainingReport november = reports.training(manager.actor(),
                new TrainingFilter(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null, null));
        TrainingReport internalOnly = reports.training(manager.actor(),
                new TrainingFilter(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), CategoryCode.INTERNAL, null));

        assertThat(october.rows()).hasSize(2);
        assertThat(october.rows()).extracting(ReportQueryService.TrainingRow::employeeName)
                .containsOnly(employee.employee().getName());
        assertThat(october.totalUnits()).isEqualTo(3);
        assertThat(october.unitsByCategory()).containsEntry(CategoryCode.EXTERNAL, 2).containsEntry(CategoryCode.INTERNAL, 1);
        assertThat(october.feesOfCoursesStartingInPeriod()).isEqualByComparingTo("300.00");
        assertThat(october.people()).isEqualTo(1);
        assertThat(november.totalUnits()).isEqualTo(2);
        assertThat(november.feesOfCoursesStartingInPeriod()).isEqualByComparingTo("0.00");
        assertThat(internalOnly.rows()).hasSize(1);
        assertThat(internalOnly.totalUnits()).isEqualTo(1);
    }

    @Test
    void reportsRefuseStaffOutsideTheManagersTeam() throws Exception {
        assertThatThrownBy(() -> reports.training(manager.actor(),
                new TrainingFilter(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, outsider.id())))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> reports.budget(manager.actor(), new BudgetFilter(2026, outsider.id())))
                .isInstanceOf(NotFoundException.class);
        MockHttpSession session = staffSession(manager);
        String id = outsider.id().toString();

        mvc.perform(get("/manager/reports").param("employeeId", id).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/manager/reports").param("tab", "budget").param("employeeId", id).session(session))
                .andExpect(status().isNotFound());
        mvc.perform(get("/manager/reports/training.csv").param("employeeId", id).session(session))
                .andExpect(status().isNotFound());
        mvc.perform(get("/manager/reports/budget.csv").param("employeeId", id).session(session))
                .andExpect(status().isNotFound());
        mvc.perform(get("/manager/reports").session(staffSession(employee))).andExpect(status().isForbidden());
        mvc.perform(get("/manager/reports/training.csv").session(staffSession(employee)))
                .andExpect(status().isForbidden());
    }

    @Test
    void trainingCsvHasExactlyThePageRowsAndIsADownload() throws Exception {
        SubmitResult first = submit(employee, external(FRI_30_OCT, MON_2_NOV, "300.00"));
        approved(employee, manager, first);
        SubmitResult second = submit(employee, internal(MON_12_OCT, Session.PM, MON_12_OCT, Session.PM));
        approved(employee, manager, second);
        MockHttpSession session = staffSession(manager);

        String page = mvc.perform(get("/manager/reports").param("from", "2026-10-01").param("to", "2026-10-31")
                .session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        byte[] bytes = mvc.perform(get("/manager/reports/training.csv").param("from", "2026-10-01")
                        .param("to", "2026-10-31").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        Matchers.containsString("attachment; filename=\"training-participation-2026-10-01-to-2026-10-31.csv\"")))
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(bytes, StandardCharsets.UTF_8);
        List<String> lines = List.of(csv.substring(1).split("\r\n"));

        assertThat(page).contains(first.referenceNo(), second.referenceNo());
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).startsWith("Reference,Staff member,Department,Course");
        assertThat(lines).anySatisfy(line -> assertThat(line).startsWith(first.referenceNo()).endsWith(",1,300.00"));
        assertThat(lines).anySatisfy(line -> assertThat(line).startsWith(second.referenceNo()).endsWith(",0.5,0.00"));
    }

    @Test
    void trainingReportPaginatesAndKeepsTheFiltersInPageLinks() throws Exception {
        for (int week = 0; week < 3; week++) {
            approved(employee, manager, submit(employee, internal(MON_12_OCT.plusWeeks(week), Session.AM,
                    MON_12_OCT.plusWeeks(week), Session.AM)));
        }
        MockHttpSession session = staffSession(manager);

        String page = mvc.perform(get("/manager/reports").param("from", "2026-10-01").param("to", "2026-10-31")
                        .param("category", "INTERNAL").param("size", "2").param("page", "2").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Showing 3").contains("of 3");
        assertThat(page).contains("category=INTERNAL");
        assertThat(page).contains("26 Oct 2026").doesNotContain("12 Oct 2026 (morning)");
    }

    @Test
    void anInvalidPeriodIsExplainedOnThePageAndRejectedForTheDownload() throws Exception {
        MockHttpSession session = staffSession(manager);

        mvc.perform(get("/manager/reports").param("from", "2026-10-31").param("to", "2026-10-01").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Choose a period whose end is on or after its start.")));
        mvc.perform(get("/manager/reports/training.csv").param("from", "2026-10-31").param("to", "2026-10-01")
                .session(session)).andExpect(status().is(422));
    }

    // ---- Budget and claims report ------------------------------------------------------------------------

    @Test
    void budgetReportUsesLedgerBalancesAndClaimTotalsOfTheTeamOnly() throws Exception {
        Long done = approved(employee, manager, submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00")));
        submit(employee, external(MON_12_OCT.plusDays(7), MON_12_OCT.plusDays(7), "100.00"));
        approved(outsider, otherManager, submit(outsider, external(MON_12_OCT, MON_12_OCT, "999.00")));
        Person newcomer = fixtures.employee("newcomer");
        fixtures.route(newcomer, manager);
        clock.setDate(MON_12_OCT.plusDays(1));
        commands.complete(employee.actor(), done, version(done), "Useful course.");
        // A direct row keeps this test independent of the claim service.
        jdbc.update("""
                INSERT INTO course_fee_application (application_id, revision, amount, paid_by_employee, approver_id, status,
                                          submitted_at, version)
                VALUES (?, 1, 550.00, TRUE, ?, 'SUBMITTED', UTC_TIMESTAMP(6), 0)
                """, done, manager.id());

        BudgetReport report = reports.budget(manager.actor(), new BudgetFilter(2026, null));

        assertThat(report.rows()).hasSize(2);
        ReportQueryService.BudgetRow row = report.rows().stream()
                .filter(r -> r.employeeName().equals(employee.employee().getName())).findFirst().orElseThrow();
        assertThat(row.balance().budget()).isEqualByComparingTo("2000.00");
        assertThat(row.balance().committedAmount()).isEqualByComparingTo("600.00");
        assertThat(row.balance().reservedAmount()).isEqualByComparingTo("100.00");
        assertThat(row.balance().availableBudget()).isEqualByComparingTo("1300.00");
        assertThat(row.completedUnits()).isEqualTo(2);
        assertThat(row.claimsSubmitted()).isEqualByComparingTo("550.00");
        ReportQueryService.BudgetRow unconfigured = report.rows().stream()
                .filter(r -> r.employeeName().equals(newcomer.employee().getName())).findFirst().orElseThrow();
        assertThat(unconfigured.balance().configured()).isFalse();
        assertThat(report.totals().budget()).isEqualByComparingTo("2000.00");
        assertThat(report.totals().committedAmount()).isEqualByComparingTo("600.00");
        assertThat(report.claimsSubmitted()).isEqualByComparingTo("550.00");

        MockHttpSession session = staffSession(manager);
        mvc.perform(get("/manager/reports").param("tab", "budget").param("year", "2026").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("1,300.00")))
                .andExpect(content().string(Matchers.containsString("Not configured")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("999.00"))));
        String csv = new String(mvc.perform(get("/manager/reports/budget.csv").param("year", "2026").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<String> lines = List.of(csv.substring(1).split("\r\n"));
        assertThat(lines).hasSize(3);
        assertThat(lines).anySatisfy(line -> assertThat(line)
                .contains(",2026,10,1,1,8,1,2000.00,100.00,600.00,1300.00,550.00,0.00,0.00"));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("Not configured"));
    }

    @Test
    void budgetYearOutsideTheOfferedRangeFallsBackToTheCurrentYear() throws Exception {
        mvc.perform(get("/manager/reports").param("tab", "budget").param("year", "1999").session(staffSession(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Budget use and fee claims 2026")));
    }
}
