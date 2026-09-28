package com.uptrail.entitlement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.entitlement.domain.LedgerDelta;
import com.uptrail.entitlement.domain.LedgerEntry;
import com.uptrail.entitlement.domain.LedgerEntryType;
import com.uptrail.entitlement.domain.TrainingAccount;
import com.uptrail.entitlement.repository.LedgerEntryRepository;
import com.uptrail.entitlement.repository.TrainingAccountRepository;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;
import com.uptrail.shared.time.BusinessClock;

/**
 * Annual accounts and their ledger. Balances are always ledger sums:
 * available = entitlement - pending reservations - approved commitments. Reimbursements are tracked in a
 * separate column and never reduce the available budget a second time.
 */
@Service
@Transactional(readOnly = true)
public class EntitlementService {

    public record Balance(int year, Long accountId, boolean configured, int entitledUnits, BigDecimal budget,
            int reservedUnits, int committedUnits, BigDecimal reservedAmount, BigDecimal committedAmount,
            BigDecimal reimbursedAmount) {

        public static Balance notConfigured(int year) {
            return new Balance(year, null, false, 0, BigDecimal.ZERO.setScale(2), 0, 0, BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
        }

        public int availableUnits() {
            return entitledUnits - reservedUnits - committedUnits;
        }

        public BigDecimal availableBudget() {
            return budget.subtract(reservedAmount).subtract(committedAmount);
        }

        public int usedUnits() {
            return reservedUnits + committedUnits;
        }

        public BigDecimal usedAmount() {
            return reservedAmount.add(committedAmount);
        }
    }

    private final TrainingAccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final BusinessClock clock;

    public EntitlementService(TrainingAccountRepository accounts, LedgerEntryRepository ledger, BusinessClock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.clock = clock;
    }

    public Balance balance(Long employeeId, int year) {
        return accounts.findByEmployeeIdAndCalendarYear(employeeId, year).map(this::balanceOf)
                .orElseGet(() -> Balance.notConfigured(year));
    }

    public Balance balanceOf(TrainingAccount account) {
        LedgerEntryRepository.Totals totals = ledger.totalsForAccount(account.getId());
        return new Balance(account.getCalendarYear(), account.getId(), true, account.getEntitledUnits(),
                account.getBudgetAmount(), intOf(totals.getReservedUnits()), intOf(totals.getCommittedUnits()),
                moneyOf(totals.getReservedAmount()), moneyOf(totals.getCommittedAmount()),
                moneyOf(totals.getReimbursedAmount()));
    }

    public Optional<TrainingAccount> account(Long employeeId, int year) {
        return accounts.findByEmployeeIdAndCalendarYear(employeeId, year);
    }

    public List<LedgerEntry> entries(Long accountId) {
        return ledger.findByAccountIdOrderByCreatedAtDescIdDesc(accountId);
    }

    public List<LedgerEntry> entriesForApplication(Long applicationId) {
        return ledger.findByApplicationIdOrderByIdAsc(applicationId);
    }

    /**
     * Locks the employee's accounts for the given years in ascending year order. Must be called after the
     * employee row is locked. Missing accounts are an error: a missing year never means unlimited budget.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Integer, TrainingAccount> lockAccounts(Long employeeId, Collection<Integer> years) {
        TreeSet<Integer> wanted = new TreeSet<>(years);
        Map<Integer, TrainingAccount> locked = new LinkedHashMap<>();
        for (TrainingAccount account : accounts.lockForYears(employeeId, wanted)) {
            locked.put(account.getCalendarYear(), account);
        }
        List<Integer> missing = wanted.stream().filter(year -> !locked.containsKey(year)).toList();
        if (!missing.isEmpty()) {
            throw missingAccount(missing);
        }
        return locked;
    }

    public static BusinessException missingAccount(Collection<Integer> years) {
        String list = years.stream().sorted().map(String::valueOf).collect(Collectors.joining(" and "));
        return new BusinessException(ErrorCode.MISSING_ANNUAL_ACCOUNT, "No training entitlement is configured for "
                + list + ". Ask an administrator to set it up before applying.");
    }

    /** What one application currently holds on one account (its reservation or commitment). */
    public LedgerDelta contribution(Long accountId, Long applicationId) {
        LedgerEntryRepository.Totals totals = ledger.totalsForApplication(accountId, applicationId);
        return new LedgerDelta(intOf(totals.getReservedUnits()), intOf(totals.getCommittedUnits()),
                moneyOf(totals.getReservedAmount()), moneyOf(totals.getCommittedAmount()),
                moneyOf(totals.getReimbursedAmount()));
    }

    /** Posts one net ledger change; zero changes are not written. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void post(Long eventId, TrainingAccount account, Long applicationId, Long claimId, LedgerEntryType type,
            LedgerDelta delta) {
        if (delta.isZero()) {
            return;
        }
        ledger.save(LedgerEntry.of(account.getId(), eventId, applicationId, claimId, type, delta, clock.now()));
    }

    private static int intOf(Number value) {
        return value == null ? 0 : value.intValue();
    }

    private static BigDecimal moneyOf(Number value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal decimal = value instanceof BigDecimal big ? big : new BigDecimal(value.toString());
        return decimal.setScale(2, RoundingMode.UNNECESSARY);
    }
}
