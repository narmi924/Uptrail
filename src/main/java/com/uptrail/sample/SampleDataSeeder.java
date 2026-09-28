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

    public SampleDataSeeder(EmployeeRepository employees, SampleOrganisation organisation) {
        this.employees = employees;
        this.organisation = organisation;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (employees.count() > 0) {
            log.info("Sample data skipped: the database already contains employees.");
            return;
        }
        SampleOrganisation.Staff staff = organisation.seed();
        log.info("Sample data loaded: {} staff accounts. See README for the sign-in details.", staff.size());
    }
}
