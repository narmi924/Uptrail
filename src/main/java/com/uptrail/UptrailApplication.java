package com.uptrail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Uptrail - staff training applications, approvals, budgets and fee claims.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class UptrailApplication {

    public static void main(String[] args) {
        SpringApplication.run(UptrailApplication.class, args);
    }
}
