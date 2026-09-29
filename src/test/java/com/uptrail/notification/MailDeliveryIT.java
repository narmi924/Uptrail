package com.uptrail.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import com.uptrail.application.AbstractApplicationIT;
import com.uptrail.notification.service.OutboxProcessor;
import com.uptrail.support.Fixtures;

/**
 * Outbox delivery against a local GreenMail SMTP server: mail goes out after the business commit with a
 * sign-in deep link, an unavailable SMTP server never blocks the business change, failed sends are retried
 * with backoff and end as FAILED after the attempt limit, and concurrent workers send a message once.
 */
class MailDeliveryIT extends AbstractApplicationIT {

    private static final int SMTP_PORT = freePort();
    private static GreenMail smtp;

    @Autowired
    private OutboxProcessor processor;

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> SMTP_PORT);
        registry.add("uptrail.mail.transport", () -> "smtp");
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void startSmtp() {
        smtp = new GreenMail(new ServerSetup(SMTP_PORT, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        smtp.start();
    }

    private static void stopSmtp() {
        if (smtp != null) {
            smtp.stop();
            smtp = null;
        }
    }

    @BeforeEach
    void smtpUp() {
        stopSmtp();
        startSmtp();
    }

    @AfterEach
    void smtpDown() {
        stopSmtp();
    }

    @AfterAll
    static void cleanUp() {
        stopSmtp();
    }

    private String outboxStatus() {
        return jdbc.queryForObject("SELECT status FROM email_outbox", String.class);
    }

    private Long submitOne() {
        return submit(employee, external(MON_12_OCT, MON_12_OCT, "600.00")).applicationId();
    }

    @Test
    void mailIsSentAfterTheBusinessChangeWithASignInDeepLink() throws Exception {
        Long applicationId = submitOne();
        assertThat(outboxStatus()).isEqualTo("PENDING");
        assertThat(smtp.getReceivedMessages()).isEmpty();

        assertThat(processor.processBatch()).isEqualTo(1);

        MimeMessage[] received = smtp.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("mgr@example.com");
        assertThat(received[0].getSubject()).contains("waiting for your decision");
        String body = GreenMailUtil.getBody(received[0]);
        String path = "/manager/applications/" + applicationId;
        assertThat(body).contains("http://localhost:8080/login?next=" + path);
        assertThat(outboxStatus()).isEqualTo("SENT");
        assertThat(count("SELECT COUNT(*) FROM email_outbox WHERE sent_at IS NOT NULL AND attempts = 1")).isEqualTo(1);

        Matcher next = Pattern.compile("login\\?next=(\\S+)").matcher(body);
        assertThat(next.find()).isTrue();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/login")
                        .param("username", manager.username()).param("password", Fixtures.PASSWORD)
                        .param("next", next.group(1))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .csrf()))
                .andExpect(redirectedUrl(path));
    }

    @Test
    void anUnavailableSmtpServerDoesNotBlockTheBusinessChangeAndTheMailIsRetried() {
        stopSmtp();

        Long applicationId = submitOne();
        assertThat(statusOf(applicationId)).isEqualTo("APPLIED");

        processor.processBatch();
        assertThat(outboxStatus()).isEqualTo("PENDING");
        assertThat(count("SELECT COUNT(*) FROM email_outbox WHERE attempts = 1 AND last_error IS NOT NULL")).isEqualTo(1);
        assertThat(processor.processBatch()).as("not due before the backoff delay").isZero();

        startSmtp();
        clock.advance(Duration.ofMinutes(2));
        assertThat(processor.processBatch()).isEqualTo(1);

        assertThat(outboxStatus()).isEqualTo("SENT");
        assertThat(smtp.getReceivedMessages()).hasSize(1);
        assertThat(count("SELECT COUNT(*) FROM email_outbox WHERE attempts = 2 AND last_error IS NULL")).isEqualTo(1);
    }

    @Test
    void afterTheAttemptLimitTheMailIsFailedUntilAnAdministratorQueuesItAgain() throws Exception {
        stopSmtp();
        submitOne();
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(processor.processBatch()).isEqualTo(1);
            clock.advance(Duration.ofHours(1).plusMinutes(1));
        }
        assertThat(outboxStatus()).isEqualTo("FAILED");
        assertThat(processor.processBatch()).isZero();

        Long outboxId = jdbc.queryForObject("SELECT id FROM email_outbox", Long.class);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/admin/outbox/{id}/retry", outboxId)
                        .session(adminSession()).with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(redirectedUrl("/admin/operations?tab=outbox&status=FAILED"));
        assertThat(outboxStatus()).isEqualTo("PENDING");
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE aggregate_type = 'OUTBOX' "
                + "AND event_type = 'RETRY_REQUESTED'")).isEqualTo(1);

        startSmtp();
        assertThat(processor.processBatch()).isEqualTo(1);
        assertThat(outboxStatus()).isEqualTo("SENT");
    }

    @Test
    void aMessageWhoseWorkerStoppedIsSentAgainAfterItsLeaseExpires() {
        submitOne();
        // Same time base as the application's own writes (UTC); avoids JVM time-zone conversions in the test.
        jdbc.update("UPDATE email_outbox SET status = 'SENDING', attempts = 1, "
                + "lease_until = next_attempt_at - INTERVAL 2 MINUTE");
        clock.advance(Duration.ofSeconds(1));

        assertThat(processor.processBatch()).isEqualTo(1);

        assertThat(outboxStatus()).isEqualTo("SENT");
        assertThat(smtp.getReceivedMessages()).hasSize(1);
        assertThat(count("SELECT COUNT(*) FROM email_outbox WHERE attempts = 2")).isEqualTo(1);
    }

    @Test
    void concurrentWorkersSendEachMessageOnce() throws Exception {
        submitOne();
        submit(employee, external(MON_12_OCT.plusDays(7), MON_12_OCT.plusDays(7), "100.00"));
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return processor.processBatch();
                }));
            }
            int total = 0;
            for (Future<Integer> future : futures) {
                total += future.get(60, TimeUnit.SECONDS);
            }
            assertThat(total).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }

        assertThat(smtp.getReceivedMessages()).hasSize(2);
        assertThat(count("SELECT COUNT(*) FROM email_outbox WHERE status = 'SENT' AND attempts = 1")).isEqualTo(2);
    }

    private org.springframework.mock.web.MockHttpSession adminSession() throws Exception {
        return (org.springframework.mock.web.MockHttpSession) mvc.perform(formLogin("/admin/login")
                .user(admin.username()).password(Fixtures.PASSWORD)).andReturn().getRequest().getSession(false);
    }
}
