package com.uptrail.service;

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

import com.uptrail.model.ApplicationStatus;
import com.uptrail.repo.CourseApplicationRepo;
import com.uptrail.model.AggregateType;
import com.uptrail.repo.AuditEventRepo;
import com.uptrail.repo.TrainingCalendarYearRepo;
import com.uptrail.model.ClaimStatus;
import com.uptrail.repo.CourseFeeApplicationRepo;
import com.uptrail.model.TrainingEntitlement;
import com.uptrail.repo.TrainingEntitlementRepo;
import com.uptrail.model.User;
import com.uptrail.model.Role;
import com.uptrail.repo.UserRepo;
import com.uptrail.repo.OutboxMessageRepo;
import com.uptrail.model.ApprovalHierarchy;
import com.uptrail.model.Designation;
import com.uptrail.repo.ApprovalHierarchyRepo;
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

    public record StaffRow(Long id, String staffId, String name, String email, String department,
            Designation designation, boolean active, String username, Set<Role> roles, Long approverId,
            String approverName) {

        public String rolesLabel() {
            return String.join(", ", roles.stream().sorted().map(Role::label).toList());
        }
    }

    public record StaffDetail(StaffRow staff, long version, long directReports, long pendingApplicationsToDecide,
            long pendingClaimsToDecide, boolean hasHistory, boolean accountEnabled) {
    }

    public record NewStaff(String staffId, String name, String email, String department, Designation designation,
            String username, String password, Set<Role> roles, Long approverId, boolean openCurrentYearAccount) {
    }

    public record Profile(String name, String email, String department, Designation designation) {
    }

    private final UserRepo employees;
    private final UserRepo accounts;
    private final ApprovalHierarchyRepo assignments;
    private final CourseApplicationRepo applications;
    private final CourseFeeApplicationRepo claims;
    private final TrainingEntitlementRepo trainingAccounts;
    private final AuditEventRepo auditEvents;
    private final OutboxMessageRepo outbox;
    private final TrainingCalendarYearRepo calendarYears;
    private final RoutingAdminService routing;
    private final EntitlementDefaults defaults;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;
    private final SessionControlService sessions;
    private final BusinessClock clock;

    public StaffAdminService(UserRepo employees, UserRepo accounts,
            ApprovalHierarchyRepo assignments, CourseApplicationRepo applications,
            CourseFeeApplicationRepo claims, TrainingEntitlementRepo trainingAccounts, AuditEventRepo auditEvents,
            OutboxMessageRepo outbox, TrainingCalendarYearRepo calendarYears, RoutingAdminService routing,
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
        Page<User> page = employees.search(q, active, pageable);
        Map<Long, User> byEmployee = new LinkedHashMap<>();
        accounts.findByUserIdIn(page.getContent().stream().map(User::getUserId).toList())
                .forEach(a -> byEmployee.put(a.getUserId(), a));
        return page.map(e -> row(e, byEmployee.get(e.getUserId())));
    }

    @Transactional(readOnly = true)
    public StaffDetail detail(Long employeeId) {
        User employee = employees.findById(employeeId).orElseThrow(NotFoundException::new);
        User account = accounts.findById(employeeId).orElse(null);
        return new StaffDetail(row(employee, account), employee.getVersion(), assignments.countByManagerId(employeeId),
                applications.countByApproverIdAndStatusIn(employeeId, ApplicationStatus.PENDING),
                claims.countByApproverIdAndStatus(employeeId, ClaimStatus.SUBMITTED), hasHistory(employeeId),
                account != null && account.isEnabled());
    }

    // ------------------------------------------------------------------ writes

    @WriteTransaction
    public Long create(User admin, NewStaff input) {
        AdminValidation v = new AdminValidation();
        String staffId = v.required("staffId", input.staffId(), 30, "staff number");
        v.matches("staffId", staffId, AdminValidation.STAFF_NO, "Use letters, digits and hyphens only.");
        String name = v.required("name", input.name(), 120, "full name");
        String email = v.required("email", input.email(), 254, "email address");
        v.matches("email", email, AdminValidation.EMAIL, "Enter a valid email address.");
        String department = v.required("department", input.department(), 100, "department");
        if (input.designation() == null) {
            v.reject("designation", "Choose a designation.");
        }
        String username = v.required("username", input.username(), 80, "username");
        if (username != null) {
            username = User.normaliseUsername(username);
            v.matches("username", username, AdminValidation.USERNAME,
                    "Use 3 to 80 lower-case letters, digits, dots, hyphens or underscores.");
            if (accounts.existsByUserName(username)) {
                v.reject("username", "This username is already taken.");
            }
        }
        if (staffId != null && employees.existsByStaffId(staffId)) {
            v.reject("staffId", "This staff number is already used.");
        }
        validatePassword(v, input.password());
        if (input.roles() == null || input.roles().isEmpty()) {
            v.reject("roles", "Choose at least one role.");
        }
        v.throwIfAny();

        User employee = employees.save(User.create(staffId, name, email, department,
                input.designation(), username, passwordEncoder.encode(input.password()),
                EnumSet.copyOf(input.roles()), clock.now()));
        User account = employee;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("staffId", staffId);
        snapshot.put("name", name);
        snapshot.put("department", department);
        snapshot.put("designation", input.designation());
        snapshot.put("username", account.getUserName());
        snapshot.put("roles", account.getRoles());
        record(employee.getUserId(), "STAFF_CREATED", admin, null, "ACTIVE", null, snapshot);

        if (input.approverId() != null) {
            routing.assign(admin, employee.getUserId(), input.approverId(), false);
        }
        if (input.openCurrentYearAccount() && account.hasRole(Role.STAFF)) {
            int year = clock.currentYear();
            EntitlementDefaults.Allowance allowance = defaults.forDesignation(input.designation());
            trainingAccounts.save(TrainingEntitlement.open(employee.getUserId(), year, allowance.units(), allowance.budget(),
                    clock.now()));
            audit.record(new AuditService.Change(AggregateType.ACCOUNT, employee.getUserId() + ":" + year,
                    "ENTITLEMENT_CREATED", admin.getUserId(), null, null, "Designation defaults at account creation",
                    Map.of("units", allowance.units(), "budget", allowance.budget())));
        }
        return employee.getUserId();
    }

    @WriteTransaction
    public void updateProfile(User admin, Long employeeId, Profile input, long expectedVersion) {
        User employee = employees.lockById(employeeId).orElseThrow(NotFoundException::new);
        if (employee.getVersion() != expectedVersion) {
            throw new com.uptrail.shared.error.StaleVersionException();
        }
        AdminValidation v = new AdminValidation();
        String name = v.required("name", input.name(), 120, "full name");
        String email = v.required("email", input.email(), 254, "email address");
        v.matches("email", email, AdminValidation.EMAIL, "Enter a valid email address.");
        String department = v.required("department", input.department(), 100, "department");
        if (input.designation() == null) {
            v.reject("designation", "Choose a designation.");
        }
        v.throwIfAny();
        Map<String, Object> before = Map.of("name", employee.getName(), "email", employee.getEmail(),
                "department", employee.getDepartment(), "designation", employee.getDesignation());
        employee.updateProfile(name, email, department, input.designation(), clock.now());
        record(employeeId, "PROFILE_UPDATED", admin, null, null, null, Map.of("before", before, "after",
                Map.of("name", name, "email", email, "department", department, "designation",
                        input.designation())));
    }

    @WriteTransaction
    public void changeRoles(User admin, Long employeeId, Set<Role> newRoles) {
        if (newRoles == null || newRoles.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose at least one role.",
                    Map.of("roles", "Choose at least one role."));
        }
        User employee = employees.lockById(employeeId).orElseThrow(NotFoundException::new);
        User account = accounts.findById(employeeId).orElseThrow(NotFoundException::new);
        Set<Role> before = EnumSet.copyOf(account.getRoles());
        EnumSet<Role> target = EnumSet.copyOf(newRoles);
        if (target.contains(Role.MANAGER)) {
            target.add(Role.STAFF);
        }
        if (before.contains(Role.MANAGER) && !target.contains(Role.MANAGER)) {
            requireNoManagerDuties(employee, "remove the manager role from");
        }
        if (before.contains(Role.ADMIN) && !target.contains(Role.ADMIN)) {
            requireAnotherAdmin(employee, "remove the administrator role from");
        }
        account.changeRoles(target, clock.now());
        String subtype = target.contains(Role.MANAGER) ? "MANAGER"
                : target.contains(Role.ADMIN) ? "ADMIN" : "STAFF";
        accounts.changeSubtype(employeeId, subtype);
        record(employeeId, "ROLES_CHANGED", admin, null, null, null, Map.of("before", before, "after", target));
        expireSessionsAfterCommit(account.getUserName());
    }

    @WriteTransaction
    public void resetPassword(User admin, Long employeeId, String newPassword) {
        AdminValidation v = new AdminValidation();
        validatePassword(v, newPassword);
        v.throwIfAny();
        employees.lockById(employeeId).orElseThrow(NotFoundException::new);
        User account = accounts.findById(employeeId).orElseThrow(NotFoundException::new);
        account.changePasswordHash(passwordEncoder.encode(newPassword), clock.now());
        record(employeeId, "PASSWORD_RESET", admin, null, null, null, Map.of());
        expireSessionsAfterCommit(account.getUserName());
    }

    @WriteTransaction
    public void deactivate(User admin, Long employeeId) {
        if (admin.getUserId().equals(employeeId)) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "You cannot deactivate your own account.");
        }
        User employee = employees.lockById(employeeId).orElseThrow(NotFoundException::new);
        if (!employee.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, employee.getName() + " is already inactive.");
        }
        User account = accounts.findById(employeeId).orElse(null);
        if (account != null && account.hasRole(Role.ADMIN)) {
            requireAnotherAdmin(employee, "deactivate");
        }
        requireNoManagerDuties(employee, "deactivate");
        employee.deactivate(clock.now());
        if (account != null) {
            account.disable(clock.now());
            expireSessionsAfterCommit(account.getUserName());
        }
        long ownPending = applications.findByApplicantIdAndStatusIn(employeeId, ApplicationStatus.PENDING).size();
        record(employeeId, "STAFF_DEACTIVATED", admin, "ACTIVE", "INACTIVE",
                ownPending == 0 ? null : ownPending + " own application(s) still wait for a decision", Map.of());
    }

    @WriteTransaction
    public void reactivate(User admin, Long employeeId) {
        User employee = employees.lockById(employeeId).orElseThrow(NotFoundException::new);
        if (employee.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, employee.getName() + " is already active.");
        }
        employee.reactivate(clock.now());
        accounts.findById(employeeId).ifPresent(a -> a.enable(clock.now()));
        record(employeeId, "STAFF_REACTIVATED", admin, "INACTIVE", "ACTIVE", null, Map.of());
    }

    /** Physical deletion, only for records created by mistake that nothing refers to. */
    @WriteTransaction
    public void delete(User admin, Long employeeId) {
        if (admin.getUserId().equals(employeeId)) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "You cannot delete your own account.");
        }
        User employee = employees.lockById(employeeId).orElseThrow(NotFoundException::new);
        if (hasHistory(employeeId) || assignments.countByManagerId(employeeId) > 0) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, employee.getName() + " has history or staff "
                    + "reporting to them, so the record is kept. Deactivate the employee instead.");
        }
        Optional<User> account = accounts.findById(employeeId);
        if (account.isPresent() && account.get().hasRole(Role.ADMIN)) {
            requireAnotherAdmin(employee, "delete");
        }
        String name = employee.getName();
        assignments.findById(employeeId).ifPresent(assignments::delete);
        trainingAccounts.deleteAll(trainingAccounts.findByEmployeeId(employeeId));
        account.ifPresent(a -> {
            expireSessionsAfterCommit(a.getUserName());

        });
        employees.delete(employee);
        employees.flush();
        audit.record(new AuditService.Change(AggregateType.STAFF, employeeId.toString(), "STAFF_DELETED",
                admin.getUserId(), "ACTIVE", null, "Record created by mistake: " + name, Map.of("name", name)));
    }

    // ------------------------------------------------------------------ helpers

    private void requireNoManagerDuties(User employee, String verb) {
        long reports = assignments.countByManagerId(employee.getUserId());
        long pendingApplications = applications.countByApproverIdAndStatusIn(employee.getUserId(),
                ApplicationStatus.PENDING);
        long pendingClaims = claims.countByApproverIdAndStatus(employee.getUserId(), ClaimStatus.SUBMITTED);
        if (reports > 0 || pendingApplications > 0 || pendingClaims > 0) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "Cannot " + verb + " " + employee.getName()
                    + ": " + reports + " staff report to them and " + pendingApplications + " application(s) and "
                    + pendingClaims + " claim(s) wait for their decision. Move their team and pending items to "
                    + "another manager on the Approval Routing page first.");
        }
    }

    private void requireAnotherAdmin(User employee, String verb) {
        if (accounts.countActiveWithRole(Role.ADMIN) <= 1) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "Cannot " + verb + " " + employee.getName()
                    + ": they are the only active administrator.");
        }
    }

    private boolean hasHistory(Long employeeId) {
        return applications.countByApplicantIdOrApproverIdOrReviewedBy(employeeId, employeeId, employeeId) > 0
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

    private void record(Long employeeId, String eventType, User admin, String from, String to, String reason,
            Map<String, ?> snapshot) {
        audit.record(new AuditService.Change(AggregateType.STAFF, employeeId.toString(), eventType,
                admin.getUserId(), from, to, reason, snapshot));
    }

    private StaffRow row(User employee, User account) {
        Long approverId = assignments.findById(employee.getUserId()).map(ApprovalHierarchy::getManagerId).orElse(null);
        String approver = approverId == null ? null
                : employees.findById(approverId).map(User::getName).orElse(null);
        return new StaffRow(employee.getUserId(), employee.getStaffId(), employee.getName(), employee.getEmail(),
                employee.getDepartment(), employee.getDesignation(), employee.isActive(),
                account == null ? null : account.getUserName(),
                account == null ? Set.of() : EnumSet.copyOf(account.getRoles().isEmpty()
                        ? EnumSet.noneOf(Role.class) : account.getRoles()), approverId, approver);
    }
}
