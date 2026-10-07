package com.uptrail.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import com.uptrail.service.CatalogueAdminService;
import com.uptrail.service.EntitlementAdminService;
import com.uptrail.service.HolidayAdminService;
import com.uptrail.service.RoutingAdminService;
import com.uptrail.service.StaffAdminService;
import com.uptrail.model.ApplicationDetails;
import com.uptrail.service.CourseApplicationService;
import com.uptrail.service.CourseApplicationService.Decision;
import com.uptrail.model.CategoryCode;
import com.uptrail.model.Session;
import com.uptrail.model.Role;
import com.uptrail.model.Designation;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.support.AbstractMySqlIT;
import com.uptrail.support.Fixtures;
import com.uptrail.support.Fixtures.Person;

/**
 * Administration: staff and roles, routing and explicit reassignment, entitlement limits, catalogue
 * snapshots, and the holiday calendar confirmation rules.
 */
class AdministrationIT extends AbstractMySqlIT {

    private static final LocalDate MON_12_OCT = LocalDate.of(2026, 10, 12);

    @Autowired
    private StaffAdminService staff;
    @Autowired
    private RoutingAdminService routing;
    @Autowired
    private EntitlementAdminService entitlements;
    @Autowired
    private CatalogueAdminService catalogue;
    @Autowired
    private HolidayAdminService holidays;
    @Autowired
    private CourseApplicationService applications;

    private Person admin;
    private Person manager;
    private Person employee;

    @BeforeEach
    void organisation() {
        admin = fixtures.admin("admin");
        manager = fixtures.manager("mgr");
        employee = fixtures.employee("emp");
        fixtures.route(employee, manager);
        fixtures.confirmedYear(2026, admin, LocalDate.of(2026, 10, 14));
        fixtures.confirmedYear(2027, admin, LocalDate.of(2027, 1, 1));
        fixtures.account(employee, 2026, 20, "2000.00");
    }

    private static void expectCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    private Long submit(Person person, String fee) {
        return applications.submit(person.actor(), new ApplicationDetails(CategoryCode.EXTERNAL, null, "Course",
                "Provider", MON_12_OCT, MON_12_OCT, Session.AM, Session.PM, new BigDecimal(fee), "Reason", null),
                UUID.randomUUID().toString()).applicationId();
    }

    private long version(Long applicationId) {
        return jdbc.queryForObject("SELECT version FROM course_application WHERE id = ?", Long.class, applicationId);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    // ------------------------------------------------------------------ staff

    @Test
    void creatingStaffAddsAccountRolesRoutingAndDefaultEntitlement() {
        Long id = staff.create(admin.actor(), new StaffAdminService.NewStaff("S9001", "New Manager",
                "new.manager@example.com", "Research", Designation.MANAGEMENT, "NewMgr", "long-enough-pw",
                EnumSet.of(Role.MANAGER), manager.id(), true));

        assertThat(count("SELECT COUNT(*) FROM user_roles WHERE user_id = ?", id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT user_name FROM users WHERE id = ?", String.class, id))
                .isEqualTo("newmgr");
        assertThat(count("SELECT COUNT(*) FROM approval_hierarchy WHERE employee_id = ? AND manager_id = ?", id,
                manager.id())).isEqualTo(1);
        assertThat(count("SELECT entitled_units FROM training_entitlement WHERE employee_id = ? AND calendar_year = 2026",
                id)).isEqualTo(20);
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE aggregate_type = 'STAFF' AND event_type = "
                + "'STAFF_CREATED'")).isEqualTo(1);
    }

    @Test
    void duplicateUsernameAndStaffNumberAreReportedPerField() {
        assertThatThrownBy(() -> staff.create(admin.actor(), new StaffAdminService.NewStaff(employee.employee()
                .getStaffId(), "Copy", "copy@example.com", "Dept", Designation.PROFESSIONAL, "EMP", "long-enough-pw",
                EnumSet.of(Role.STAFF), null, false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.fieldErrors()).containsKeys("staffId", "username"));
    }

    @Test
    void deactivationIsGuardedAndEndsSignIn() throws Exception {
        expectCode(() -> staff.deactivate(admin.actor(), admin.id()), ErrorCode.RULE_VIOLATION);
        expectCode(() -> staff.deactivate(admin.actor(), manager.id()), ErrorCode.RULE_VIOLATION);

        MockHttpSession session = (MockHttpSession) mvc.perform(formLogin("/employee/login").user("emp")
                .password(Fixtures.PASSWORD)).andReturn().getRequest().getSession(false);
        staff.deactivate(admin.actor(), employee.id());

        assertThat(count("SELECT COUNT(*) FROM users WHERE id = ? AND active = 0", employee.id())).isEqualTo(1);
        mvc.perform(get("/staff/home").session(session))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/employee/login?expired"));
        mvc.perform(formLogin("/employee/login").user("emp").password(Fixtures.PASSWORD)).andExpect(unauthenticated());
    }

    @Test
    void theLastAdministratorKeepsTheRole() {
        Person other = fixtures.employee("other");
        expectCode(() -> staff.changeRoles(admin.actor(), admin.id(), EnumSet.of(Role.STAFF)),
                ErrorCode.RULE_VIOLATION);

        staff.changeRoles(admin.actor(), other.id(), EnumSet.of(Role.STAFF, Role.ADMIN));
        staff.changeRoles(admin.actor(), admin.id(), EnumSet.of(Role.STAFF));

        assertThat(count("SELECT COUNT(*) FROM user_roles WHERE user_id = ? AND role_code = 'ADMIN'", admin.id())).isZero();
    }

    @Test
    void aManagerWithPendingItemsKeepsTheRoleUntilTheTeamIsMoved() {
        Person newManager = fixtures.manager("newmgr");
        Long pending = submit(employee, "100.00");

        expectCode(() -> staff.changeRoles(admin.actor(), manager.id(), EnumSet.of(Role.STAFF)),
                ErrorCode.RULE_VIOLATION);

        routing.moveTeam(admin.actor(), manager.id(), newManager.id());
        staff.changeRoles(admin.actor(), manager.id(), EnumSet.of(Role.STAFF));

        assertThat(count("SELECT approver_id FROM course_application WHERE id = ?", pending)).isEqualTo(newManager.id());
    }

    @Test
    void onlyRecordsWithoutHistoryCanBeDeleted() {
        Person mistake = fixtures.employee("mistake");
        submit(employee, "100.00");

        expectCode(() -> staff.delete(admin.actor(), employee.id()), ErrorCode.RULE_VIOLATION);
        staff.delete(admin.actor(), mistake.id());

        assertThat(count("SELECT COUNT(*) FROM users WHERE id = ?", mistake.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM users WHERE id = ?", employee.id())).isEqualTo(1);
    }

    // ------------------------------------------------------------------ routing

    @Test
    void routingRejectsSelfNonManagersAndLoops() {
        Person director = fixtures.manager("director");
        fixtures.route(manager, director);

        expectCode(() -> routing.assign(admin.actor(), employee.id(), employee.id(), false), ErrorCode.VALIDATION_FAILED);
        Person peer = fixtures.employee("peer");
        expectCode(() -> routing.assign(admin.actor(), employee.id(), peer.id(), false), ErrorCode.VALIDATION_FAILED);
        expectCode(() -> routing.assign(admin.actor(), director.id(), manager.id(), false), ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void reassignmentMovesWaitingApplicationsExplicitlyAndIsAudited() {
        Person newManager = fixtures.manager("newmgr");
        Long pending = submit(employee, "100.00");

        routing.assign(admin.actor(), employee.id(), newManager.id(), false);
        assertThat(count("SELECT approver_id FROM course_application WHERE id = ?", pending)).isEqualTo(manager.id());

        routing.assign(admin.actor(), employee.id(), newManager.id(), true);
        assertThat(count("SELECT approver_id FROM course_application WHERE id = ?", pending)).isEqualTo(newManager.id());
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE aggregate_type = 'APPLICATION' AND event_type = "
                + "'REASSIGNED'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM email_outbox WHERE template_code = 'APPLICATION_REASSIGNED' AND "
                + "recipient_employee_id = ?", newManager.id())).isEqualTo(1);

        assertThatThrownBy(() -> applications.decide(manager.actor(), pending, Decision.APPROVE, "OK", version(pending)))
                .isInstanceOfAny(NotFoundException.class, BusinessException.class);
        applications.decide(newManager.actor(), pending, Decision.APPROVE, "OK", version(pending));
    }

    // ------------------------------------------------------------------ entitlements

    @Test
    void entitlementCannotDropBelowWhatIsReservedOrApproved() {
        submit(employee, "600.00");

        expectCode(() -> entitlements.save(admin.actor(), employee.id(), 2026, new BigDecimal("0.5"),
                new BigDecimal("2000.00"), "Policy change"), ErrorCode.RULE_VIOLATION);
        expectCode(() -> entitlements.save(admin.actor(), employee.id(), 2026, new BigDecimal("10"),
                new BigDecimal("599.99"), "Policy change"), ErrorCode.RULE_VIOLATION);
        expectCode(() -> entitlements.save(admin.actor(), employee.id(), 2026, new BigDecimal("7.25"),
                new BigDecimal("2000.00"), "Policy change"), ErrorCode.VALIDATION_FAILED);
        expectCode(() -> entitlements.save(admin.actor(), employee.id(), 2026, new BigDecimal("7"),
                new BigDecimal("2000.00"), " "), ErrorCode.VALIDATION_FAILED);

        entitlements.save(admin.actor(), employee.id(), 2026, new BigDecimal("7.5"), new BigDecimal("600.00"),
                "Reduced for the second half");
        assertThat(count("SELECT entitled_units FROM training_entitlement WHERE employee_id = ? AND calendar_year = 2026",
                employee.id())).isEqualTo(15);
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE event_type = 'ENTITLEMENT_CHANGED' AND reason = "
                + "'Reduced for the second half'")).isEqualTo(1);
    }

    @Test
    void missingAccountsCanBeOpenedWithDefaultsForTheNextYearOnly() {
        int opened = entitlements.openMissing(admin.actor(), 2027);

        // Applicants are the manager and the employee; the administrator has no employee role.
        assertThat(opened).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM training_entitlement WHERE calendar_year = 2027")).isEqualTo(2);
        assertThat(entitlements.openMissing(admin.actor(), 2027)).isZero();
        expectCode(() -> entitlements.openMissing(admin.actor(), 2029), ErrorCode.YEAR_NOT_OPEN);
    }

    // ------------------------------------------------------------------ catalogue

    @Test
    void catalogueRulesAndSnapshotsOfSubmittedApplications() {
        expectCode(() -> catalogue.addCourse(admin.actor(), new CatalogueAdminService.CourseInput(
                CategoryCode.INTERNAL, null, "Paid internal", new BigDecimal("10.00"), null)), ErrorCode.VALIDATION_FAILED);
        Long courseId = catalogue.addCourse(admin.actor(), new CatalogueAdminService.CourseInput(
                CategoryCode.EXTERNAL, null, "Original title", new BigDecimal("300.00"), null));
        Long applicationId = applications.submit(employee.actor(), new ApplicationDetails(CategoryCode.EXTERNAL,
                courseId, "Original title", "Provider", MON_12_OCT, MON_12_OCT, Session.AM, Session.PM,
                new BigDecimal("300.00"), "Reason", null), UUID.randomUUID().toString()).applicationId();

        catalogue.updateCourse(admin.actor(), courseId, new CatalogueAdminService.CourseInput(CategoryCode.EXTERNAL,
                null, "Renamed title", new BigDecimal("999.00"), null), false);

        assertThat(jdbc.queryForObject("SELECT course_title FROM course_application WHERE id = ?", String.class,
                applicationId)).isEqualTo("Original title");
        assertThat(jdbc.queryForObject("SELECT course_fee FROM course_application WHERE id = ?", BigDecimal.class,
                applicationId)).isEqualByComparingTo("300.00");
    }

    @Test
    void catalogueCourseListIsPaginated() throws Exception {
        for (int i = 1; i <= 12; i++) {
            catalogue.addCourse(admin.actor(), new CatalogueAdminService.CourseInput(CategoryCode.EXTERNAL, null,
                    String.format("Course %02d", i), new BigDecimal("100.00"), null));
        }
        MockHttpSession adminSession = (MockHttpSession) mvc.perform(formLogin("/admin/login").user("admin")
                .password(Fixtures.PASSWORD)).andReturn().getRequest().getSession(false);

        String secondPage = mvc.perform(get("/admin/catalogue").param("tab", "courses").param("page", "2")
                .session(adminSession)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(secondPage).contains("Showing 11–12 of 12 courses").contains("tab=courses");
        assertThat(secondPage).doesNotContain(">Course 01<");
    }

    // ------------------------------------------------------------------ holidays

    @Test
    void changingHolidayDatesOfAConfirmedYearReturnsItToDraftAndBlocksSubmissions() {
        holidays.addHoliday(admin.actor(), LocalDate.of(2026, 11, 2), "Fixture day", "FIXTURE");

        assertThat(jdbc.queryForObject("SELECT status FROM training_calendar_year WHERE calendar_year = 2026",
                String.class)).isEqualTo("DRAFT");
        expectCode(() -> submit(employee, "100.00"), ErrorCode.HOLIDAY_CALENDAR_UNCONFIRMED);

        holidays.describeHoliday(admin.actor(), LocalDate.of(2026, 11, 2), "Renamed", "FIXTURE");
        holidays.confirmYear(admin.actor(), 2026, "FIXTURE rechecked");
        assertThat(submit(employee, "100.00")).isNotNull();
        holidays.describeHoliday(admin.actor(), LocalDate.of(2026, 11, 2), "Renamed again", "FIXTURE");
        assertThat(jdbc.queryForObject("SELECT status FROM training_calendar_year WHERE calendar_year = 2026",
                String.class)).isEqualTo("CONFIRMED");
    }

    @Test
    void impactListsWaitingAndApprovedApplicationsOnTheDate() {
        Long pending = submit(employee, "100.00");

        var impact = holidays.impact(MON_12_OCT);

        assertThat(impact.yearConfirmed()).isTrue();
        assertThat(impact.applications()).extracting(HolidayAdminService.ImpactRow::applicationId).containsExactly(pending);
        assertThat(holidays.impact(MON_12_OCT.plusDays(1)).applications()).isEmpty();
    }

    @Test
    void calendarsNeedHolidaysAndASourceBeforeConfirmationAndCanImportTheBundledList() {
        clock.setDate(LocalDate.of(2027, 3, 1));
        holidays.createYear(admin.actor(), 2028, "FIXTURE list");
        expectCode(() -> holidays.confirmYear(admin.actor(), 2028, "FIXTURE"), ErrorCode.RULE_VIOLATION);
        expectCode(() -> holidays.confirmYear(admin.actor(), 2028, " "), ErrorCode.VALIDATION_FAILED);
        expectCode(() -> holidays.addHoliday(admin.actor(), LocalDate.of(2029, 1, 1), "x", "y"), ErrorCode.RULE_VIOLATION);

        jdbc.update("DELETE FROM excluded_days WHERE YEAR(holiday_date) = 2027");
        int imported = holidays.importBundled(admin.actor(), 2027);
        assertThat(imported).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT status FROM training_calendar_year WHERE calendar_year = 2027",
                String.class)).isEqualTo("DRAFT");
    }

    // ------------------------------------------------------------------ pages

    @Test
    void administrationPagesRenderForAdministratorsOnly() throws Exception {
        MockHttpSession adminSession = (MockHttpSession) mvc.perform(formLogin("/admin/login").user("admin")
                .password(Fixtures.PASSWORD)).andReturn().getRequest().getSession(false);
        for (String page : new String[] {"/admin/staff", "/admin/staff/new", "/admin/staff/" + employee.id(),
                "/admin/routing", "/admin/entitlements", "/admin/entitlements?year=2027", "/admin/catalogue",
                "/admin/catalogue?tab=providers", "/admin/catalogue?tab=categories", "/admin/holidays",
                "/admin/holidays?year=2028"}) {
            mvc.perform(get(page).session(adminSession)).andExpect(status().isOk());
        }
        MockHttpSession staffSession = (MockHttpSession) mvc.perform(formLogin("/employee/login").user("emp")
                .password(Fixtures.PASSWORD)).andReturn().getRequest().getSession(false);
        mvc.perform(get("/admin/staff").session(staffSession)).andExpect(status().isForbidden());
    }

    @Test
    void addingAHolidayThroughThePageAsksForConfirmationFirst() throws Exception {
        submit(employee, "100.00");
        MockHttpSession adminSession = (MockHttpSession) mvc.perform(formLogin("/admin/login").user("admin")
                .password(Fixtures.PASSWORD)).andReturn().getRequest().getSession(false);

        mvc.perform(post("/admin/holidays/add").session(adminSession).with(csrf()).param("date", "2026-10-12")
                        .param("name", "Fixture").param("sourceNote", "FIXTURE"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Applications that include this date")));
        assertThat(count("SELECT COUNT(*) FROM excluded_days WHERE holiday_date = '2026-10-12'")).isZero();

        mvc.perform(post("/admin/holidays/add").session(adminSession).with(csrf()).param("date", "2026-10-12")
                        .param("name", "Fixture").param("sourceNote", "FIXTURE").param("confirmed", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(count("SELECT COUNT(*) FROM excluded_days WHERE holiday_date = '2026-10-12'")).isEqualTo(1);
    }
}
