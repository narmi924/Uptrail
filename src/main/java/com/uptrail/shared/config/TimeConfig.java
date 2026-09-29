package com.uptrail.shared.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.uptrail.shared.time.BusinessClock;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock systemClock() {
        return Clock.system(BusinessClock.ZONE);
    }
}
