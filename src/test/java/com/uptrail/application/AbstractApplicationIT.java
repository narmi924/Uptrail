package com.uptrail.application;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import com.uptrail.application.domain.ApplicationDetails;
import com.uptrail.application.service.ApplicationCommandService;
import com.uptrail.application.service.ApplicationCommandService.SubmitResult;
import com.uptrail.catalogue.domain.CategoryCode;
import com.uptrail.entitlement.domain.Session;
import com.uptrail.entitlement.service.EntitlementService;
import com.uptrail.support.AbstractMySqlIT;
import com.uptrail.support.Fixtures;
import com.uptrail.support.Fixtures.Person;

/**
 * Common organisation for application tests: an administrator, a manager and an employee routed to that
 * manager, confirmed synthetic calendars for 2026 (holiday on Wed 14 Oct) and 2027 (holiday on 1 Jan),
 * and accounts of 20 half-day units and SGD 2,000 per year. Business date: Monday 5 October 2026.
 */
abstract class AbstractApplicationIT extends AbstractMySqlIT {

    static final LocalDate MON_12_OCT = LocalDate.of(2026, 10, 12);
    static final LocalDate FIXTURE_HOLIDAY = LocalDate.of(2026, 10, 14);

    @Autowired
    protected ApplicationCommandService commands;

    @Autowired
    protected EntitlementService entitlements;

    protected Person admin;
    protected Person manager;
    protected Person employee;

    @BeforeEach
    void createOrganisation() {
        admin = fixtures.admin("admin");
        manager = fixtures.manager("mgr");
        employee = fixtures.employee("emp");
        fixtures.route(employee, manager);
        fixtures.confirmedYear(2026, admin, FIXTURE_HOLIDAY);
        fixtures.confirmedYear(2027, admin, LocalDate.of(2027, 1, 1));
        fixtures.account(employee, 2026, 20, "2000.00");
        fixtures.account(employee, 2027, 20, "2000.00");
    }

    static ApplicationDetails external(LocalDate start, LocalDate end, String fee) {
        return new ApplicationDetails(CategoryCode.EXTERNAL, null, "Spring Application Development",
                "Sample Provider", start, end, Session.AM, Session.PM, new BigDecimal(fee),
                "Use Spring in our internal applications.", null);
    }

    static ApplicationDetails internal(LocalDate start, Session startSession, LocalDate end, Session endSession) {
        return new ApplicationDetails(CategoryCode.INTERNAL, null, "Secure Coding Fundamentals", null, start, end,
                startSession, endSession, BigDecimal.ZERO, "Improve code quality in my team.", null);
    }

    SubmitResult submit(Person person, ApplicationDetails details) {
        return commands.submit(person.actor(), details, UUID.randomUUID().toString());
    }

    long version(Long applicationId) {
        return jdbc.queryForObject("SELECT version FROM course_application WHERE id = ?", Long.class, applicationId);
    }

    String statusOf(Long applicationId) {
        return jdbc.queryForObject("SELECT status FROM course_application WHERE id = ?", String.class, applicationId);
    }

    long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    MockHttpSession staffSession(Person person) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").user(person.username()).password(Fixtures.PASSWORD))
                .andReturn().getRequest().getSession(false);
    }
}
