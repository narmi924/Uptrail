package com.uptrail.entitlement.service;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.uptrail.organisation.domain.Designation;

/**
 * Default annual entitlement per designation (half-day units and SGD budget). Only used to prefill the
 * administrator's form and the sample data; each employee's training account is the source of truth.
 */
@ConfigurationProperties("uptrail.entitlement-defaults")
public record EntitlementDefaults(Allowance administrative, Allowance professional, Allowance management) {

    public record Allowance(int units, BigDecimal budget) {
    }

    public Allowance forDesignation(Designation designation) {
        return switch (designation) {
            case ADMINISTRATIVE -> administrative;
            case PROFESSIONAL -> professional;
            case MANAGEMENT -> management;
        };
    }
}
