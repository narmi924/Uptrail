package com.uptrail.entitlement.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.application.domain.CourseApplication;
import com.uptrail.application.repository.CourseApplicationRepository;
import com.uptrail.entitlement.domain.LedgerEntry;
import com.uptrail.entitlement.domain.LedgerEntryType;
import com.uptrail.entitlement.domain.TrainingAccount;
import com.uptrail.entitlement.service.EntitlementService.Balance;
import com.uptrail.identity.domain.Actor;
import com.uptrail.shared.time.BusinessClock;

/**
 * The employee's own entitlement page: the balance of one year and every ledger movement behind it, so that
 * each change of days or budget can be traced to the application or claim that caused it.
 */
@Service
@Transactional(readOnly = true)
public class EntitlementQueryService {

    public record LedgerRow(Instant at, LedgerEntryType type, Long applicationId, String applicationReference,
            String courseTitle, Long claimId, int reservedUnits, int committedUnits, BigDecimal reservedAmount,
            BigDecimal committedAmount, BigDecimal reimbursedAmount) {
    }

    public record Overview(int year, List<Integer> years, Balance balance, int completedUnits, List<LedgerRow> ledger) {
    }

    private final EntitlementService entitlements;
    private final CourseApplicationRepository applications;
    private final BusinessClock clock;

    public EntitlementQueryService(EntitlementService entitlements, CourseApplicationRepository applications,
            BusinessClock clock) {
        this.entitlements = entitlements;
        this.applications = applications;
        this.clock = clock;
    }

    /** Previous, current and next year; any other requested year falls back to the current one. */
    public Overview overview(Actor actor, Integer requestedYear) {
        int current = clock.currentYear();
        List<Integer> years = List.of(current - 1, current, current + 1);
        int year = requestedYear != null && years.contains(requestedYear) ? requestedYear : current;
        Long employeeId = actor.employeeId();
        Balance balance = entitlements.balance(employeeId, year);
        Number completed = applications.sumCompletedUnits(employeeId, LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31));
        List<LedgerRow> rows = new ArrayList<>();
        TrainingAccount account = entitlements.account(employeeId, year).orElse(null);
        if (account != null) {
            List<LedgerEntry> entries = entitlements.entries(account.getId());
            Map<Long, CourseApplication> byId = new HashMap<>();
            applications.findAllById(entries.stream().map(LedgerEntry::getApplicationId).filter(Objects::nonNull)
                    .distinct().toList()).forEach(a -> byId.put(a.getId(), a));
            for (LedgerEntry e : entries) {
                CourseApplication application = e.getApplicationId() == null ? null : byId.get(e.getApplicationId());
                rows.add(new LedgerRow(e.getCreatedAt(), e.getEntryType(), e.getApplicationId(),
                        application == null ? null : application.getReferenceNo(),
                        application == null ? null : application.getCourseTitle(), e.getClaimId(),
                        e.getReservedUnitsDelta(), e.getCommittedUnitsDelta(), e.getReservedAmountDelta(),
                        e.getCommittedAmountDelta(), e.getReimbursedAmountDelta()));
            }
        }
        return new Overview(year, years, balance, completed == null ? 0 : completed.intValue(), rows);
    }
}
