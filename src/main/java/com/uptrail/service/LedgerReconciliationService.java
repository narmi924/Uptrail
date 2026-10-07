package com.uptrail.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.model.ApplicationStatus;
import com.uptrail.model.CourseApplication;
import com.uptrail.repo.CourseApplicationRepo;
import com.uptrail.model.ClaimStatus;
import com.uptrail.model.CourseFeeApplication;
import com.uptrail.repo.CourseFeeApplicationRepo;
import com.uptrail.model.TrainingEntitlement;
import com.uptrail.repo.TrainingEntitlementRepo;

/**
 * Read-only consistency check: recomputes what every annual account should hold from the applications and
 * claims themselves and compares it with the ledger sums. Any difference indicates a defect; nothing is
 * corrected automatically.
 */
@Service
@Transactional(readOnly = true)
public class LedgerReconciliationService {

    public record Figures(int reservedUnits, int committedUnits, BigDecimal reservedAmount, BigDecimal committedAmount,
            BigDecimal reimbursedAmount) {
    }

    public record Mismatch(Long accountId, Long employeeId, int year, Figures expected, Figures actual) {
    }

    public record Result(int accountsChecked, List<Mismatch> mismatches) {

        public boolean consistent() {
            return mismatches.isEmpty();
        }
    }

    private final TrainingEntitlementRepo accounts;
    private final CourseApplicationRepo applications;
    private final CourseFeeApplicationRepo claims;
    private final EntitlementService entitlements;

    public LedgerReconciliationService(TrainingEntitlementRepo accounts, CourseApplicationRepo applications,
            CourseFeeApplicationRepo claims, EntitlementService entitlements) {
        this.accounts = accounts;
        this.applications = applications;
        this.claims = claims;
        this.entitlements = entitlements;
    }

    public Result check() {
        Map<String, int[]> units = new HashMap<>();
        Map<String, BigDecimal[]> amounts = new HashMap<>();
        Map<Long, CourseApplication> byId = new HashMap<>();
        for (CourseApplication application : applications.findAll()) {
            byId.put(application.getId(), application);
            ApplicationStatus status = application.getStatus();
            boolean reserved = status.isPending();
            boolean committed = status == ApplicationStatus.APPROVED || status == ApplicationStatus.COMPLETED;
            if (!reserved && !committed) {
                continue;
            }
            int column = reserved ? 0 : 1;
            application.unitsByYear().forEach((year, value) ->
                    units.computeIfAbsent(key(application.getApplicantId(), year), k -> new int[2])[column] += value);
            BigDecimal[] money = amounts.computeIfAbsent(key(application.getApplicantId(),
                    application.getStartDate().getYear()), k -> zeros());
            money[column] = money[column].add(application.getCourseFee());
        }
        for (CourseFeeApplication claim : claims.findAll()) {
            if (claim.getStatus() == ClaimStatus.REIMBURSED) {
                CourseApplication application = byId.get(claim.getApplicationId());
                BigDecimal[] money = amounts.computeIfAbsent(key(application.getApplicantId(),
                        application.getStartDate().getYear()), k -> zeros());
                money[2] = money[2].add(claim.getAmount());
            }
        }

        List<Mismatch> mismatches = new ArrayList<>();
        List<TrainingEntitlement> all = accounts.findAll();
        for (TrainingEntitlement account : all) {
            String key = key(account.getEmployeeId(), account.getCalendarYear());
            int[] u = units.getOrDefault(key, new int[2]);
            BigDecimal[] m = amounts.getOrDefault(key, zeros());
            Figures expected = new Figures(u[0], u[1], m[0], m[1], m[2]);
            EntitlementService.Balance balance = entitlements.balanceOf(account);
            Figures actual = new Figures(balance.reservedUnits(), balance.committedUnits(), balance.reservedAmount(),
                    balance.committedAmount(), balance.reimbursedAmount());
            if (!same(expected, actual)) {
                mismatches.add(new Mismatch(account.getId(), account.getEmployeeId(), account.getCalendarYear(),
                        expected, actual));
            }
        }
        return new Result(all.size(), mismatches);
    }

    private static boolean same(Figures a, Figures b) {
        return a.reservedUnits() == b.reservedUnits() && a.committedUnits() == b.committedUnits()
                && a.reservedAmount().compareTo(b.reservedAmount()) == 0
                && a.committedAmount().compareTo(b.committedAmount()) == 0
                && a.reimbursedAmount().compareTo(b.reimbursedAmount()) == 0;
    }

    private static BigDecimal[] zeros() {
        return new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    }

    private static String key(Long employeeId, int year) {
        return employeeId + ":" + year;
    }
}
