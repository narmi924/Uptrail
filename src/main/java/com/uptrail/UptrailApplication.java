package com.uptrail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Uptrail - Staff Training and Approvals.
 * An implementation of the SA63 Course Application Tracking System (CATS).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class UptrailApplication {

    public static void main(String[] args) {
        SpringApplication.run(UptrailApplication.class, args);
    }
}
