package com.uptrail.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.uptrail.organisation.repository.EmployeeRepository;

/**
 * Loads the synthetic sample organisation when {@code uptrail.sample-data.enabled=true} and the database
 * has no employees yet. It never modifies a database that already contains people.
 */
@Component
@ConditionalOnProperty(name = "uptrail.sample-data.enabled", havingValue = "true")
public class SampleDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

    private final EmployeeRepository employees;
    private final SampleOrganisation organisation;
    private final SampleActivity activity;
    private final SampleClaims claims;

    public SampleDataSeeder(EmployeeRepository employees, SampleOrganisation organisation, SampleActivity activity,
            SampleClaims claims) {
        this.employees = employees;
        this.organisation = organisation;
        this.activity = activity;
        this.claims = claims;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (employees.count() > 0) {
            log.info("Sample data skipped: the database already contains employees.");
            return;
        }
        SampleOrganisation.Staff staff = organisation.seed();
        SampleActivity.Created created = activity.seed(staff);
        int claimCount = claims.seed(staff, created.applications());
        log.info("Sample data loaded: {} staff accounts, {} course applications and {} fee claims. See README for "
                + "the sign-in details.", staff.size(), created.applications().size(), claimCount);
    }
}
