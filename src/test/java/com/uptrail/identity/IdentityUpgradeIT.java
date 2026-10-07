package com.uptrail.identity;

import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;

/** Upgrade an occupied V5 database, not only an empty database. */
class IdentityUpgradeIT {
    @Test
    void migrationPreservesCredentialsRoutingAndApprovedHistory() {
        try (MySQLContainer db = new MySQLContainer("mysql:8.4")
                .withDatabaseName("upgrade_test").withUsername("uptrail").withPassword("uptrail_test")) {
            db.start();
            var source = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
            Flyway.configure().dataSource(source).target("5").load().migrate();
            var jdbc = new JdbcTemplate(source);
            for (long id : new long[] {101, 202, 303, 404}) {
                jdbc.update("INSERT INTO employee(id,staff_no,full_name,email,department,designation_code,active,version,created_at,updated_at) VALUES(?,?,?,?,?,'PROFESSIONAL',1,0,NOW(6),NOW(6))",
                        id, "S" + id, "Person " + id, id + "@example.com", "Testing");
                jdbc.update("INSERT INTO user_account(id,employee_id,username,password_hash,enabled,version,created_at,updated_at) VALUES(?,?,?,?,1,0,NOW(6),NOW(6))",
                        id + 1000, id, "user" + id, "unchanged-hash-" + id);
            }
            jdbc.update("INSERT INTO user_role VALUES(1101,'EMPLOYEE'),(1202,'EMPLOYEE'),(1202,'MANAGER'),(1303,'ADMIN'),(1404,'MANAGER'),(1404,'ADMIN')");
            jdbc.update("INSERT INTO approval_assignment VALUES(101,202,0,NOW(6))");
            jdbc.update("""
                    INSERT INTO course_application(id,reference_no,employee_id,approver_id,category_code,course_title,
                        provider_name,start_date,end_date,start_session,end_session,course_fee,justification,status,
                        submitted_at,updated_at,reviewed_by,reviewed_at,review_comment,version,client_request_id,create_request_hash)
                    VALUES(77,'CA-UPGRADE',101,202,'INTERNAL','Existing course','In-house','2026-10-20','2026-10-20',
                        'AM','PM',0,'Existing justification','APPROVED',NOW(6),NOW(6),202,NOW(6),'Keep this decision',
                        3,'upgrade-request','upgrade-hash')
                    """);
            Flyway.configure().dataSource(source).load().migrate();
            assertThat(jdbc.queryForObject("SELECT staff_id FROM users WHERE id=101", String.class)).isEqualTo("S101");
            assertThat(jdbc.queryForObject("SELECT user_type FROM users WHERE id=202", String.class)).isEqualTo("MANAGER");
            assertThat(jdbc.queryForObject("SELECT user_type FROM users WHERE id=303", String.class)).isEqualTo("ADMIN");
            assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id=101", String.class))
                    .isEqualTo("unchanged-hash-101");
            assertThat(jdbc.queryForList("SELECT role_code FROM user_roles WHERE user_id=202 ORDER BY role_code", String.class))
                    .containsExactly("MANAGER", "STAFF");
            assertThat(jdbc.queryForObject("SELECT user_type FROM users WHERE id=404", String.class)).isEqualTo("MANAGER");
            assertThat(jdbc.queryForList("SELECT role_code FROM user_roles WHERE user_id=404 ORDER BY role_code", String.class))
                    .containsExactly("ADMIN", "MANAGER", "STAFF");
            assertThat(jdbc.queryForObject("SELECT manager_id FROM approval_hierarchy WHERE employee_id=101", Long.class))
                    .isEqualTo(202);
            assertThat(jdbc.queryForMap("SELECT applicant_id,approver_id,reviewed_by,decision_reason,version,status FROM course_application WHERE id=77"))
                    .containsEntry("applicant_id", 101L).containsEntry("approver_id", 202L)
                    .containsEntry("reviewed_by", 202L).containsEntry("decision_reason", "Keep this decision")
                    .containsEntry("version", 3L).containsEntry("status", "APPROVED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('employee','user_account','user_role')", Integer.class))
                    .isZero();
        }
    }
}
