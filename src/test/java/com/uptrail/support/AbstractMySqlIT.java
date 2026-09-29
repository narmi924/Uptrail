package com.uptrail.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Base class of integration tests: full application context against real MySQL, a movable business clock
 * and an empty database before each test. All subclasses share one cached Spring context.
 */
@SpringBootTest(properties = {
        "uptrail.mail.worker.enabled=false",
        "uptrail.sample-data.enabled=false",
        "uptrail.documents.root=target/test-documents",
        "uptrail.documents.sweep.enabled=false",
        "uptrail.mail.capture-dir=target/test-mail"
})
@AutoConfigureMockMvc
@Import({TestClockConfig.class, Fixtures.class})
public abstract class AbstractMySqlIT {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected Fixtures fixtures;

    @Autowired
    protected MockMvc mvc;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        MySqlTestDatabase database = MySqlTestDatabase.get();
        registry.add("spring.datasource.url", database::jdbcUrl);
        registry.add("spring.datasource.username", database::username);
        registry.add("spring.datasource.password", database::password);
    }

    @BeforeEach
    void resetState() {
        DatabaseCleaner.clean(jdbc, transactionTemplate);
        clock.setDate(TestClockConfig.DEFAULT_TODAY);
    }
}
