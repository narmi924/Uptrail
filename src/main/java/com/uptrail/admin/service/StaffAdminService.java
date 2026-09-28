package com.uptrail.admin.service;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.uptrail.application.domain.ApplicationStatus;
import com.uptrail.application.repository.CourseApplicationRepository;
import com.uptrail.audit.domain.AggregateType;
import com.uptrail.audit.repository.AuditEventRepository;
import com.uptrail.audit.service.AuditService;
import com.uptrail.catalogue.repository.TrainingCalendarYearRepository;
import com.uptrail.claim.domain.ClaimStatus;
import com.uptrail.claim.repository.CourseClaimRepository;
import com.uptrail.entitlement.domain.TrainingAccount;
import com.uptrail.entitlement.repository.TrainingAccountRepository;
import com.uptrail.entitlement.service.EntitlementDefaults;
import com.uptrail.identity.domain.Actor;
import com.uptrail.identity.domain.Role;
import com.uptrail.identity.domain.UserAccount;
import com.uptrail.identity.repository.UserAccountRepository;
import com.uptrail.identity.service.SessionControlService;
import com.uptrail.notification.repository.OutboxMessageRepository;
import com.uptrail.organisation.domain.ApprovalAssignment;
import com.uptrail.organisation.domain.Designation;
import com.uptrail.organisation.domain.Employee;
import com.uptrail.organisation.repository.ApprovalAssignmentRepository;
import com.uptrail.organisation.repository.EmployeeRepository;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.shared.time.BusinessClock;
import com.uptrail.shared.tx.WriteTransaction;

/**
 * Staff and role administration. People with history are never physically deleted: they are deactivated,
 * which removes them from active lists, disables sign-in and ends their sessions. Changes that would
 * leave applications without an approver or the system without an administrator are refused.
 */
@Service
public class StaffAdminService {

    public record StaffRow(Long id, String staffNo, String fullName, String email, String department,
            Designation designation, boolean active, String username, Set<Role> roles, Long approverId,
            String approverName) {

        public String rolesLabel() {
            return String.join(", ", roles.stream().sorted().map(Role::label).toList());
        }
    }

    public record StaffDetail(StaffRow staff, long version, long directReports, long pendingApplicationsToDecide,
            long pendingClaimsToDecide, boolean hasHistory, boolean accountEnabled) {
    }

    public record NewStaff(String staffNo, String fullName, String email, String department, Designation designation,
            String username, String password, Set<Role> roles, Long approverId, boolean openCurrentYearAccount) {
    }

    public record Profile(String fullName, String email, String department, Designation designation) {
    }

    private final EmployeeRepository employees;
    private final UserAccountRepository accounts;
    private final ApprovalAssignmentRepository assignments;
    private final CourseApplicationRepository applications;
    private final CourseClaimRepository claims;
    private final TrainingAccountRepository trainingAccounts;
    private final AuditEventRepository auditEvents;
    private final OutboxMessageRepository outbox;
    private final TrainingCalendarYearRepository calendarYears;
    private final RoutingAdminService routing;
    private final EntitlementDefaults defaults;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;
    private final SessionControlService sessions;
    private final BusinessClock clock;

    public StaffAdminService(EmployeeRepository employees, UserAccountRepository accounts,
            ApprovalAssignmentRepository assignments, CourseApplicationRepository applications,
            CourseClaimRepository claims, TrainingAccountRepository trainingAccounts, AuditEventRepository auditEvents,
            OutboxMessageRepository outbox, TrainingCalendarYearRepository calendarYears, RoutingAdminService routing,
            EntitlementDefaults defaults, AuditService audit, PasswordEncoder passwordEncoder,
            SessionControlService sessions, BusinessClock clock) {
        this.employees = employees;
        this.accounts = accounts;
        this.assignments = assignments;
        this.applications = applications;
        this.claims = claims;
        this.trainingAccounts = trainingAccounts;
        this.auditEvents = auditEvents;
        this.outbox = outbox;
        this.calendarYears = calendarYears;
        this.routing = routing;
        this.defaults = defaults;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public Page<StaffRow> search(String query, Boolean active, Pageable pageable) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        Page<Employee> page = employees.search(q, active, pageable);
        Map<Long, UserAccount> byEmployee = new LinkedHashMap<>();
        accounts.findByEmployeeIdIn(page.getContent().stream().map(Employee::getId).toList())
                .forEach(a -> byEmployee.put(a.getEmployeeId(), a));
        return page.map(e -> row(e, byEmployee.get(e.getId())));
    }

    @Transactional(readOnly = true)
    public StaffDetail detail(Long employeeId) {
        Employee employee = employees.findById(employeeId).orElseThrow(NotFoundException::new);
        UserAccount account = accounts.findByEmployeeId(employeeId).orElse(null);
        return new StaffDetail(row(employee, account), employee.getVersion(), assignments.countByManagerId(employeeId),
                applications.countByApproverIdAndStatusIn(employeeId, ApplicationStatus.PENDING),
                claims.countByApproverIdAndStatus(employeeId, ClaimStatus.SUBMITTED), hasHistory(employeeId),
                account != null && account.isEnabled());
    }

    // ------------------------------------------------------------------ writes

    @WriteTransaction
    public Long create(Actor admin, NewStaff input) {
        AdminValidation v = new AdminValidation();
        String staffNo = v.required("staffNo", input.staffNo(), 30, "staff number");
        v.matches("staffNo", staffNo, AdminValidation.STAFF_NO, "Use letters, digits and hyphens only.");
        String fullName = v.required("fullName", input.fullName(), 120, "full name");
        String email = v.required("email", input.email(), 254, "email address");
        v.matches("email", email, AdminValidation.EMAIL, "Enter a valid email address.");
        String department = v.required("department", input.department(), 100, "department");
        if (input.designation() == null) {
            v.reject("designation", "Choose a designation.");
        }
        String username = v.required("username", input.username(), 80, "username");
        if (username != null) {
            username = UserAccount.normaliseUsername(username);
            v.matches("username", username, AdminValidation.USERNAME,
                    "Use 3 to 80 lower-case letters, digits, dots, hyphens or underscores.");
            if (accounts.existsByUsername(username)) {
                v.reject("username", "This username is already taken.");
            }
        }
        if (staffNo != null && employees.existsByStaffNo(staffNo)) {
            v.reject("staffNo", "This staff number is already used.");
        }
        validatePassword(v, input.password());
        if (input.roles() == null || input.roles().isEmpty()) {
            v.reject("roles", "Choose at least one role.");
        }
        v.throwIfAny();

        Employee employee = employees.save(Employee.create(staffNo, fullName, email, department,
                input.designation(), clock.now()));
        UserAccount account = accounts.save(UserAccount.create(employee.getId(), username,
                passwordEncoder.encode(input.password()), EnumSet.copyOf(input.roles()), clock.now()));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("staffNo", staffNo);
        snapshot.put("name", fullName);
        snapshot.put("department", department);
        snapshot.put("designation", input.designation());
        snapshot.put("username", account.getUsername());
        snapshot.put("roles", account.getRoles());
        record(employee.getId(), "STAFF_CREATED", admin, null, "ACTIVE", null, snapshot);

        if (input.approverId() != null) {
            routing.assign(admin, employee.getId(), input.approverId(), false);
        }
        if (input.openCurrentYearAccount() && account.hasRole(Role.EMPLOYEE)) {
            int year = clock.currentYear();
            EntitlementDefaults.Allowance allowance = defaults.forDesignation(input.designation());
            trainingAccounts.save(TrainingAccount.open(employee.getId(), year, allowance.units(), allowance.budget(),
                    clock.now()));
            audit.record(new AuditService.Change(AggregateType.ACCOUNT, employee.getId() + ":" + year,
                    "ENTITLEMENT_CREATED", admin.employeeId(), null, null, "Designation defaults at account creation",
                    Map.of("units", allowance.units(), "budget", allowance.budget())));
        }
        return employee.getId();
    }

    @WriteTransaction
    public void updateProfile(Actor admin, Long employeeId, Profile input, long expectedVersion) {
        Employee employee = employees.lockById(employeeId).orElseThrow(NotFoundException::new);
        if (employee.getVersion() != expectedVersion) {
            throw new com.uptrail.shared.error.StaleVersionException();
        }
        AdminValidation v = new AdminValidation();
        String fullName = v.required("fullName", input.fullName(), 120, "full name");
        String email = v.required("email", input.email(), 254, "email address");
        v.matches("email", email, AdminValidation.EMAIL, "Enter a valid email address.");
        String department = v.required("department", input.department(), 100, "department");
        if (input.designation() == null) {
            v.reject("designation", "Choose a designation.");
        }
        v.throwIfAny();
        Map<String, Object> before = Map.of("name", employee.getFullName(), "email", employee.getEmail(),
                "department", employee.getDepartment(), "designation", employee.getDesignation());
        employee.updateProfile(fullName, email, department, input.designation(), clock.now());
        record(employeeId, "PROFILE_UPDATED", admin, null, null, null, Map.of("before", before, "after",
                Map.of("name", fullName, "email", email, "department", department, "designation",
                        input.designation())));
    }

    @WriteTransaction
    public void changeRoles(Actor admin, Long employeeId, Set<Role> newRoles) {
        if (newRoles == null || newRoles.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose at least one role.",
                    Map.of("roles", "Choose at least one role."));
        }
        Employee employee = employees.lockById(employeeId).orElseThrow(NotFoundException::new);
        UserAccount account = accounts.findByEmployeeId(employeeId).orElseThrow(NotFoundException::new);
        Set<Role> before = EnumSet.copyOf(account.getRoles());
        EnumSet<Role> target = EnumSet.copyOf(newRoles);
        if (target.contains(Role.MANAGER)) {
            target.add(Role.EMPLOYEE);
        }
        if (before.contains(Role.MANAGER) && !target.contains(Role.MANAGER)) {
            requireNoManagerDuties(employee, "remove the manager role from");
        }
        if (before.contains(Role.ADMIN) && !target.contains(Role.ADMIN)) {
            requireAnotherAdmin(employee, "remove the administrator role from");
        }
        account.changeRoles(target, clock.now());
        record(employeeId, "ROLES_CHANGED", admin, null, null, null, Map.of("before", before, "after", target));
        expireSessionsAfterCommit(account.getUsername());
    }

    @WriteTransaction
    public void resetPassword(Actor admin, Long employeeId, String newPassword) {
        AdminValidation v = new AdminValidation();
        validatePassword(v, newPassword);
        v.throwIfAny();
        employees.lockById(employeeId).orElseThrow(NotFoundException::new);
        UserAccount account = accounts.findByEmployeeId(employeeId).orElseThrow(NotFoundException::new);
        account.changePasswordHash(passwordEncoder.encode(newPassword), clock.now());
        record(employeeId, "PASSWORD_RESET", admin, null, null, null, Map.of());
        expireSessionsAfterCommit(account.getUsername());
    }

    @WriteTransaction
    public void deactivate(Actor admin, Long employeeId) {
        if (admin.employeeId().equals(employeeId)) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "You cannot deactivate your own account.");
        }
        Employee employee = employees.lockById(employeeId).orElseThrow(NotFoundException::new);
        if (!employee.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, employee.getFullName() + " is already inactive.");
        }
        UserAccount account = accounts.findByEmployeeId(employeeId).orElse(null);
        if (account != null && account.hasRole(Role.ADMIN)) {
            requireAnotherAdmin(employee, "deactivate");
        }
        requireNoManagerDuties(employee, "deactivate");
        employee.deactivate(clock.now());
        if (account != null) {
            account.disable(clock.now());
            expireSessionsAfterCommit(account.getUsername());
        }
        long ownPending = applications.findByEmployeeIdAndStatusIn(employeeId, ApplicationStatus.PENDING).size();
        record(employeeId, "STAFF_DEACTIVATED", admin, "ACTIVE", "INACTIVE",
                ownPending == 0 ? null : ownPending + " own application(s) still wait for a decision", Map.of());
    }

    @WriteTransaction
    public void reactivate(Actor admin, Long employeeId) {
        Employee employee = employees.lockById(employeeId).orElseThrow(NotFoundException::new);
        if (employee.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, employee.getFullName() + " is already active.");
        }
        employee.reactivate(clock.now());
        accounts.findByEmployeeId(employeeId).ifPresent(a -> a.enable(clock.now()));
        record(employeeId, "STAFF_REACTIVATED", admin, "INACTIVE", "ACTIVE", null, Map.of());
    }

    /** Physical deletion, only for records created by mistake that nothing refers to. */
    @WriteTransaction
    public void delete(Actor admin, Long employeeId) {
        if (admin.employeeId().equals(employeeId)) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "You cannot delete your own account.");
        }
        Employee employee = employees.lockById(employeeId).orElseThrow(NotFoundException::new);
        if (hasHistory(employeeId) || assignments.countByManagerId(employeeId) > 0) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, employee.getFullName() + " has history or staff "
                    + "reporting to them, so the record is kept. Deactivate the employee instead.");
        }
        Optional<UserAccount> account = accounts.findByEmployeeId(employeeId);
        if (account.isPresent() && account.get().hasRole(Role.ADMIN)) {
            requireAnotherAdmin(employee, "delete");
        }
        String name = employee.getFullName();
        assignments.findById(employeeId).ifPresent(assignments::delete);
        trainingAccounts.deleteAll(trainingAccounts.findByEmployeeId(employeeId));
        account.ifPresent(a -> {
            expireSessionsAfterCommit(a.getUsername());
            accounts.delete(a);
        });
        employees.delete(employee);
        employees.flush();
        audit.record(new AuditService.Change(AggregateType.STAFF, employeeId.toString(), "STAFF_DELETED",
                admin.employeeId(), "ACTIVE", null, "Record created by mistake: " + name, Map.of("name", name)));
    }

    // ------------------------------------------------------------------ helpers

    private void requireNoManagerDuties(Employee employee, String verb) {
        long reports = assignments.countByManagerId(employee.getId());
        long pendingApplications = applications.countByApproverIdAndStatusIn(employee.getId(),
                ApplicationStatus.PENDING);
        long pendingClaims = claims.countByApproverIdAndStatus(employee.getId(), ClaimStatus.SUBMITTED);
        if (reports > 0 || pendingApplications > 0 || pendingClaims > 0) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "Cannot " + verb + " " + employee.getFullName()
                    + ": " + reports + " staff report to them and " + pendingApplications + " application(s) and "
                    + pendingClaims + " claim(s) wait for their decision. Move their team and pending items to "
                    + "another manager on the Approval Routing page first.");
        }
    }

    private void requireAnotherAdmin(Employee employee, String verb) {
        if (accounts.countActiveWithRole(Role.ADMIN) <= 1) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "Cannot " + verb + " " + employee.getFullName()
                    + ": they are the only active administrator.");
        }
    }

    private boolean hasHistory(Long employeeId) {
        return applications.countByEmployeeIdOrApproverIdOrReviewedBy(employeeId, employeeId, employeeId) > 0
                || claims.countByReviewedByOrReimbursedBy(employeeId, employeeId) > 0
                || auditEvents.countByActorEmployeeId(employeeId) > 0
                || outbox.countByRecipientEmployeeId(employeeId) > 0
                || calendarYears.countByConfirmedBy(employeeId) > 0;
    }

    private static void validatePassword(AdminValidation v, String password) {
        if (password == null || password.length() < AdminValidation.MIN_PASSWORD) {
            v.reject("password", "Use at least " + AdminValidation.MIN_PASSWORD + " characters.");
        } else if (password.length() > 200) {
            v.reject("password", "Use at most 200 characters.");
        }
    }

    private void expireSessionsAfterCommit(String username) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sessions.expireSessionsOf(username);
                }
            });
        } else {
            sessions.expireSessionsOf(username);
        }
    }

    private void record(Long employeeId, String eventType, Actor admin, String from, String to, String reason,
            Map<String, ?> snapshot) {
        audit.record(new AuditService.Change(AggregateType.STAFF, employeeId.toString(), eventType,
                admin.employeeId(), from, to, reason, snapshot));
    }

    private StaffRow row(Employee employee, UserAccount account) {
        Long approverId = assignments.findById(employee.getId()).map(ApprovalAssignment::getManagerId).orElse(null);
        String approver = approverId == null ? null
                : employees.findById(approverId).map(Employee::getFullName).orElse(null);
        return new StaffRow(employee.getId(), employee.getStaffNo(), employee.getFullName(), employee.getEmail(),
                employee.getDepartment(), employee.getDesignation(), employee.isActive(),
                account == null ? null : account.getUsername(),
                account == null ? Set.of() : EnumSet.copyOf(account.getRoles().isEmpty()
                        ? EnumSet.noneOf(Role.class) : account.getRoles()), approverId, approver);
    }
}
