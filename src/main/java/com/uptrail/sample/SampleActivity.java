package com.uptrail.sample;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.uptrail.application.domain.ApplicationDetails;
import com.uptrail.application.domain.CourseApplication;
import com.uptrail.application.repository.CourseApplicationRepository;
import com.uptrail.application.service.ApplicationCommandService;
import com.uptrail.application.service.ApplicationCommandService.Decision;
import com.uptrail.catalogue.domain.CategoryCode;
import com.uptrail.catalogue.service.HolidayCalendarService;
import com.uptrail.entitlement.domain.Session;
import com.uptrail.identity.domain.Actor;
import com.uptrail.identity.domain.Role;
import com.uptrail.identity.domain.UserAccount;
import com.uptrail.identity.repository.UserAccountRepository;
import com.uptrail.organisation.domain.Employee;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.time.BusinessClock;

/**
 * Sample course applications in every state, created through the same services as user actions. Past
 * steps run at past business instants via {@link BusinessClock#callAt}, so every rule (future start,
 * completion after the end date, budgets, overlaps) is applied exactly as for real users. Dates are
 * relative to today; a step that is not possible (for example early in January) is skipped and logged.
 */
@Component
public class SampleActivity {

    private static final Logger log = LoggerFactory.getLogger(SampleActivity.class);

    private final ApplicationCommandService commands;
    private final CourseApplicationRepository applications;
    private final UserAccountRepository accounts;
    private final HolidayCalendarService holidays;
    private final BusinessClock clock;

    public SampleActivity(ApplicationCommandService commands, CourseApplicationRepository applications,
            UserAccountRepository accounts, HolidayCalendarService holidays, BusinessClock clock) {
        this.commands = commands;
        this.applications = applications;
        this.accounts = accounts;
        this.holidays = holidays;
        this.clock = clock;
    }

    /** Result of the sample activity: application ids by a short key, for later sample steps (claims). */
    public record Created(Map<String, Long> applications) {
    }

    public Created seed(SampleOrganisation.Staff staff) {
        Map<String, Long> created = new java.util.LinkedHashMap<>();
        LocalDate today = clock.today();

        // Daniel's team
        completed(created, "siti-spring", staff, "siti", "daniel", external("Spring Application Development",
                "Merlion Tech Academy", "600.00"), today.minusDays(120), 2,
                "Learned Spring Boot testing and security; will run a lunch-and-learn.");
        approvedPast(created, "marcus-scrum", staff, "marcus", "daniel", certification("Scrum Master Certification",
                "Kestrel Professional Institute", "1100.00"), today.minusDays(90), 2);
        pending(created, "weiling-cloud", staff, "weiling", external("Cloud Architecture Foundations",
                "Cloud Guild Academy", "850.00"), today.plusDays(21), 2);
        pendingInternal(created, "arjun-secure", staff, "arjun", "Secure Coding Fundamentals",
                today.plusDays(14), Session.AM, today.plusDays(14), Session.AM);
        updated(created, "hannah-ux", staff, "hannah", external("UX Research Methods", "Harbourline Training",
                "450.00"), today.plusDays(30), 2, "480.00");
        rejected(created, "farid-k8s", staff, "farid", "daniel", external("Kubernetes in Practice",
                "Cloud Guild Academy", "1200.00"), today.plusDays(40), 3,
                "The platform team starts Kubernetes work next year; please reapply in the first quarter.");
        approvedFuture(created, "kelvin-reviews", staff, "kelvin", "daniel", internal("Effective Code Reviews"),
                today.plusDays(10), 1, "Useful for the new review rotation.");
        deleted(created, "siti-agile", staff, "siti", internal("Agile Facilitation Workshop"), today.plusDays(35), 1);
        cancelled(created, "kelvin-privacy", staff, "kelvin", "daniel", internal("Data Privacy Essentials"),
                today.plusDays(50), 1, "Clashes with the release freeze.");
        crossYear(created, "arjun-cert", staff, "arjun", certification("Cloud Solutions Architect Associate",
                "Cloud Guild Academy", "300.00"));

        // Priya's team
        completed(created, "meiling-privacy", staff, "meiling", "priya", internal("Data Privacy Essentials"),
                today.minusDays(100), 1, "Clear guidance on handling personal data in analytics projects.");
        pending(created, "rahul-pm", staff, "rahul", certification("Project Management Professional Preparation",
                "Kestrel Professional Institute", "900.00"), today.plusDays(25), 3);
        approvedFutureInternal(created, "nora-agile", staff, "nora", "priya", "Agile Facilitation Workshop",
                today.plusDays(12));
        completed(created, "junhao-cloud", staff, "junhao", "priya", external("Cloud Architecture Foundations",
                "Cloud Guild Academy", "850.00"), today.minusDays(75), 2,
                "Designed a reference architecture for the reporting service.");
        pending(created, "aisha-spring", staff, "aisha", external("Spring Application Development",
                "Merlion Tech Academy", "600.00"), today.plusDays(18), 2);
        approvedPast(created, "clara-ux", staff, "clara", "priya", external("UX Research Methods",
                "Harbourline Training", "450.00"), today.minusDays(40), 1);

        // Managers apply too; their approver is the director.
        pending(created, "daniel-k8s", staff, "daniel", external("Kubernetes in Practice", "Cloud Guild Academy",
                "1200.00"), today.plusDays(45), 2);
        completed(created, "priya-reviews", staff, "priya", "grace", internal("Effective Code Reviews"),
                today.minusDays(60), 1, "Adopted the checklist for the analytics team.");
        return new Created(created);
    }

    // ------------------------------------------------------------------ scenario steps

    private void pending(Map<String, Long> created, String key, SampleOrganisation.Staff staff, String applicant,
            Template course, LocalDate from, int days) {
        LocalDate start = workingDayOnOrAfter(from);
        run(key, () -> created.put(key, submit(staff, applicant, course.details(start, lastDay(start, days)),
                submittedAt(start, 14))));
    }

    private void pendingInternal(Map<String, Long> created, String key, SampleOrganisation.Staff staff,
            String applicant, String title, LocalDate start, Session startSession, LocalDate end, Session endSession) {
        LocalDate first = workingDayOnOrAfter(start);
        LocalDate last = workingDayOnOrAfter(end);
        run(key, () -> created.put(key, submit(staff, applicant, new ApplicationDetails(CategoryCode.INTERNAL, null,
                title, null, first, last, startSession, endSession, BigDecimal.ZERO,
                "Relevant to my current project work.", null), submittedAt(first, 7))));
    }

    private void updated(Map<String, Long> created, String key, SampleOrganisation.Staff staff, String applicant,
            Template course, LocalDate from, int days, String newFee) {
        LocalDate start = workingDayOnOrAfter(from);
        LocalDate end = lastDay(start, days);
        run(key, () -> {
            Long id = submit(staff, applicant, course.details(start, end), submittedAt(start, 20));
            Actor actor = actor(staff, applicant);
            clock.callAt(submittedAt(start, 18), () -> {
                commands.update(actor, id, course.withFee(newFee).details(start, end), version(id));
                return null;
            });
            created.put(key, id);
        });
    }

    private void rejected(Map<String, Long> created, String key, SampleOrganisation.Staff staff, String applicant,
            String approver, Template course, LocalDate from, int days, String reason) {
        LocalDate start = workingDayOnOrAfter(from);
        run(key, () -> {
            Long id = submit(staff, applicant, course.details(start, lastDay(start, days)), submittedAt(start, 21));
            decide(staff, approver, id, Decision.REJECT, reason, submittedAt(start, 19));
            created.put(key, id);
        });
    }

    private void approvedFuture(Map<String, Long> created, String key, SampleOrganisation.Staff staff,
            String applicant, String approver, Template course, LocalDate from, int days, String reason) {
        LocalDate start = workingDayOnOrAfter(from);
        run(key, () -> {
            Long id = submit(staff, applicant, course.details(start, lastDay(start, days)), submittedAt(start, 14));
            decide(staff, approver, id, Decision.APPROVE, reason, submittedAt(start, 12));
            created.put(key, id);
        });
    }

    private void approvedFutureInternal(Map<String, Long> created, String key, SampleOrganisation.Staff staff,
            String applicant, String approver, String title, LocalDate from) {
        LocalDate day = workingDayOnOrAfter(from);
        run(key, () -> {
            Long id = submit(staff, applicant, new ApplicationDetails(CategoryCode.INTERNAL, null, title, null, day,
                    day, Session.PM, Session.PM, BigDecimal.ZERO, "Facilitating our sprint reviews.", null),
                    submittedAt(day, 10));
            decide(staff, approver, id, Decision.APPROVE, "Good fit for the team's retrospectives.",
                    submittedAt(day, 8));
            created.put(key, id);
        });
    }

    private void approvedPast(Map<String, Long> created, String key, SampleOrganisation.Staff staff,
            String applicant, String approver, Template course, LocalDate from, int days) {
        LocalDate start = workingDayOnOrAfter(from);
        run(key, () -> {
            Long id = submit(staff, applicant, course.details(start, lastDay(start, days)), submittedAt(start, 21));
            decide(staff, approver, id, Decision.APPROVE, "Supports this year's team goals.", submittedAt(start, 18));
            created.put(key, id);
        });
    }

    private void completed(Map<String, Long> created, String key, SampleOrganisation.Staff staff, String applicant,
            String approver, Template course, LocalDate from, int days, String experience) {
        LocalDate start = workingDayOnOrAfter(from);
        LocalDate end = lastDay(start, days);
        run(key, () -> {
            Long id = submit(staff, applicant, course.details(start, end), submittedAt(start, 21));
            decide(staff, approver, id, Decision.APPROVE, "Directly relevant to current projects.",
                    submittedAt(start, 18));
            Actor actor = actor(staff, applicant);
            clock.callAt(at(end.plusDays(3), 10), () -> {
                commands.complete(actor, id, version(id), experience);
                return null;
            });
            created.put(key, id);
        });
    }

    private void deleted(Map<String, Long> created, String key, SampleOrganisation.Staff staff, String applicant,
            Template course, LocalDate from, int days) {
        LocalDate start = workingDayOnOrAfter(from);
        run(key, () -> {
            Long id = submit(staff, applicant, course.details(start, lastDay(start, days)), submittedAt(start, 25));
            Actor actor = actor(staff, applicant);
            clock.callAt(submittedAt(start, 24), () -> {
                commands.delete(actor, id, version(id));
                return null;
            });
            created.put(key, id);
        });
    }

    private void cancelled(Map<String, Long> created, String key, SampleOrganisation.Staff staff, String applicant,
            String approver, Template course, LocalDate from, int days, String reason) {
        LocalDate start = workingDayOnOrAfter(from);
        run(key, () -> {
            Long id = submit(staff, applicant, course.details(start, lastDay(start, days)), submittedAt(start, 30));
            decide(staff, approver, id, Decision.APPROVE, "Approved.", submittedAt(start, 28));
            Actor actor = actor(staff, applicant);
            clock.callAt(submittedAt(start, 20), () -> {
                commands.cancel(actor, id, version(id), reason);
                return null;
            });
            created.put(key, id);
        });
    }

    private void crossYear(Map<String, Long> created, String key, SampleOrganisation.Staff staff, String applicant,
            Template course) {
        int year = clock.currentYear();
        LocalDate start = workingDayOnOrAfter(LocalDate.of(year, 12, 29));
        LocalDate end = workingDayOnOrAfter(LocalDate.of(year + 1, 1, 4));
        if (!start.isAfter(clock.today())) {
            log.info("Sample step {} skipped: the end of the year has passed.", key);
            return;
        }
        run(key, () -> created.put(key, submit(staff, applicant, course.details(start, end), clock.now())));
    }

    // ------------------------------------------------------------------ helpers

    private Long submit(SampleOrganisation.Staff staff, String applicant, ApplicationDetails details, Instant at) {
        Actor actor = actor(staff, applicant);
        return clock.callAt(at, () -> commands.submit(actor, details, UUID.randomUUID().toString()).applicationId());
    }

    private void decide(SampleOrganisation.Staff staff, String approver, Long id, Decision decision, String reason,
            Instant at) {
        Actor actor = actor(staff, approver);
        clock.callAt(at, () -> {
            commands.decide(actor, id, decision, reason, version(id));
            return null;
        });
    }

    private long version(Long applicationId) {
        return applications.findById(applicationId).map(CourseApplication::getVersion).orElseThrow();
    }

    private Actor actor(SampleOrganisation.Staff staff, String username) {
        Employee employee = staff.get(username);
        UserAccount account = accounts.findByEmployeeId(employee.getId()).orElseThrow();
        return new Actor(employee.getId(), employee.getFullName(), account.getRoles().isEmpty()
                ? java.util.Set.of(Role.EMPLOYEE) : account.getRoles());
    }

    /** Submission instant a number of days before the course, but never in the future. */
    private Instant submittedAt(LocalDate courseStart, int daysBefore) {
        LocalDate date = courseStart.minusDays(daysBefore);
        Instant candidate = at(date, 9);
        return candidate.isAfter(clock.now()) ? clock.now() : candidate;
    }

    private static Instant at(LocalDate date, int hour) {
        return ZonedDateTime.of(date, LocalTime.of(hour, 0), BusinessClock.ZONE).toInstant();
    }

    private LocalDate workingDayOnOrAfter(LocalDate date) {
        Map<LocalDate, String> holidayNames = holidays.holidaysBetween(date, date.plusDays(20));
        LocalDate day = date;
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY
                || holidayNames.containsKey(day)) {
            day = day.plusDays(1);
        }
        return day;
    }

    /** Last day of a course of {@code workingDays} consecutive working days starting at {@code start}. */
    private LocalDate lastDay(LocalDate start, int workingDays) {
        LocalDate day = start;
        for (int counted = 1; counted < workingDays; counted++) {
            day = workingDayOnOrAfter(day.plusDays(1));
        }
        return day;
    }

    private void run(String key, Runnable step) {
        try {
            step.run();
        } catch (BusinessException | IllegalStateException e) {
            log.info("Sample step {} skipped: {}", key, e.getMessage());
        }
    }

    private record Template(CategoryCode category, String title, String provider, String fee) {

        ApplicationDetails details(LocalDate start, LocalDate end) {
            return new ApplicationDetails(category, null, title, provider, start, end, Session.AM, Session.PM,
                    new BigDecimal(fee), "Directly supports my current responsibilities.",
                    "A teammate covers my support rotation.");
        }

        Template withFee(String newFee) {
            return new Template(category, title, provider, newFee);
        }
    }

    private static Template external(String title, String provider, String fee) {
        return new Template(CategoryCode.EXTERNAL, title, provider, fee);
    }

    private static Template certification(String title, String provider, String fee) {
        return new Template(CategoryCode.CERTIFICATION, title, provider, fee);
    }

    private static Template internal(String title) {
        return new Template(CategoryCode.INTERNAL, title, null, "0.00");
    }
}
