# CATS team implementation reference

Uptrail is a complete MVC training application with the CATS inheritance and layer names. Open the files for your module below. The assignment, latest diagrams and agreed team contracts define requirements.

## Shared model and session

```text
User
├── Staff
│   └── Manager
└── Admin
```

One User owns one userId, userName, name and staffId. Manager inherits through Staff. There is no separate Employee/UserAccount entity or duplicated employee fields. Code uses model / repo / service / controller.

Login stores a password-free, correctly typed User in session "user". CurrentUserService resolves it for all pages; client parameters cannot replace it. Spring Security supplies password checking, CSRF and session protection behind this contract.

## Run the complete MVC application

Java 21 and Docker:

```bash
git clone https://github.com/narmi924/Uptrail.git
cd Uptrail
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Windows: use mvnw.cmd. See README for a UnixDomainSockets loopback workaround.

| Role | Login | Account | First page |
| --- | --- | --- | --- |
| Staff | /employee/login | hannah | /staff/home |
| Manager | /employee/login | daniel | /manager/home |
| Admin | /admin/login | alex | /admin/home |

Sample password: Uptrail#2026. Manager also uses /staff/home to apply; a different assigned manager decides. All data is synthetic.

The default is pure MVC. Add rest-enhanced later for live eligibility, catalogue search and interactive calendar. Submission, decisions, history and calendar navigation already work without REST.

## Source map

| Task | Files |
| --- | --- |
| Identity | [User](../src/main/java/com/uptrail/model/User.java), [Staff](../src/main/java/com/uptrail/model/Staff.java), [Manager](../src/main/java/com/uptrail/model/Manager.java), [Admin](../src/main/java/com/uptrail/model/Admin.java) |
| Login/session | [UserController](../src/main/java/com/uptrail/controller/UserController.java), [UserService](../src/main/java/com/uptrail/service/UserService.java), [CurrentUserService](../src/main/java/com/uptrail/service/CurrentUserService.java), [EntrySuccessHandler](../src/main/java/com/uptrail/controller/EntrySuccessHandler.java) |
| Staff form/lifecycle | [StaffController](../src/main/java/com/uptrail/controller/StaffController.java), [CourseApplicationService](../src/main/java/com/uptrail/service/CourseApplicationService.java), [form](../src/main/resources/templates/employee/applications/form.html) |
| Application contract | [CourseApplication](../src/main/java/com/uptrail/model/CourseApplication.java), [CourseApplicationRepo](../src/main/java/com/uptrail/repo/CourseApplicationRepo.java) |
| Manager decisions | [ManagerController](../src/main/java/com/uptrail/controller/ManagerController.java), [ManagerService](../src/main/java/com/uptrail/service/ManagerService.java), [ManagerRepo](../src/main/java/com/uptrail/repo/ManagerRepo.java), [review](../src/main/resources/templates/manager/review.html) |
| Reporting relationship | [ApprovalHierarchy](../src/main/java/com/uptrail/model/ApprovalHierarchy.java), [RoutingAdminService](../src/main/java/com/uptrail/service/RoutingAdminService.java) |
| Days/budget/overlaps | [ApplicationEvaluator](../src/main/java/com/uptrail/service/ApplicationEvaluator.java), [TrainingDayCalculator](../src/main/java/com/uptrail/model/TrainingDayCalculator.java), [TrainingEntitlement](../src/main/java/com/uptrail/model/TrainingEntitlement.java), [ExcludedDays](../src/main/java/com/uptrail/model/ExcludedDays.java) |
| Subordinate history/scope | [ManagerTeamController](../src/main/java/com/uptrail/controller/ManagerTeamController.java), [AccessScopePolicy](../src/main/java/com/uptrail/service/AccessScopePolicy.java) |
| Fee claims | [CourseFeeApplication](../src/main/java/com/uptrail/model/CourseFeeApplication.java), [StaffClaimController](../src/main/java/com/uptrail/controller/StaffClaimController.java), [ClaimCommandService](../src/main/java/com/uptrail/service/ClaimCommandService.java) |
| Administration | [AdminStaffController](../src/main/java/com/uptrail/controller/AdminStaffController.java), [StaffAdminService](../src/main/java/com/uptrail/service/StaffAdminService.java) |
| Startup/upgrade | [configuration](../src/main/resources/application.yml), [Docker Compose](../docker-compose.yml), [V6](../src/main/resources/db/migration/V6__cats_user_hierarchy.sql) |

## Shared contracts

- userId is the database identity; staffId is the String business number. ManagerRepo.findByStaffId queries the inherited field.
- CourseApplication stores applicantId, approverId, status, decisionReason, reviewedBy and reviewedAt. Applicant/assignedManager relationships resolve those same IDs; history survives role changes.
- ApprovalHierarchy is the current manager. Submission snapshots that manager; pending reassignment is explicit and old decisions stay unchanged.
- Only the assigned manager decides pending records, with a reason for both outcomes. Self-approval is rejected. Every write rechecks identity, ownership, state and version.
- Current-year history, annual usage and overlapping approved team courses belong to the basic flow.
- Keep CATS's existing numeric ID types/enums when moving code. Uptrail uses Long IDs and BigDecimal money.
- Annual limits belong to TrainingEntitlement; User/Staff do not duplicate yearly budgets.
- Pre-V6 application/claim email links under /employee redirect to the Staff detail pages. The same role and ownership checks still apply.

## AI, delivery and validation

Give the assistant the team files, agreed contract, failing flow and relevant reference files. Ask for a small patch in the owner's module. Review the diff, understand it, run compile/fast tests, and verify the actual login-to-feature flow before opening a PR.

Complete login -> Staff submission -> assigned Manager decision/reason -> Staff result -> lifecycle/history -> usage/overlaps first. Leave time for MySQL startup, deployment and presentation rehearsal. Optional ledger/outbox/cleanup/report internals do not have to be learnt before implementing one page.

```bash
./mvnw -B -ntp clean verify
./mvnw -B -ntp -Pe2e verify
```

Regression checks cover real subtypes, inherited identifier lookup, one password-free session, spoofing protection, role changes with stable IDs, occupied-database upgrades preserving decisions, lifecycle/claims, concurrency and browser flows with/without REST.
