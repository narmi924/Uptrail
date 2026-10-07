package com.uptrail.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.model.AggregateType;
import com.uptrail.model.TrainingEntitlement;
import com.uptrail.repo.TrainingEntitlementRepo;
import com.uptrail.service.EntitlementService.Balance;
import com.uptrail.model.User;
import com.uptrail.model.Designation;
import com.uptrail.repo.UserRepo;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.shared.time.BusinessClock;
import com.uptrail.shared.tx.WriteTransaction;
import com.uptrail.shared.web.Formats;

/**
 * Annual training-day entitlement and budget per employee, for the current and the next year. A change may
 * never go below what is already reserved or approved; every change needs a reason and is audited.
 */
@Service
public class EntitlementAdminService {

    public record AccountRow(Long employeeId, String staffId, String name, String department,
            Designation designation, Balance balance) {
    }

    private final UserRepo employees;
    private final TrainingEntitlementRepo accounts;
    private final EntitlementService entitlements;
    private final EntitlementDefaults defaults;
    private final AuditService audit;
    private final BusinessClock clock;
    private final Formats formats;

    public EntitlementAdminService(UserRepo employees, TrainingEntitlementRepo accounts,
            EntitlementService entitlements, EntitlementDefaults defaults, AuditService audit, BusinessClock clock,
            Formats formats) {
        this.employees = employees;
        this.accounts = accounts;
        this.entitlements = entitlements;
        this.defaults = defaults;
        this.audit = audit;
        this.clock = clock;
        this.formats = formats;
    }

    public List<Integer> editableYears() {
        int year = clock.currentYear();
        return List.of(year, year + 1);
    }

    @Transactional(readOnly = true)
    public Page<AccountRow> list(int year, String query, Pageable pageable) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return employees.findActiveApplicants(q, pageable).map(e -> new AccountRow(e.getId(), e.getStaffId(),
                e.getName(), e.getDepartment(), e.getDesignation(), entitlements.balance(e.getId(), year)));
    }

    public EntitlementDefaults.Allowance defaultsFor(Designation designation) {
        return defaults.forDesignation(designation);
    }

    /**
     * Creates or changes an account. {@code days} is in steps of half a day; the lower bound is what the
     * employee already has reserved or approved in that year.
     */
    @WriteTransaction
    public void save(User admin, Long employeeId, int year, BigDecimal days, BigDecimal budget, String reason) {
        requireEditableYear(year);
        AdminValidation v = new AdminValidation();
        Integer units = null;
        if (days == null || days.signum() < 0) {
            v.reject("days", "Enter the number of training days (0 or more).");
        } else if (days.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() > 0) {
            v.reject("days", "Use whole or half days, for example 7.5.");
        } else if (days.compareTo(BigDecimal.valueOf(260)) > 0) {
            v.reject("days", "A year has at most about 260 working days.");
        } else {
            units = days.multiply(BigDecimal.valueOf(2)).intValueExact();
        }
        BigDecimal amount = v.money("budget", budget, true);
        String why = v.required("reason", reason, 2000, "reason for this change");
        v.throwIfAny();

        User employee = employees.lockById(employeeId).orElseThrow(NotFoundException::new);
        Optional<TrainingEntitlement> existing = accounts.lockForYears(employeeId, List.of(year)).stream().findFirst();
        if (existing.isEmpty()) {
            accounts.save(TrainingEntitlement.open(employeeId, year, units, amount, clock.now()));
            audit.record(new AuditService.Change(AggregateType.ACCOUNT, employeeId + ":" + year, "ENTITLEMENT_CREATED",
                    admin.getUserId(), null, null, why, Map.of("employee", employee.getName(), "units", units,
                            "budget", amount)));
            return;
        }
        TrainingEntitlement account = existing.get();
        Balance balance = entitlements.balanceOf(account);
        if (units < balance.usedUnits()) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, formats.days(balance.usedUnits())
                    + " are already reserved or approved for " + employee.getName() + " in " + year
                    + "; the entitlement cannot be lower.", Map.of("days", "At least " + formats.days(balance.usedUnits())
                    + "."));
        }
        if (amount.compareTo(balance.usedAmount()) < 0) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, formats.money(balance.usedAmount())
                    + " is already reserved or approved for " + employee.getName() + " in " + year
                    + "; the budget cannot be lower.", Map.of("budget", "At least "
                    + formats.money(balance.usedAmount()) + "."));
        }
        Map<String, Object> before = Map.of("units", account.getEntitledUnits(), "budget", account.getBudgetAmount());
        account.reconfigure(units, amount, clock.now());
        audit.record(new AuditService.Change(AggregateType.ACCOUNT, employeeId + ":" + year, "ENTITLEMENT_CHANGED",
                admin.getUserId(), null, null, why, Map.of("before", before, "after",
                        Map.of("units", units, "budget", amount))));
    }

    /** Opens accounts with the designation defaults for every active applicant who has none for the year. */
    @WriteTransaction
    public int openMissing(User admin, int year) {
        requireEditableYear(year);
        int opened = 0;
        for (User employee : employees.findActiveApplicants("", Pageable.unpaged()).getContent()) {
            if (accounts.findByEmployeeIdAndCalendarYear(employee.getUserId(), year).isPresent()) {
                continue;
            }
            employees.lockById(employee.getUserId());
            if (accounts.findByEmployeeIdAndCalendarYear(employee.getUserId(), year).isPresent()) {
                continue;
            }
            EntitlementDefaults.Allowance allowance = defaults.forDesignation(employee.getDesignation());
            accounts.save(TrainingEntitlement.open(employee.getUserId(), year, allowance.units(), allowance.budget(),
                    clock.now()));
            audit.record(new AuditService.Change(AggregateType.ACCOUNT, employee.getUserId() + ":" + year,
                    "ENTITLEMENT_CREATED", admin.getUserId(), null, null,
                    "Opened with the defaults for " + employee.getDesignation().label() + " staff",
                    Map.of("units", allowance.units(), "budget", allowance.budget())));
            opened++;
        }
        return opened;
    }

    private void requireEditableYear(int year) {
        if (!editableYears().contains(year)) {
            throw new BusinessException(ErrorCode.YEAR_NOT_OPEN, "Entitlements can be set for "
                    + editableYears().get(0) + " and " + editableYears().get(1) + " only.");
        }
    }
}
