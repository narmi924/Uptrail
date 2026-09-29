package com.uptrail.sample;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.uptrail.application.domain.CourseApplication;
import com.uptrail.application.repository.CourseApplicationRepository;
import com.uptrail.claim.domain.CourseClaim;
import com.uptrail.claim.repository.CourseClaimRepository;
import com.uptrail.claim.service.ClaimCommandService;
import com.uptrail.claim.service.ClaimCommandService.Decision;
import com.uptrail.claim.service.DocumentStorage.Upload;
import com.uptrail.identity.domain.Actor;
import com.uptrail.identity.domain.Role;
import com.uptrail.identity.domain.UserAccount;
import com.uptrail.identity.repository.UserAccountRepository;
import com.uptrail.organisation.domain.Employee;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.time.BusinessClock;

/**
 * Sample fee claims in every state, created through the claim service at past business instants after the
 * sample courses were completed. Documents are small synthetic PDF files labelled SAMPLE.
 */
@Component
public class SampleClaims {

    private static final Logger log = LoggerFactory.getLogger(SampleClaims.class);

    private final ClaimCommandService commands;
    private final CourseClaimRepository claims;
    private final CourseApplicationRepository applications;
    private final UserAccountRepository accounts;
    private final BusinessClock clock;

    public SampleClaims(ClaimCommandService commands, CourseClaimRepository claims,
            CourseApplicationRepository applications, UserAccountRepository accounts, BusinessClock clock) {
        this.commands = commands;
        this.claims = claims;
        this.applications = applications;
        this.accounts = accounts;
        this.clock = clock;
    }

    /** Creates the sample claims and returns how many were created. */
    public int seed(SampleOrganisation.Staff staff, Map<String, Long> created) {
        int count = 0;
        count += step("siti-spring", () -> {
            Long id = submit(staff, created, "siti-spring", "siti", "600.00", 5);
            decide(staff, "daniel", id, Decision.APPROVE, "Receipt and certificate match the approved course.",
                    created, "siti-spring", 8);
            register(staff, "alex", id, created, "siti-spring", 10);
        });
        count += step("junhao-cloud", () -> {
            Long id = submit(staff, created, "junhao-cloud", "junhao", "850.00", 4);
            decide(staff, "priya", id, Decision.APPROVE, "Documents checked; full course fee.", created,
                    "junhao-cloud", 6);
        });
        count += step("weiling-data", () -> {
            Long id = submit(staff, created, "weiling-data", "weiling", "700.00", 5);
            decide(staff, "daniel", id, Decision.REJECT,
                    "The receipt shows a different course. Attach the receipt for Data Engineering with Python.",
                    created, "weiling-data", 9);
            Actor actor = actor(staff, "weiling");
            clock.callAt(after(created, "weiling-data", 12), () -> {
                commands.resubmit(actor, id, version(id), new BigDecimal("700.00"), true,
                        receipt(created, "weiling-data", "700.00"), certificate(created, "weiling-data"));
                return null;
            });
        });
        count += step("aisha-cert", () -> submit(staff, created, "aisha-cert", "aisha", "500.00", 3));
        count += step("rahul-bi", () -> {
            Long id = submit(staff, created, "rahul-bi", "rahul", "400.00", 6);
            decide(staff, "priya", id, Decision.REJECT,
                    "The certificate has no course dates. Ask the provider for a dated certificate and resubmit.",
                    created, "rahul-bi", 9);
        });
        return count;
    }

    private Long submit(SampleOrganisation.Staff staff, Map<String, Long> created, String key, String claimant,
            String amount, int daysAfterEnd) {
        Long applicationId = required(created, key);
        Actor actor = actor(staff, claimant);
        return clock.callAt(after(created, key, daysAfterEnd), () -> commands.submit(actor, applicationId,
                new BigDecimal(amount), true, receipt(created, key, amount), certificate(created, key)));
    }

    private void decide(SampleOrganisation.Staff staff, String manager, Long claimId, Decision decision,
            String reason, Map<String, Long> created, String key, int daysAfterEnd) {
        Actor actor = actor(staff, manager);
        clock.callAt(after(created, key, daysAfterEnd), () -> {
            commands.decide(actor, claimId, decision, reason, version(claimId));
            return null;
        });
    }

    private void register(SampleOrganisation.Staff staff, String admin, Long claimId, Map<String, Long> created,
            String key, int daysAfterEnd) {
        Actor actor = actor(staff, admin);
        clock.callAt(after(created, key, daysAfterEnd),
                () -> commands.registerReimbursement(actor, claimId, version(claimId)));
    }

    private Upload receipt(Map<String, Long> created, String key, String amount) {
        CourseApplication application = application(created, key);
        return new Upload("sample-receipt-" + application.getReferenceNo() + ".pdf", SampleDocuments.pdf(
                "SAMPLE RECEIPT", "Course: " + application.getCourseTitle(),
                "Provider: " + application.getProviderName(), "Amount paid: SGD " + amount,
                "Course dates: " + application.getStartDate() + " to " + application.getEndDate()));
    }

    private Upload certificate(Map<String, Long> created, String key) {
        CourseApplication application = application(created, key);
        return new Upload("sample-certificate-" + application.getReferenceNo() + ".pdf", SampleDocuments.pdf(
                "SAMPLE CERTIFICATE OF COMPLETION", "Course: " + application.getCourseTitle(),
                "Provider: " + application.getProviderName(),
                "Completed: " + application.getStartDate() + " to " + application.getEndDate()));
    }

    /** A business instant some days after the course ended, never later than now. */
    private Instant after(Map<String, Long> created, String key, int days) {
        LocalDate date = application(created, key).getEndDate().plusDays(days);
        Instant candidate = ZonedDateTime.of(date, LocalTime.of(11, 0), BusinessClock.ZONE).toInstant();
        return candidate.isAfter(clock.now()) ? clock.now() : candidate;
    }

    private CourseApplication application(Map<String, Long> created, String key) {
        return applications.findById(required(created, key)).orElseThrow();
    }

    private static Long required(Map<String, Long> created, String key) {
        Long id = created.get(key);
        if (id == null) {
            throw new IllegalStateException("the sample course " + key + " was not created");
        }
        return id;
    }

    private long version(Long claimId) {
        return claims.findById(claimId).map(CourseClaim::getVersion).orElseThrow();
    }

    private Actor actor(SampleOrganisation.Staff staff, String username) {
        Employee employee = staff.get(username);
        UserAccount account = accounts.findByEmployeeId(employee.getId()).orElseThrow();
        return new Actor(employee.getId(), employee.getFullName(),
                account.getRoles().isEmpty() ? Set.of(Role.EMPLOYEE) : account.getRoles());
    }

    private int step(String key, Runnable step) {
        try {
            step.run();
            return 1;
        } catch (BusinessException | IllegalStateException e) {
            log.info("Sample claim {} skipped: {}", key, e.getMessage());
            return 0;
        }
    }
}
