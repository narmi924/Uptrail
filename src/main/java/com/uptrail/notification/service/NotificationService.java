package com.uptrail.notification.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.notification.domain.MailTemplate;
import com.uptrail.notification.domain.OutboxMessage;
import com.uptrail.notification.repository.OutboxMessageRepository;
import com.uptrail.organisation.domain.Employee;
import com.uptrail.organisation.repository.EmployeeRepository;
import com.uptrail.shared.time.BusinessClock;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes email notifications to the outbox in the caller's business transaction. Nothing is sent here;
 * the outbox worker delivers after commit, so an SMTP problem can never undo a business change.
 */
@Service
public class NotificationService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final OutboxMessageRepository outbox;
    private final EmployeeRepository employees;
    private final BusinessClock clock;

    public NotificationService(OutboxMessageRepository outbox, EmployeeRepository employees, BusinessClock clock) {
        this.outbox = outbox;
        this.employees = employees;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Long eventId, Long recipientEmployeeId, MailTemplate template, Map<String, ?> payload) {
        Employee recipient = employees.findById(recipientEmployeeId).orElseThrow();
        Map<String, String> values = new LinkedHashMap<>();
        payload.forEach((key, value) -> values.put(key, value == null ? null : String.valueOf(value)));
        values.put("recipientName", recipient.getFullName());
        outbox.save(OutboxMessage.pending(eventId, recipientEmployeeId, recipient.getEmail(), template,
                JSON.writeValueAsString(values), clock.now()));
    }
}
