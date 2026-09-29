package com.uptrail.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends through the SMTP server configured with {@code spring.mail.*} (Mailpit on port 1025 locally).
 * Connection and read timeouts are short so that an unavailable server does not block the worker.
 */
@Component
@ConditionalOnProperty(name = "uptrail.mail.transport", havingValue = "smtp", matchIfMissing = true)
public class SmtpMailTransport implements MailTransport {

    private final JavaMailSender sender;
    private final String from;

    public SmtpMailTransport(JavaMailSender sender, @Value("${uptrail.mail.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(OutgoingMail mail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(mail.to());
        message.setSubject(mail.subject());
        message.setText(mail.body());
        sender.send(message);
    }

    @Override
    public String name() {
        return "smtp";
    }
}
