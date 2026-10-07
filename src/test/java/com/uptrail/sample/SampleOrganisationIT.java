package com.uptrail.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.uptrail.support.AbstractMySqlIT;

/**
 * The sample organisation loads consistent configuration: people, roles, routing, accounts, confirmed
 * holiday calendars with their source, and a catalogue.
 */
class SampleOrganisationIT extends AbstractMySqlIT {

    @Autowired
    private SampleOrganisation sampleOrganisation;

    @Value("${uptrail.sample-data.password}")
    private String samplePassword;

    @Test
    void loadsTheSampleOrganisation() {
        SampleOrganisation.Staff staff = sampleOrganisation.seed();

        assertThat(staff).hasSize(SampleOrganisation.usernames().size());
        assertThat(count("SELECT COUNT(*) FROM users WHERE active = 1")).isEqualTo(staff.size() - 1);
        assertThat(count("SELECT COUNT(*) FROM users WHERE email NOT LIKE '%@example.com'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM training_calendar_year WHERE status = 'CONFIRMED'")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM excluded_days WHERE YEAR(holiday_date) = 2026")).isEqualTo(14);
        assertThat(count("SELECT COUNT(*) FROM excluded_days WHERE YEAR(holiday_date) = 2027")).isEqualTo(12);
        assertThat(count("SELECT COUNT(*) FROM excluded_days WHERE source_note NOT LIKE '%retrieved 2026-09-28%'"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM course_catalogue")).isEqualTo(11);
        assertThat(count("SELECT COUNT(*) FROM course_catalogue WHERE category_code = 'INTERNAL' AND default_fee <> 0"))
                .isZero();
        // Grace (top-level manager) and Ethan (new joiner) intentionally have no approver.
        assertThat(count("SELECT COUNT(*) FROM approval_hierarchy")).isEqualTo(staff.size() - 3);
        // Jasmine has no account for next year; the admin-only account and the inactive employee have none.
        assertThat(count("SELECT COUNT(*) FROM training_entitlement WHERE calendar_year = 2026")).isEqualTo(19);
        assertThat(count("SELECT COUNT(*) FROM training_entitlement WHERE calendar_year = 2027")).isEqualTo(18);
    }

    @Test
    void sampleAccountsCanSignInAtTheirEntryPoints() throws Exception {
        sampleOrganisation.seed();

        mvc.perform(formLogin("/employee/login").user("daniel").password(samplePassword)).andExpect(authenticated());
        mvc.perform(formLogin("/admin/login").user("alex").password(samplePassword)).andExpect(authenticated());
        mvc.perform(formLogin("/employee/login").user("benjamin").password(samplePassword)).andExpect(unauthenticated());
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
