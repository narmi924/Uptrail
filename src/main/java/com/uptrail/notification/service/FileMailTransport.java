package com.uptrail.notification.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.uptrail.shared.time.BusinessClock;

/**
 * Writes each email as a text file into the capture directory instead of sending it. Useful without any
 * SMTP server; nothing leaves the machine.
 */
@Component
@ConditionalOnProperty(name = "uptrail.mail.transport", havingValue = "file")
public class FileMailTransport implements MailTransport {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final Path directory;
    private final String from;
    private final BusinessClock clock;

    public FileMailTransport(@Value("${uptrail.mail.capture-dir}") String directory,
            @Value("${uptrail.mail.from}") String from, BusinessClock clock) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.from = from;
        this.clock = clock;
    }

    @Override
    public void send(OutgoingMail mail) throws IOException {
        Files.createDirectories(directory);
        String content = "From: " + from + "\nTo: " + mail.to() + "\nSubject: " + mail.subject() + "\n\n" + mail.body();
        Path file = directory.resolve(STAMP.format(clock.now()) + "-" + mail.outboxId() + ".txt");
        Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public String name() {
        return "file";
    }
}
