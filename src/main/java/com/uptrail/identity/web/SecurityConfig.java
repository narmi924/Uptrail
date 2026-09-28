package com.uptrail.identity.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Engineering baseline: only static assets, error pages and the health endpoint are reachable; every
 * other request is denied until the identity milestone adds the two sign-in entry points.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain baselineChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/img/**", "/webjars/**", "/error", "/actuator/health")
                .permitAll()
                .anyRequest().denyAll());
        return http.build();
    }
}
