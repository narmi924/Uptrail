package com.uptrail.sample;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.uptrail.model.CatalogueCourse;
import com.uptrail.model.CategoryCode;
import com.uptrail.model.ExcludedDays;
import com.uptrail.model.TrainingCalendarYear;
import com.uptrail.model.TrainingProvider;
import com.uptrail.repo.CatalogueCourseRepo;
import com.uptrail.repo.ExcludedDaysRepo;
import com.uptrail.repo.TrainingCalendarYearRepo;
import com.uptrail.repo.TrainingProviderRepo;
import com.uptrail.service.OfficialHolidayData;
import com.uptrail.model.TrainingEntitlement;
import com.uptrail.repo.TrainingEntitlementRepo;
import com.uptrail.service.EntitlementDefaults;
import com.uptrail.model.Role;
import com.uptrail.model.User;
import com.uptrail.repo.UserRepo;
import com.uptrail.model.ApprovalHierarchy;
import com.uptrail.model.Designation;
import com.uptrail.repo.ApprovalHierarchyRepo;
import com.uptrail.shared.time.BusinessClock;

/**
 * Synthetic organisation: fictional people ({@code @example.com}), routing, annual accounts, providers,
 * catalogue and the bundled public holiday calendars. This is configuration data, so it is written
 * directly; business activity (applications, claims) goes through the normal services.
 */
@Component
public class SampleOrganisation {

    private static final String DOMAIN = "@example.com";

    /** Staff keyed by username, in creation order. */
    public static final class Staff extends LinkedHashMap<String, User> {
        public User get(String username) {
            User employee = super.get(username);
            if (employee == null) {
                throw new IllegalArgumentException("Unknown sample user " + username);
            }
            return employee;
        }
    }

    private record Person(String username, String name, String department, Designation designation,
            Set<Role> roles, String manager, boolean active, boolean nextYearAccount) {
    }

    private static final List<Person> PEOPLE = List.of(
            new Person("alex", "Alex Tan", "IT Services", Designation.ADMINISTRATIVE, EnumSet.of(Role.ADMIN),
                    null, true, false),
            new Person("grace", "Grace Lim", "Leadership", Designation.MANAGEMENT, EnumSet.of(Role.MANAGER),
                    null, true, true),
            new Person("daniel", "Daniel Wong", "Software Engineering", Designation.MANAGEMENT,
                    EnumSet.of(Role.MANAGER), "grace", true, true),
            new Person("priya", "Priya Nair", "Data and Analytics", Designation.MANAGEMENT,
                    EnumSet.of(Role.MANAGER), "grace", true, true),
            new Person("nurul", "Nurul Huda", "Human Resources", Designation.ADMINISTRATIVE,
                    EnumSet.of(Role.ADMIN, Role.STAFF), "grace", true, true),
            staff("siti", "Siti Rahman", "Software Engineering", Designation.PROFESSIONAL, "daniel"),
            staff("marcus", "Marcus Lee", "Software Engineering", Designation.PROFESSIONAL, "daniel"),
            staff("weiling", "Wei Ling Chua", "Software Engineering", Designation.PROFESSIONAL, "daniel"),
            staff("arjun", "Arjun Menon", "Software Engineering", Designation.PROFESSIONAL, "daniel"),
            staff("hannah", "Hannah Goh", "Software Engineering", Designation.ADMINISTRATIVE, "daniel"),
            staff("farid", "Farid Ismail", "Software Engineering", Designation.PROFESSIONAL, "daniel"),
            new Person("jasmine", "Jasmine Teo", "Software Engineering", Designation.PROFESSIONAL,
                    EnumSet.of(Role.STAFF), "daniel", true, false),
            staff("kelvin", "Kelvin Ng", "Software Engineering", Designation.PROFESSIONAL, "daniel"),
            staff("meiling", "Mei Ling Ho", "Data and Analytics", Designation.PROFESSIONAL, "priya"),
            staff("rahul", "Rahul Sharma", "Data and Analytics", Designation.PROFESSIONAL, "priya"),
            staff("nora", "Nora Lim", "Data and Analytics", Designation.ADMINISTRATIVE, "priya"),
            staff("junhao", "Jun Hao Tan", "Data and Analytics", Designation.PROFESSIONAL, "priya"),
            staff("aisha", "Aisha Yusof", "Data and Analytics", Designation.PROFESSIONAL, "priya"),
            staff("clara", "Clara Wee", "Data and Analytics", Designation.PROFESSIONAL, "priya"),
            new Person("benjamin", "Benjamin Ong", "Data and Analytics", Designation.PROFESSIONAL,
                    EnumSet.of(Role.STAFF), "priya", false, false),
            new Person("ethan", "Ethan Koh", "Data and Analytics", Designation.PROFESSIONAL,
                    EnumSet.of(Role.STAFF), null, true, true));

    private static Person staff(String username, String name, String department, Designation designation,
            String manager) {
        return new Person(username, name, department, designation, EnumSet.of(Role.STAFF), manager, true,
                true);
    }

    private final UserRepo employees;
    private final UserRepo accounts;
    private final ApprovalHierarchyRepo assignments;
    private final TrainingEntitlementRepo trainingAccounts;
    private final TrainingCalendarYearRepo calendarYears;
    private final ExcludedDaysRepo holidays;
    private final TrainingProviderRepo providers;
    private final CatalogueCourseRepo catalogue;
    private final OfficialHolidayData officialHolidays;
    private final EntitlementDefaults defaults;
    private final PasswordEncoder passwordEncoder;
    private final BusinessClock clock;
    private final TransactionTemplate transactions;
    private final String samplePassword;

    public SampleOrganisation(UserRepo employees, UserRepo accounts,
            ApprovalHierarchyRepo assignments, TrainingEntitlementRepo trainingAccounts,
            TrainingCalendarYearRepo calendarYears, ExcludedDaysRepo holidays,
            TrainingProviderRepo providers, CatalogueCourseRepo catalogue,
            OfficialHolidayData officialHolidays, EntitlementDefaults defaults, PasswordEncoder passwordEncoder,
            BusinessClock clock, TransactionTemplate transactions,
            @Value("${uptrail.sample-data.password}") String samplePassword) {
        this.employees = employees;
        this.accounts = accounts;
        this.assignments = assignments;
        this.trainingAccounts = trainingAccounts;
        this.calendarYears = calendarYears;
        this.holidays = holidays;
        this.providers = providers;
        this.catalogue = catalogue;
        this.officialHolidays = officialHolidays;
        this.defaults = defaults;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.transactions = transactions;
        this.samplePassword = samplePassword;
    }

    public Staff seed() {
        return transactions.execute(status -> {
            Staff staff = createStaff();
            createCalendars(staff.get("alex").getUserId());
            createAccounts(staff);
            createCatalogue();
            return staff;
        });
    }

    private Staff createStaff() {
        var now = clock.now();
        String passwordHash = passwordEncoder.encode(samplePassword);
        Staff staff = new Staff();
        int number = 1001;
        for (Person person : PEOPLE) {
            String email = person.name().toLowerCase().replace(' ', '.') + DOMAIN;
            User employee = employees.save(User.create("S" + number++, person.name(), email,
                    person.department(), person.designation(), person.username(), passwordHash,
                    person.roles(), now));
            User account = employee;
            if (!person.active()) {
                employee.deactivate(now);
                account.disable(now);
            }
            accounts.save(account);
            staff.put(person.username(), employee);
        }
        for (Person person : PEOPLE) {
            if (person.manager() != null) {
                assignments.save(ApprovalHierarchy.assign(staff.get(person.username()).getUserId(),
                        staff.get(person.manager()).getUserId(), now));
            }
        }
        return staff;
    }

    private void createCalendars(Long adminId) {
        var now = clock.now();
        for (int year : officialHolidays.years()) {
            TrainingCalendarYear calendarYear = calendarYears.save(
                    TrainingCalendarYear.draft(year, OfficialHolidayData.SOURCE_NOTE, now));
            List<OfficialHolidayData.Entry> entries = officialHolidays.forYear(year);
            for (OfficialHolidayData.Entry entry : entries) {
                holidays.save(ExcludedDays.create(entry.date(), entry.name(), OfficialHolidayData.SOURCE_NOTE, now));
            }
            calendarYear.confirm(adminId, entries.size(), OfficialHolidayData.SOURCE_NOTE, now);
        }
    }

    private void createAccounts(Staff staff) {
        var now = clock.now();
        int year = clock.currentYear();
        for (Person person : PEOPLE) {
            boolean applicant = person.roles().contains(Role.STAFF) || person.roles().contains(Role.MANAGER);
            if (!applicant || !person.active()) {
                continue;
            }
            User employee = staff.get(person.username());
            EntitlementDefaults.Allowance allowance = defaults.forDesignation(person.designation());
            trainingAccounts.save(TrainingEntitlement.open(employee.getUserId(), year, allowance.units(),
                    allowance.budget(), now));
            if (person.nextYearAccount()) {
                trainingAccounts.save(TrainingEntitlement.open(employee.getUserId(), year + 1, allowance.units(),
                        allowance.budget(), now));
            }
        }
    }

    private void createCatalogue() {
        Map<String, TrainingProvider> byName = new LinkedHashMap<>();
        for (String name : List.of("Harbourline Training", "Merlion Tech Academy", "Kestrel Professional Institute",
                "Cloud Guild Academy")) {
            byName.put(name, providers.save(TrainingProvider.create(name)));
        }
        course(CategoryCode.INTERNAL, null, "Secure Coding Fundamentals", "0.00",
                "Half-day sessions on common web vulnerabilities and code review checklists.");
        course(CategoryCode.INTERNAL, null, "Effective Code Reviews", "0.00", "Team practices for reviews.");
        course(CategoryCode.INTERNAL, null, "Data Privacy Essentials", "0.00", "Handling personal data at work.");
        course(CategoryCode.INTERNAL, null, "Agile Facilitation Workshop", "0.00", "Running planning and retrospectives.");
        course(CategoryCode.EXTERNAL, byName.get("Merlion Tech Academy"), "Spring Application Development", "600.00",
                "Building web applications with Spring Boot.");
        course(CategoryCode.EXTERNAL, byName.get("Cloud Guild Academy"), "Cloud Architecture Foundations", "850.00",
                "Designing resilient cloud systems.");
        course(CategoryCode.EXTERNAL, byName.get("Harbourline Training"), "UX Research Methods", "450.00",
                "Interviews, usability tests and synthesis.");
        course(CategoryCode.EXTERNAL, byName.get("Cloud Guild Academy"), "Kubernetes in Practice", "1200.00",
                "Operating containerised workloads.");
        course(CategoryCode.CERTIFICATION, byName.get("Kestrel Professional Institute"), "Scrum Master Certification",
                "1100.00", "Two-day course with certification exam.");
        course(CategoryCode.CERTIFICATION, byName.get("Cloud Guild Academy"), "Cloud Solutions Architect Associate",
                "300.00", "Exam preparation and exam voucher.");
        course(CategoryCode.CERTIFICATION, byName.get("Kestrel Professional Institute"),
                "Project Management Professional Preparation", "900.00", "Exam preparation course.");
    }

    private void course(CategoryCode category, TrainingProvider provider, String title, String fee,
            String description) {
        catalogue.save(CatalogueCourse.create(category, provider == null ? null : provider.getId(), title,
                new BigDecimal(fee), description));
    }

    public static List<String> usernames() {
        return PEOPLE.stream().map(Person::username).toList();
    }
}
