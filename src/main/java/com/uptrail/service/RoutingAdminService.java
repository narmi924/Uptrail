package com.uptrail.service;

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

import com.uptrail.model.ApplicationStatus;
import com.uptrail.model.CourseApplication;
import com.uptrail.repo.CourseApplicationRepo;
import com.uptrail.model.AggregateType;
import com.uptrail.model.AuditEvent;
import com.uptrail.model.ClaimStatus;
import com.uptrail.model.CourseFeeApplication;
import com.uptrail.repo.CourseFeeApplicationRepo;
import com.uptrail.model.User;
import com.uptrail.model.Role;
import com.uptrail.repo.UserRepo;
import com.uptrail.model.MailTemplate;
import com.uptrail.model.ApprovalHierarchy;
import com.uptrail.repo.ApprovalHierarchyRepo;
import com.uptrail.service.StaffService.PersonRef;
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

    public record RouteRow(Long employeeId, String staffId, String name, String department, Long managerId,
            String managerName, long pendingWithOtherApprover) {
    }

    private final UserRepo employees;
    private final ApprovalHierarchyRepo assignments;
    private final UserRepo accounts;
    private final CourseApplicationRepo applications;
    private final CourseFeeApplicationRepo claims;
    private final AuditService audit;
    private final NotificationService notifications;
    private final BusinessClock clock;
    private final String baseUrl;

    public RoutingAdminService(UserRepo employees, ApprovalHierarchyRepo assignments,
            UserRepo accounts, CourseApplicationRepo applications, CourseFeeApplicationRepo claims,
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
            Long managerId = assignments.findById(e.getId()).map(ApprovalHierarchy::getManagerId).orElse(null);
            String managerName = managerId == null ? null
                    : employees.findById(managerId).map(User::getName).orElse(null);
            long stale = applications.findByApplicantIdAndStatusIn(e.getId(), ApplicationStatus.PENDING).stream()
                    .filter(a -> !Objects.equals(a.getApproverId(), managerId)).count();
            return new RouteRow(e.getId(), e.getStaffId(), e.getName(), e.getDepartment(), managerId, managerName,
                    stale);
        });
    }

    @Transactional(readOnly = true)
    public List<PersonRef> managers() {
        return employees.findActiveManagers().stream()
                .map(e -> new PersonRef(e.getId(), e.getName(), e.getDepartment(), e.isActive())).toList();
    }

    /**
     * Sets or removes the approver of an employee. With {@code reassignPending} the employee's applications
     * and claims waiting for a decision move to the new approver as well.
     */
    @WriteTransaction
    public int assign(User admin, Long employeeId, Long managerId, boolean reassignPending) {
        User employee = employees.lockById(employeeId).orElseThrow(NotFoundException::new);
        Long previous = assignments.findById(employeeId).map(ApprovalHierarchy::getManagerId).orElse(null);
        if (managerId == null) {
            assignments.findById(employeeId).ifPresent(assignments::delete);
            audit.record(new AuditService.Change(AggregateType.ROUTING, employeeId.toString(), "ROUTE_REMOVED",
                    admin.getUserId(), idOrNone(previous), "NONE", null, Map.of("employee", employee.getName())));
            return 0;
        }
        validateManager(employee, managerId);
        ApprovalHierarchy assignment = assignments.findById(employeeId).orElse(null);
        if (assignment == null) {
            assignments.save(ApprovalHierarchy.assign(employeeId, managerId, clock.now()));
        } else {
            assignment.changeManager(managerId, clock.now());
        }
        audit.record(new AuditService.Change(AggregateType.ROUTING, employeeId.toString(), "ROUTE_CHANGED",
                admin.getUserId(), idOrNone(previous), managerId.toString(), null,
                Map.of("employee", employee.getName(), "manager", nameOf(managerId))));
        return reassignPending ? reassignPendingItems(admin, employee, managerId) : 0;
    }

    /** Moves every direct report of one manager, with their waiting items, to another manager. */
    @WriteTransaction
    public int moveTeam(User admin, Long fromManagerId, Long toManagerId) {
        if (Objects.equals(fromManagerId, toManagerId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose two different managers.",
                    Map.of("toManagerId", "Choose a different manager."));
        }
        int moved = 0;
        List<Long> team = assignments.findByManagerId(fromManagerId).stream()
                .map(ApprovalHierarchy::getEmployeeId).sorted().toList();
        for (Long employeeId : team) {
            assign(admin, employeeId, toManagerId, true);
            moved++;
        }
        // Items assigned to the old manager by people who no longer report to them.
        for (CourseApplication application : applications.findByApproverIdAndStatusIn(fromManagerId,
                ApplicationStatus.PENDING)) {
            User owner = employees.lockById(application.getApplicantId()).orElseThrow();
            if (!owner.getUserId().equals(toManagerId)) {
                reassignApplication(admin, applications.lockById(application.getId()).orElseThrow(), toManagerId,
                        owner);
            }
        }
        for (CourseFeeApplication claim : claims.findByApproverIdAndStatus(fromManagerId, ClaimStatus.SUBMITTED)) {
            CourseApplication application = applications.findById(claim.getApplicationId()).orElseThrow();
            if (!application.getApplicantId().equals(toManagerId)) {
                employees.lockById(application.getApplicantId());
                reassignClaim(admin, claim, toManagerId);
            }
        }
        return moved;
    }

    private int reassignPendingItems(User admin, User employee, Long managerId) {
        int moved = 0;
        for (CourseApplication application : applications.findByApplicantIdAndStatusIn(employee.getUserId(),
                ApplicationStatus.PENDING)) {
            if (!Objects.equals(application.getApproverId(), managerId)) {
                reassignApplication(admin, applications.lockById(application.getId()).orElseThrow(), managerId,
                        employee);
                moved++;
            }
        }
        for (CourseFeeApplication claim : claims.findForEmployee(employee.getUserId(), ClaimStatus.SUBMITTED)) {
            if (!Objects.equals(claim.getApproverId(), managerId)) {
                reassignClaim(admin, claim, managerId);
                moved++;
            }
        }
        return moved;
    }

    private void reassignApplication(User admin, CourseApplication application, Long managerId, User owner) {
        Long previous = application.getApproverId();
        application.reassignApprover(managerId, clock.now());
        AuditEvent event = audit.record(new AuditService.Change(AggregateType.APPLICATION,
                application.getId().toString(), "REASSIGNED", admin.getUserId(), application.getStatus().name(),
                application.getStatus().name(), "Approver changed by an administrator",
                Map.of("fromApprover", previous, "toApprover", managerId)));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reference", application.getReferenceNo());
        payload.put("applicantName", owner.getName());
        payload.put("courseTitle", application.getCourseTitle());
        payload.put("status", application.getStatus().label());
        payload.put("link", baseUrl + "/employee/login?next=/manager/applications/" + application.getId());
        notifications.enqueue(event.getId(), managerId, MailTemplate.APPLICATION_REASSIGNED, payload);
    }

    private void reassignClaim(User admin, CourseFeeApplication claim, Long managerId) {
        Long previous = claim.getApproverId();
        claim.reassignApprover(managerId);
        audit.record(new AuditService.Change(AggregateType.CLAIM, claim.getId().toString(), "REASSIGNED",
                admin.getUserId(), claim.getStatus().name(), claim.getStatus().name(),
                "Approver changed by an administrator", Map.of("fromApprover", previous, "toApprover", managerId)));
    }

    private void validateManager(User employee, Long managerId) {
        if (employee.getUserId().equals(managerId)) {
            throw invalid("An employee cannot be their own approver.");
        }
        User manager = employees.findById(managerId).orElseThrow(() -> invalid("Choose an existing manager."));
        if (!manager.isActive() || !accounts.userHasRole(managerId, Role.MANAGER)) {
            throw invalid(manager.getName() + " is not an active manager.");
        }
        Set<Long> seen = new HashSet<>();
        Long current = managerId;
        while (current != null && seen.add(current)) {
            if (current.equals(employee.getUserId())) {
                throw invalid(manager.getName() + " already reports, directly or indirectly, to "
                        + employee.getName() + "; this route would create a loop.");
            }
            current = assignments.findById(current).map(ApprovalHierarchy::getManagerId).orElse(null);
        }
    }

    private String nameOf(Long employeeId) {
        return employees.findById(employeeId).map(User::getName).orElse("Unknown");
    }

    private static String idOrNone(Long id) {
        return id == null ? "NONE" : id.toString();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message, Map.of("managerId", message));
    }
}
