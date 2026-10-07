package com.uptrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import com.uptrail.support.AbstractMySqlIT;

/**
 * The application context only starts if Flyway migrated an empty database and Hibernate validated every
 * entity against the resulting schema ({@code ddl-auto=validate}). The checks below confirm the schema
 * contents and a sample of the database-level constraints.
 */
class SchemaMigrationIT extends AbstractMySqlIT {

    @Test
    void migrationsCreateAllSeventeenBusinessTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name <> 'flyway_schema_history' ORDER BY table_name",
                String.class);

        assertThat(tables).containsExactlyInAnyOrder(
                "users", "user_roles", "approval_hierarchy", "training_entitlement",
                "course_category", "training_provider", "course_catalogue", "excluded_days",
                "training_calendar_year", "course_application", "application_day", "course_fee_application",
                "claim_document", "audit_event", "training_ledger", "email_outbox");
    }

    @Test
    void allFiveMigrationsAreApplied() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6");
    }

    @Test
    void theThreeFixedCourseCategoriesExist() {
        List<String> codes = jdbc.queryForList("SELECT code FROM course_category ORDER BY code", String.class);

        assertThat(codes).containsExactly("CERTIFICATION", "EXTERNAL", "INTERNAL");
    }

    @Test
    void aFourthCategoryIsRejectedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO course_category (code, display_name, version) VALUES ('WORKSHOP', 'Workshop', 0)"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_course_category_code");
    }

    @Test
    void aHolidayNeedsItsCalendarYear() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO excluded_days (holiday_date, name, source_note, updated_at) "
                        + "VALUES ('2031-01-01', 'New Year', 'FIXTURE', NOW(6))"))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("INSERT INTO training_calendar_year (calendar_year, status, source_note, version, created_at, "
                + "updated_at) VALUES (2031, 'DRAFT', 'FIXTURE', 0, NOW(6), NOW(6))");
        int inserted = jdbc.update("INSERT INTO excluded_days (holiday_date, name, source_note, updated_at) "
                + "VALUES ('2031-01-01', 'New Year', 'FIXTURE', NOW(6))");

        assertThat(inserted).isEqualTo(1);
    }

    @Test
    void internalCatalogueCoursesMustBeFree() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO course_catalogue (category_code, title, default_fee, active, version) "
                        + "VALUES ('INTERNAL', 'Paid internal course', 10.00, 1, 0)"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_catalogue_internal_free");
    }
}
