package com.uptrail.support;

import java.time.LocalDate;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    /** Default business date for integration tests: Monday 5 October 2026, 09:00 Singapore time. */
    public static final LocalDate DEFAULT_TODAY = LocalDate.of(2026, 10, 5);

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock(MutableClock.at(DEFAULT_TODAY, 9));
    }
}
