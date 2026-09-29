package com.uptrail.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.uptrail.notification.domain.MailTemplate;
import com.uptrail.notification.service.FileMailTransport;
import com.uptrail.notification.service.MailRenderer;
import com.uptrail.notification.service.MailRenderer.RenderedMail;
import com.uptrail.notification.service.MailTransport.OutgoingMail;
import com.uptrail.shared.time.BusinessClock;

/**
 * Every notification renders to a one-line subject and a plain-text body with the sign-in deep link, and
 * the file transport writes a readable message.
 */
class MailRenderingTest {

    private static final MailRenderer RENDERER = new MailRenderer();

    private static Map<String, String> values() {
        Map<String, String> values = new HashMap<>();
        values.put("recipientName", "Siti Rahman");
        values.put("reference", "UPT-2026-000001");
        values.put("applicantName", "Siti Rahman");
        values.put("claimantName", "Siti Rahman");
        values.put("courseTitle", "Spring Application Development");
        values.put("period", "12 Oct 2026 to 13 Oct 2026");
        values.put("status", "Approved");
        values.put("amount", "SGD 600.00");
        values.put("revision", "1");
        values.put("reimbursementReference", "SIM-20261016-000001");
        values.put("link", "http://localhost:8080/login?next=/employee/applications/1");
        return values;
    }

    @ParameterizedTest
    @EnumSource(MailTemplate.class)
    void everyTemplateRendersWithASubjectAndTheDeepLink(MailTemplate template) {
        RenderedMail mail = RENDERER.render(template, values());

        assertThat(mail.subject()).isNotBlank().doesNotContain("\n").contains("UPT-2026-000001");
        assertThat(mail.body()).contains("Hello Siti Rahman,")
                .contains("http://localhost:8080/login?next=/employee/applications/1")
                .doesNotContain("null").doesNotContain("[(").doesNotContain("Subject:");
    }

    @Test
    void theManagersReasonIsIncludedOnlyWhenGiven() {
        Map<String, String> values = values();
        values.put("reason", "Directly relevant to the platform migration.");

        assertThat(RENDERER.render(MailTemplate.APPLICATION_APPROVED, values).body())
                .contains("Reason given by your manager:").contains("Directly relevant to the platform migration.");
        assertThat(RENDERER.render(MailTemplate.APPLICATION_APPROVED, values()).body())
                .doesNotContain("Reason given by your manager:");
    }

    @Test
    void textIsNotHtmlEscaped() {
        Map<String, String> values = values();
        values.put("courseTitle", "Q&A <Advanced> \"Spring\"");

        assertThat(RENDERER.render(MailTemplate.CLAIM_SUBMITTED, values).body())
                .contains("Q&A <Advanced> \"Spring\"");
    }

    @Test
    void theFileTransportWritesOneTextFilePerMessage(@TempDir Path directory) throws IOException {
        BusinessClock clock = new BusinessClock(Clock.fixed(Instant.parse("2026-10-05T01:00:00Z"), ZoneOffset.UTC));
        FileMailTransport transport = new FileMailTransport(directory.toString(), "Uptrail <no-reply@example.com>", clock);

        transport.send(new OutgoingMail(42L, "siti@example.com", "Subject line", "Body text\n"));

        Path file = directory.resolve("20261005-010000-42.txt");
        assertThat(Files.readString(file, StandardCharsets.UTF_8))
                .startsWith("From: Uptrail <no-reply@example.com>\nTo: siti@example.com\nSubject: Subject line\n\nBody text");
    }
}
