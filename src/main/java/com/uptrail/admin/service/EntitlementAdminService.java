package com.uptrail.admin.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.audit.domain.AggregateType;
import com.uptrail.audit.service.AuditService;
import com.uptrail.entitlement.domain.TrainingAccount;
import com.uptrail.entitlement.repository.TrainingAccountRepository;
import com.uptrail.entitlement.service.EntitlementDefaults;
import com.uptrail.entitlement.service.EntitlementService;
import com.uptrail.entitlement.service.EntitlementService.Balance;
import com.uptrail.identity.domain.Actor;
import com.uptrail.organisation.domain.Designation;
import com.uptrail.organisation.domain.Employee;
import com.uptrail.organisation.repository.EmployeeRepository;
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

    public record AccountRow(Long employeeId, String staffNo, String fullName, String department,
            Designation designation, Balance balance) {
    }

    private final EmployeeRepository employees;
    private final TrainingAccountRepository accounts;
    private final EntitlementService entitlements;
    private final EntitlementDefaults defaults;
    private final AuditService audit;
    private final BusinessClock clock;
    private final Formats formats;

    public EntitlementAdminService(EmployeeRepository employees, TrainingAccountRepository accounts,
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
        return employees.findActiveApplicants(q, pageable).map(e -> new AccountRow(e.getId(), e.getStaffNo(),
                e.getFullName(), e.getDepartment(), e.getDesignation(), entitlements.balance(e.getId(), year)));
    }

    public EntitlementDefaults.Allowance defaultsFor(Designation designation) {
        return defaults.forDesignation(designation);
    }

    /**
     * Creates or changes an account. {@code days} is in steps of half a day; the lower bound is what the
     * employee already has reserved or approved in that year.
     */
    @WriteTransaction
    public void save(Actor admin, Long employeeId, int year, BigDecimal days, BigDecimal budget, String reason) {
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

        Employee employee = employees.lockById(employeeId).orElseThrow(NotFoundException::new);
        Optional<TrainingAccount> existing = accounts.lockForYears(employeeId, List.of(year)).stream().findFirst();
        if (existing.isEmpty()) {
            accounts.save(TrainingAccount.open(employeeId, year, units, amount, clock.now()));
            audit.record(new AuditService.Change(AggregateType.ACCOUNT, employeeId + ":" + year, "ENTITLEMENT_CREATED",
                    admin.employeeId(), null, null, why, Map.of("employee", employee.getFullName(), "units", units,
                            "budget", amount)));
            return;
        }
        TrainingAccount account = existing.get();
        Balance balance = entitlements.balanceOf(account);
        if (units < balance.usedUnits()) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, formats.days(balance.usedUnits())
                    + " are already reserved or approved for " + employee.getFullName() + " in " + year
                    + "; the entitlement cannot be lower.", Map.of("days", "At least " + formats.days(balance.usedUnits())
                    + "."));
        }
        if (amount.compareTo(balance.usedAmount()) < 0) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, formats.money(balance.usedAmount())
                    + " is already reserved or approved for " + employee.getFullName() + " in " + year
                    + "; the budget cannot be lower.", Map.of("budget", "At least "
                    + formats.money(balance.usedAmount()) + "."));
        }
        Map<String, Object> before = Map.of("units", account.getEntitledUnits(), "budget", account.getBudgetAmount());
        account.reconfigure(units, amount, clock.now());
        audit.record(new AuditService.Change(AggregateType.ACCOUNT, employeeId + ":" + year, "ENTITLEMENT_CHANGED",
                admin.employeeId(), null, null, why, Map.of("before", before, "after",
                        Map.of("units", units, "budget", amount))));
    }

    /** Opens accounts with the designation defaults for every active applicant who has none for the year. */
    @WriteTransaction
    public int openMissing(Actor admin, int year) {
        requireEditableYear(year);
        int opened = 0;
        for (Employee employee : employees.findActiveApplicants("", Pageable.unpaged()).getContent()) {
            if (accounts.findByEmployeeIdAndCalendarYear(employee.getId(), year).isPresent()) {
                continue;
            }
            employees.lockById(employee.getId());
            if (accounts.findByEmployeeIdAndCalendarYear(employee.getId(), year).isPresent()) {
                continue;
            }
            EntitlementDefaults.Allowance allowance = defaults.forDesignation(employee.getDesignation());
            accounts.save(TrainingAccount.open(employee.getId(), year, allowance.units(), allowance.budget(),
                    clock.now()));
            audit.record(new AuditService.Change(AggregateType.ACCOUNT, employee.getId() + ":" + year,
                    "ENTITLEMENT_CREATED", admin.employeeId(), null, null,
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
