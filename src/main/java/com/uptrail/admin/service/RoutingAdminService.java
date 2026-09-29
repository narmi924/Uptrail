package com.uptrail.admin.service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.application.domain.ApplicationStatus;
import com.uptrail.application.domain.CourseApplication;
import com.uptrail.application.repository.CourseApplicationRepository;
import com.uptrail.audit.domain.AggregateType;
import com.uptrail.audit.domain.AuditEvent;
import com.uptrail.audit.service.AuditService;
import com.uptrail.claim.domain.ClaimStatus;
import com.uptrail.claim.domain.CourseClaim;
import com.uptrail.claim.repository.CourseClaimRepository;
import com.uptrail.identity.domain.Actor;
import com.uptrail.identity.domain.Role;
import com.uptrail.identity.repository.UserAccountRepository;
import com.uptrail.notification.domain.MailTemplate;
import com.uptrail.notification.service.NotificationService;
import com.uptrail.organisation.domain.ApprovalAssignment;
import com.uptrail.organisation.domain.Employee;
import com.uptrail.organisation.repository.ApprovalAssignmentRepository;
import com.uptrail.organisation.repository.EmployeeRepository;
import com.uptrail.organisation.service.EmployeeDirectoryService.PersonRef;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.shared.time.BusinessClock;
import com.uptrail.shared.tx.WriteTransaction;

/**
 * Single-level approval routing. A routing change affects new submissions only; applications and claims
 * already waiting keep their approver unless the administrator explicitly reassigns them, which is audited
 * and notifies the new approver.
 */
@Service
public class RoutingAdminService {

    public record RouteRow(Long employeeId, String staffNo, String fullName, String department, Long managerId,
            String managerName, long pendingWithOtherApprover) {
    }

    private final EmployeeRepository employees;
    private final ApprovalAssignmentRepository assignments;
    private final UserAccountRepository accounts;
    private final CourseApplicationRepository applications;
    private final CourseClaimRepository claims;
    private final AuditService audit;
    private final NotificationService notifications;
    private final BusinessClock clock;
    private final String baseUrl;

    public RoutingAdminService(EmployeeRepository employees, ApprovalAssignmentRepository assignments,
            UserAccountRepository accounts, CourseApplicationRepository applications, CourseClaimRepository claims,
            AuditService audit, NotificationService notifications, BusinessClock clock,
            @Value("${uptrail.base-url}") String baseUrl) {
        this.employees = employees;
        this.assignments = assignments;
        this.accounts = accounts;
        this.applications = applications;
        this.claims = claims;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    @Transactional(readOnly = true)
    public Page<RouteRow> list(String query, Pageable pageable) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return employees.findActiveApplicants(q, pageable).map(e -> {
            Long managerId = assignments.findById(e.getId()).map(ApprovalAssignment::getManagerId).orElse(null);
            String managerName = managerId == null ? null
                    : employees.findById(managerId).map(Employee::getFullName).orElse(null);
            long stale = applications.findByEmployeeIdAndStatusIn(e.getId(), ApplicationStatus.PENDING).stream()
                    .filter(a -> !Objects.equals(a.getApproverId(), managerId)).count();
            return new RouteRow(e.getId(), e.getStaffNo(), e.getFullName(), e.getDepartment(), managerId, managerName,
                    stale);
        });
    }

    @Transactional(readOnly = true)
    public List<PersonRef> managers() {
        return employees.findActiveManagers().stream()
                .map(e -> new PersonRef(e.getId(), e.getFullName(), e.getDepartment(), e.isActive())).toList();
    }

    /**
     * Sets or removes the approver of an employee. With {@code reassignPending} the employee's applications
     * and claims waiting for a decision move to the new approver as well.
     */
    @WriteTransaction
    public int assign(Actor admin, Long employeeId, Long managerId, boolean reassignPending) {
        Employee employee = employees.lockById(employeeId).orElseThrow(NotFoundException::new);
        Long previous = assignments.findById(employeeId).map(ApprovalAssignment::getManagerId).orElse(null);
        if (managerId == null) {
            assignments.findById(employeeId).ifPresent(assignments::delete);
            audit.record(new AuditService.Change(AggregateType.ROUTING, employeeId.toString(), "ROUTE_REMOVED",
                    admin.employeeId(), idOrNone(previous), "NONE", null, Map.of("employee", employee.getFullName())));
            return 0;
        }
        validateManager(employee, managerId);
        ApprovalAssignment assignment = assignments.findById(employeeId).orElse(null);
        if (assignment == null) {
            assignments.save(ApprovalAssignment.assign(employeeId, managerId, clock.now()));
        } else {
            assignment.changeManager(managerId, clock.now());
        }
        audit.record(new AuditService.Change(AggregateType.ROUTING, employeeId.toString(), "ROUTE_CHANGED",
                admin.employeeId(), idOrNone(previous), managerId.toString(), null,
                Map.of("employee", employee.getFullName(), "manager", nameOf(managerId))));
        return reassignPending ? reassignPendingItems(admin, employee, managerId) : 0;
    }

    /** Moves every direct report of one manager, with their waiting items, to another manager. */
    @WriteTransaction
    public int moveTeam(Actor admin, Long fromManagerId, Long toManagerId) {
        if (Objects.equals(fromManagerId, toManagerId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose two different managers.",
                    Map.of("toManagerId", "Choose a different manager."));
        }
        int moved = 0;
        List<Long> team = assignments.findByManagerId(fromManagerId).stream()
                .map(ApprovalAssignment::getEmployeeId).sorted().toList();
        for (Long employeeId : team) {
            assign(admin, employeeId, toManagerId, true);
            moved++;
        }
        // Items assigned to the old manager by people who no longer report to them.
        for (CourseApplication application : applications.findByApproverIdAndStatusIn(fromManagerId,
                ApplicationStatus.PENDING)) {
            Employee owner = employees.lockById(application.getEmployeeId()).orElseThrow();
            if (!owner.getId().equals(toManagerId)) {
                reassignApplication(admin, applications.lockById(application.getId()).orElseThrow(), toManagerId,
                        owner);
            }
        }
        for (CourseClaim claim : claims.findByApproverIdAndStatus(fromManagerId, ClaimStatus.SUBMITTED)) {
            CourseApplication application = applications.findById(claim.getApplicationId()).orElseThrow();
            if (!application.getEmployeeId().equals(toManagerId)) {
                employees.lockById(application.getEmployeeId());
                reassignClaim(admin, claim, toManagerId);
            }
        }
        return moved;
    }

    private int reassignPendingItems(Actor admin, Employee employee, Long managerId) {
        int moved = 0;
        for (CourseApplication application : applications.findByEmployeeIdAndStatusIn(employee.getId(),
                ApplicationStatus.PENDING)) {
            if (!Objects.equals(application.getApproverId(), managerId)) {
                reassignApplication(admin, applications.lockById(application.getId()).orElseThrow(), managerId,
                        employee);
                moved++;
            }
        }
        for (CourseClaim claim : claims.findForEmployee(employee.getId(), ClaimStatus.SUBMITTED)) {
            if (!Objects.equals(claim.getApproverId(), managerId)) {
                reassignClaim(admin, claim, managerId);
                moved++;
            }
        }
        return moved;
    }

    private void reassignApplication(Actor admin, CourseApplication application, Long managerId, Employee owner) {
        Long previous = application.getApproverId();
        application.reassignApprover(managerId, clock.now());
        AuditEvent event = audit.record(new AuditService.Change(AggregateType.APPLICATION,
                application.getId().toString(), "REASSIGNED", admin.employeeId(), application.getStatus().name(),
                application.getStatus().name(), "Approver changed by an administrator",
                Map.of("fromApprover", previous, "toApprover", managerId)));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reference", application.getReferenceNo());
        payload.put("applicantName", owner.getFullName());
        payload.put("courseTitle", application.getCourseTitle());
        payload.put("status", application.getStatus().label());
        payload.put("link", baseUrl + "/login?next=/manager/applications/" + application.getId());
        notifications.enqueue(event.getId(), managerId, MailTemplate.APPLICATION_REASSIGNED, payload);
    }

    private void reassignClaim(Actor admin, CourseClaim claim, Long managerId) {
        Long previous = claim.getApproverId();
        claim.reassignApprover(managerId);
        audit.record(new AuditService.Change(AggregateType.CLAIM, claim.getId().toString(), "REASSIGNED",
                admin.employeeId(), claim.getStatus().name(), claim.getStatus().name(),
                "Approver changed by an administrator", Map.of("fromApprover", previous, "toApprover", managerId)));
    }

    private void validateManager(Employee employee, Long managerId) {
        if (employee.getId().equals(managerId)) {
            throw invalid("An employee cannot be their own approver.");
        }
        Employee manager = employees.findById(managerId).orElseThrow(() -> invalid("Choose an existing manager."));
        if (!manager.isActive() || !accounts.employeeHasRole(managerId, Role.MANAGER)) {
            throw invalid(manager.getFullName() + " is not an active manager.");
        }
        Set<Long> seen = new HashSet<>();
        Long current = managerId;
        while (current != null && seen.add(current)) {
            if (current.equals(employee.getId())) {
                throw invalid(manager.getFullName() + " already reports, directly or indirectly, to "
                        + employee.getFullName() + "; this route would create a loop.");
            }
            current = assignments.findById(current).map(ApprovalAssignment::getManagerId).orElse(null);
        }
    }

    private String nameOf(Long employeeId) {
        return employees.findById(employeeId).map(Employee::getFullName).orElse("Unknown");
    }

    private static String idOrNone(Long id) {
        return id == null ? "NONE" : id.toString();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message, Map.of("managerId", message));
    }
}
