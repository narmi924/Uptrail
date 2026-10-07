package com.uptrail.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import com.uptrail.model.ExcludedDays;
import com.uptrail.model.TrainingCalendarYear;
import com.uptrail.repo.ExcludedDaysRepo;
import com.uptrail.repo.TrainingCalendarYearRepo;
import com.uptrail.model.TrainingEntitlement;
import com.uptrail.repo.TrainingEntitlementRepo;
import com.uptrail.model.User;
import com.uptrail.model.Role;
import com.uptrail.repo.UserRepo;
import com.uptrail.model.ApprovalHierarchy;
import com.uptrail.model.Designation;
import com.uptrail.repo.ApprovalHierarchyRepo;
import com.uptrail.shared.time.BusinessClock;

/**
 * Builds test data directly in the database. Holidays created here are synthetic and marked FIXTURE.
 */
@TestComponent
public class Fixtures {

    public static final String PASSWORD = "Test-password-1";
    public static final String FIXTURE_NOTE = "FIXTURE: synthetic test calendar, not official data";

    private static final AtomicInteger SEQUENCE = new AtomicInteger(1);

    private final UserRepo employees;
    private final UserRepo accounts;
    private final ApprovalHierarchyRepo assignments;
    private final TrainingEntitlementRepo trainingAccounts;
    private final TrainingCalendarYearRepo calendarYears;
    private final ExcludedDaysRepo holidays;
    private final PasswordEncoder passwordEncoder;
    private final BusinessClock clock;
    private final TransactionTemplate tx;
    private String passwordHash;

    public Fixtures(UserRepo employees, UserRepo accounts,
            ApprovalHierarchyRepo assignments, TrainingEntitlementRepo trainingAccounts,
            TrainingCalendarYearRepo calendarYears, ExcludedDaysRepo holidays,
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

    public record Person(User employee, String username, User actor) {
        public Long id() {
            return employee.getUserId();
        }
    }

    public Person person(String username, Designation designation, Role... roles) {
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
        return tx.execute(status -> {
            int n = SEQUENCE.getAndIncrement();
            User employee = employees.save(User.create("T" + n, capitalise(username) + " Tester",
                    username + "@example.com", "Testing", designation, username, passwordHash,
                    EnumSet.copyOf(List.of(roles)), clock.now()));
            User account = employee;
            return new Person(employee, account.getUserName(),
                    User.identity(employee.getUserId(), employee.getName(), account.getRoles()));
        });
    }

    public Person employee(String username) {
        return person(username, Designation.PROFESSIONAL, Role.STAFF);
    }

    public Person manager(String username) {
        return person(username, Designation.MANAGEMENT, Role.MANAGER);
    }

    public Person admin(String username) {
        return person(username, Designation.ADMINISTRATIVE, Role.ADMIN);
    }

    public void route(Person employee, Person manager) {
        tx.executeWithoutResult(status -> assignments.save(
                ApprovalHierarchy.assign(employee.id(), manager.id(), clock.now())));
    }

    public TrainingEntitlement account(Person employee, int year, int units, String budget) {
        return tx.execute(status -> trainingAccounts.save(
                TrainingEntitlement.open(employee.id(), year, units, new BigDecimal(budget), clock.now())));
    }

    /** A confirmed calendar year with the given synthetic holidays. */
    public void confirmedYear(int year, Person admin, LocalDate... holidayDates) {
        tx.executeWithoutResult(status -> {
            TrainingCalendarYear calendarYear = calendarYears.save(
                    TrainingCalendarYear.draft(year, FIXTURE_NOTE, clock.now()));
            for (LocalDate date : holidayDates) {
                holidays.save(ExcludedDays.create(date, "Fixture holiday " + date, FIXTURE_NOTE, clock.now()));
            }
            calendarYear.confirm(admin.id(), Math.max(holidayDates.length, 1), FIXTURE_NOTE, clock.now());
        });
    }

    public void draftYear(int year, LocalDate... holidayDates) {
        tx.executeWithoutResult(status -> {
            calendarYears.save(TrainingCalendarYear.draft(year, FIXTURE_NOTE, clock.now()));
            for (LocalDate date : holidayDates) {
                holidays.save(ExcludedDays.create(date, "Fixture holiday " + date, FIXTURE_NOTE, clock.now()));
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
