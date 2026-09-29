package com.uptrail.identity.web;

import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority;
import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasRole;
import static org.springframework.security.authorization.AuthorizationManagers.allOf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.uptrail.identity.service.UptrailUserDetailsService;

/**
 * Two filter chains, one per entry point. Both use the same database accounts; a session only grants the
 * workspace it signed in to (authority ENTRY_STAFF or ENTRY_ADMIN) plus the shared training calendar.
 * CSRF protection stays enabled everywhere and every state change is a POST.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; img-src 'self' data:; "
            + "object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'";

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    @Order(1)
    SecurityFilterChain adminChain(HttpSecurity http, UptrailUserDetailsService users, PasswordEncoder encoder,
            SessionRegistry sessionRegistry) throws Exception {
        http.securityMatcher("/admin/**")
                .authenticationManager(manager(users, encoder, EntryPoint.ADMIN))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login").permitAll()
                        .anyRequest().access(allOf(hasAuthority(EntryPoint.ADMIN.authority()), hasRole("ADMIN"))))
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .successHandler(new EntrySuccessHandler(EntryPoint.ADMIN))
                        .failureUrl("/admin/login?error"))
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login?logout"))
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl("/admin/login?expired"));
        commonHeaders(http);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain staffChain(HttpSecurity http, UptrailUserDetailsService users, PasswordEncoder encoder,
            SessionRegistry sessionRegistry) throws Exception {
        RequestMatcher api = PathPatternRequestMatcher.pathPattern("/api/**");
        AuthorizationManager<RequestAuthorizationContext> staff = hasAuthority(EntryPoint.STAFF.authority());
        http.authenticationManager(manager(users, encoder, EntryPoint.STAFF))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/error", "/actuator/health", "/actuator/health/liveness",
                                "/actuator/health/readiness", "/css/**", "/js/**",
                                "/img/**", "/fonts/**", "/webjars/**", "/favicon.ico").permitAll()
                        // The training calendar is available to every signed-in user, in either workspace.
                        .requestMatchers("/calendar", "/api/v1/calendar").authenticated()
                        .requestMatchers("/manager/**").access(allOf(staff, hasRole("MANAGER")))
                        .requestMatchers("/employee/**", "/claims/**", "/api/v1/applications/**",
                                "/api/v1/catalogue/**").access(allOf(staff, hasRole("EMPLOYEE")))
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(new EntrySuccessHandler(EntryPoint.STAFF))
                        .failureUrl("/login?error"))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout"))
                .exceptionHandling(errors -> errors
                        .defaultAuthenticationEntryPointFor(ApiSecurityResponses.unauthenticated(), api)
                        .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),
                                AnyRequestMatcher.INSTANCE)
                        .defaultAccessDeniedHandlerFor(ApiSecurityResponses.denied(), api)
                        // With a single mapping Spring Security would use the JSON handler for every request;
                        // pages get the standard handler, which shows the 403 error page.
                        .defaultAccessDeniedHandlerFor(new AccessDeniedHandlerImpl(), AnyRequestMatcher.INSTANCE))
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl("/login?expired"));
        commonHeaders(http);
        return http.build();
    }

    private static ProviderManager manager(UptrailUserDetailsService users, PasswordEncoder encoder,
            EntryPoint entryPoint) {
        DaoAuthenticationProvider passwordCheck = new DaoAuthenticationProvider(users);
        passwordCheck.setPasswordEncoder(encoder);
        return new ProviderManager(new EntryAuthenticationProvider(passwordCheck, entryPoint));
    }

    private static void commonHeaders(HttpSecurity http) throws Exception {
        http.headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));
    }
}
