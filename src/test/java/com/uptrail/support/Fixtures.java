package com.uptrail.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import com.uptrail.catalogue.domain.PublicHoliday;
import com.uptrail.catalogue.domain.TrainingCalendarYear;
import com.uptrail.catalogue.repository.PublicHolidayRepository;
import com.uptrail.catalogue.repository.TrainingCalendarYearRepository;
import com.uptrail.entitlement.domain.TrainingAccount;
import com.uptrail.entitlement.repository.TrainingAccountRepository;
import com.uptrail.identity.domain.Actor;
import com.uptrail.identity.domain.Role;
import com.uptrail.identity.domain.UserAccount;
import com.uptrail.identity.repository.UserAccountRepository;
import com.uptrail.organisation.domain.ApprovalAssignment;
import com.uptrail.organisation.domain.Designation;
import com.uptrail.organisation.domain.Employee;
import com.uptrail.organisation.repository.ApprovalAssignmentRepository;
import com.uptrail.organisation.repository.EmployeeRepository;
import com.uptrail.shared.time.BusinessClock;

/**
 * Builds test data directly in the database. Holidays created here are synthetic and marked FIXTURE.
 */
@TestComponent
public class Fixtures {

    public static final String PASSWORD = "Test-password-1";
    public static final String FIXTURE_NOTE = "FIXTURE: synthetic test calendar, not official data";

    private static final AtomicInteger SEQUENCE = new AtomicInteger(1);

    private final EmployeeRepository employees;
    private final UserAccountRepository accounts;
    private final ApprovalAssignmentRepository assignments;
    private final TrainingAccountRepository trainingAccounts;
    private final TrainingCalendarYearRepository calendarYears;
    private final PublicHolidayRepository holidays;
    private final PasswordEncoder passwordEncoder;
    private final BusinessClock clock;
    private final TransactionTemplate tx;
    private String passwordHash;

    public Fixtures(EmployeeRepository employees, UserAccountRepository accounts,
            ApprovalAssignmentRepository assignments, TrainingAccountRepository trainingAccounts,
            TrainingCalendarYearRepository calendarYears, PublicHolidayRepository holidays,
            PasswordEncoder passwordEncoder, BusinessClock clock, TransactionTemplate tx) {
        this.employees = employees;
        this.accounts = accounts;
        this.assignments = assignments;
        this.trainingAccounts = trainingAccounts;
        this.calendarYears = calendarYears;
        this.holidays = holidays;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.tx = tx;
    }

    public record Person(Employee employee, String username, Actor actor) {
        public Long id() {
            return employee.getId();
        }
    }

    public Person person(String username, Designation designation, Role... roles) {
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
        return tx.execute(status -> {
            int n = SEQUENCE.getAndIncrement();
            Employee employee = employees.save(Employee.create("T" + n, capitalise(username) + " Tester",
                    username + "@example.com", "Testing", designation, clock.now()));
            UserAccount account = accounts.save(UserAccount.create(employee.getId(), username, passwordHash,
                    EnumSet.copyOf(List.of(roles)), clock.now()));
            return new Person(employee, account.getUsername(),
                    new Actor(employee.getId(), employee.getFullName(), account.getRoles()));
        });
    }

    public Person employee(String username) {
        return person(username, Designation.PROFESSIONAL, Role.EMPLOYEE);
    }

    public Person manager(String username) {
        return person(username, Designation.MANAGEMENT, Role.MANAGER);
    }

    public Person admin(String username) {
        return person(username, Designation.ADMINISTRATIVE, Role.ADMIN);
    }

    public void route(Person employee, Person manager) {
        tx.executeWithoutResult(status -> assignments.save(
                ApprovalAssignment.assign(employee.id(), manager.id(), clock.now())));
    }

    public TrainingAccount account(Person employee, int year, int units, String budget) {
        return tx.execute(status -> trainingAccounts.save(
                TrainingAccount.open(employee.id(), year, units, new BigDecimal(budget), clock.now())));
    }

    /** A confirmed calendar year with the given synthetic holidays. */
    public void confirmedYear(int year, Person admin, LocalDate... holidayDates) {
        tx.executeWithoutResult(status -> {
            TrainingCalendarYear calendarYear = calendarYears.save(
                    TrainingCalendarYear.draft(year, FIXTURE_NOTE, clock.now()));
            for (LocalDate date : holidayDates) {
                holidays.save(PublicHoliday.create(date, "Fixture holiday " + date, FIXTURE_NOTE, clock.now()));
            }
            calendarYear.confirm(admin.id(), Math.max(holidayDates.length, 1), FIXTURE_NOTE, clock.now());
        });
    }

    public void draftYear(int year, LocalDate... holidayDates) {
        tx.executeWithoutResult(status -> {
            calendarYears.save(TrainingCalendarYear.draft(year, FIXTURE_NOTE, clock.now()));
            for (LocalDate date : holidayDates) {
                holidays.save(PublicHoliday.create(date, "Fixture holiday " + date, FIXTURE_NOTE, clock.now()));
            }
        });
    }

    public void deactivate(Person person) {
        tx.executeWithoutResult(status -> employees.findById(person.id()).orElseThrow().deactivate(clock.now()));
    }

    private static String capitalise(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
