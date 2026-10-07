package com.uptrail.support;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Empties all business tables of the disposable test database between tests. Reference rows created by
 * migrations (the three course categories) and the Flyway history are kept.
 */
public final class DatabaseCleaner {

    private static final List<String> TABLES = List.of(
            "email_outbox", "claim_document", "training_ledger", "course_fee_application", "audit_event",
            "application_day", "course_application", "training_entitlement", "excluded_days",
            "training_calendar_year", "course_catalogue", "training_provider", "approval_hierarchy",
            "user_roles", "users");

    private DatabaseCleaner() {
    }

    public static void clean(JdbcTemplate jdbc, TransactionTemplate tx) {
        tx.executeWithoutResult(status -> {
            for (String table : TABLES) {
                jdbc.update("DELETE FROM " + table);
            }
            jdbc.update("UPDATE course_category SET display_name = CASE code "
                    + "WHEN 'INTERNAL' THEN 'Internal Training' WHEN 'EXTERNAL' THEN 'External Course' "
                    + "ELSE 'Professional Certification' END");
        });
    }
}
