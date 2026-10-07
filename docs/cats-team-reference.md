# CATS team: MVC-first implementation reference

Use Uptrail to find a working example when a CATS task is blocked. The assignment and the team's current diagrams define the product; `bbcares1/CATS_System` defines the team's models and code. Adapt a small, relevant implementation rather than replacing the team project with this application.

## Start with the mandatory MVC flow

Uptrail's submissions, application lifecycle, approvals, history and administration use `@Controller`, Thymeleaf and ordinary HTML form posts. Services validate the commands and repositories persist them. REST is an optional enhancement, not a requirement for these flows.

Run the MVC-only reference on Java 21 with Docker:

```bash
git clone https://github.com/narmi924/Uptrail.git
cd Uptrail
docker compose up -d
./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev,mvc-reference"
```

On Windows use `mvnw.cmd` with the same arguments. Open [staff login](http://localhost:8080/login) or [admin login](http://localhost:8080/admin/login). The sample password is `Uptrail#2026`; use `hannah` as an employee, `daniel` as the employee's manager and `alex` as administrator.

The `mvc-reference` profile does not register the three REST controllers. The application form validates on submission, catalogue search is hidden, and the calendar uses server-rendered lists and GET form navigation. Submission, manager decisions and the ordinary employee/manager/admin pages continue to work. Remove the profile later to enable live eligibility checks, catalogue search and the interactive calendar. The public online demo uses that full mode.

## Find the example for your problem

Paths below start at `src/main/java/com/uptrail/`, unless they point to templates.

| Problem | Working source | What to reuse |
| --- | --- | --- |
| Login, identity and workspace changes | [SecurityConfig](../src/main/java/com/uptrail/identity/web/SecurityConfig.java), [UptrailUserPrincipal](../src/main/java/com/uptrail/identity/service/UptrailUserPrincipal.java), [CurrentUserAdvice](../src/main/java/com/uptrail/identity/web/CurrentUserAdvice.java) | One authenticated identity shared by controllers; a selected workspace does not grant a role |
| Who approves an employee's request? | [ApprovalAssignment](../src/main/java/com/uptrail/organisation/domain/ApprovalAssignment.java), [RoutingAdminService](../src/main/java/com/uptrail/admin/service/RoutingAdminService.java) | One current manager per employee, validated assignment and an explicit option for reassigning pending records |
| Keep the approver after routing changes | [CourseApplication](../src/main/java/com/uptrail/application/domain/CourseApplication.java), [ApplicationCommandService](../src/main/java/com/uptrail/application/service/ApplicationCommandService.java) | Copy the approver onto an application at submission; retain its applicant, decision author and history |
| Employees submit and edit with MVC | [EmployeeApplicationController](../src/main/java/com/uptrail/application/web/EmployeeApplicationController.java), [application form](../src/main/resources/templates/employee/applications/form.html) | GET form, bound POST, server validation errors and redirect after success |
| Category, dates, days, budget and overlaps | [ApplicationEvaluator](../src/main/java/com/uptrail/application/service/ApplicationEvaluator.java), [TrainingDayCalculator](../src/main/java/com/uptrail/entitlement/domain/TrainingDayCalculator.java) | Shared validation on every write; half days only for internal training; exclude weekends and holidays |
| Approve or reject with a required reason | [ManagerApprovalController](../src/main/java/com/uptrail/approval/web/ManagerApprovalController.java), [ApplicationCommandService](../src/main/java/com/uptrail/application/service/ApplicationCommandService.java) | POST decision, assigned-manager check, pending-status check, reason and decision metadata |
| Manager worklist and decision support | [ManagerApplicationService](../src/main/java/com/uptrail/approval/service/ManagerApplicationService.java), [manager review](../src/main/resources/templates/manager/review.html) | Group by employee; show annual usage and other team courses in the same period |
| Subordinate history and access scope | [ManagerTeamController](../src/main/java/com/uptrail/approval/web/ManagerTeamController.java), [AccessScopePolicy](../src/main/java/com/uptrail/organisation/service/AccessScopePolicy.java) | Current-year history for the appropriate employee; limit reads to the manager's scope |
| Fee claims, if there is time | [EmployeeClaimController](../src/main/java/com/uptrail/claim/web/EmployeeClaimController.java), [ClaimCommandService](../src/main/java/com/uptrail/claim/service/ClaimCommandService.java) | Completed external/certification course, receipt and certificate, decision reason |
| Startup and database configuration | [application.yml](../src/main/resources/application.yml), [Docker Compose](../docker-compose.yml), [migrations](../src/main/resources/db/migration/) | Matching schema/entity names, environment-based credentials and reproducible sample data |

## Employee and manager IDs: settle the meaning once

The important distinction is the database identity versus the employee's business number:

| Meaning | Uptrail | CATS adaptation |
| --- | --- | --- |
| Database identity | `Employee.id` (`Long`) | Use the team's inherited `userId` and its existing numeric type |
| Employee number | `Employee.staffNo` (`String`) | Currently `Staff.staffId` (`String`) in team main; keep the team's agreed name |
| Current reporting/approval relationship | `ApprovalAssignment.employeeId -> managerId` | A `Staff -> Manager` relationship or the existing routing entity, with one source of truth |
| Approver of a submitted record | `CourseApplication.approverId` | A stored assigned approver on CourseApplication, separate from the current staff relationship |

An attribute named `monitorId` is not enough by itself. Decide whether it means the manager's database ID or business number, validate that it points to a real manager, use the same relation for course and claim routing, and filter both lists and decisions by the assigned approver. A typed relationship or foreign key makes that contract clearer than an unconstrained string.

**Managing your own application is different from approving it.** Managers use the employee workspace to submit, edit or cancel their own applications. Those applications are routed to a different manager. Uptrail routes Daniel and Priya to Grace; Grace has no configured approver and cannot submit until one is assigned. Self-assignment and self-approval are rejected. If the team decides on a different rule, record it explicitly before adapting the decision service.

Changing a staff member's current manager should not silently change who approved their old applications. Uptrail keeps an approver snapshot; pending reassignment is an explicit administrative operation.

## Adapt these differences; do not copy the entire model

- CATS uses `User -> Staff -> Manager` and `User -> Admin`. Uptrail uses an employee record, a sign-in account and role assignments. Reuse the routing and validation behaviour while preserving the team's inheritance model.
- CATS currently uses a manual session contract; Uptrail uses Spring Security's authenticated principal everywhere. For CATS, agree on one session key and one shared identity resolver for login, Staff and Manager. Do not resolve identity independently in each controller. Adding Spring Security can remain a later optional task.
- Uptrail's `/login`, `/employee/...` and `/manager/approvals` are examples. Preserve the CATS login/dashboard routes and adapt form actions and redirects together.
- Map enum constants, field names, numeric ID types and SQL column names to the actual team code. A green compile does not verify SQL seed data or login-to-course navigation.
- The ledger, audit outbox, email retry, document storage and concurrency protocol go beyond the smallest mandatory implementation. Reuse what the chosen team scope needs; they are not prerequisites for finishing the basic MVC flow.

## Use AI and small PRs to move faster

For each blocked task, give the coding assistant the relevant team files, the failing command or flow, this source map and the agreed model/route/session contract. Ask for the smallest change within the owner's module.

The author reviews the diff, understands the decision and checks these before opening a PR:

1. The whole team project compiles and its fast tests pass.
2. The actual affected flow works, including identity, ownership and invalid input; do not stop at resolving Git conflicts.
3. The patch preserves other modules and adapts reference names instead of replacing shared models or personal database settings.
4. The PR states the behaviour, checks performed and any remaining integration dependency.

A short discussion should produce a named owner and a written contract. Continue with a tested patch rather than repeatedly reopening the same decision.

## Delivery order and demonstration

Finish the complete mandatory path first:

```text
Login and shared identity
  -> Staff submits a valid application
  -> Only its assigned manager sees and decides it, with a reason
  -> Staff sees the new status and reason
  -> Update/delete/cancel/complete and current-year history
  -> Manager sees usage and overlapping approved team courses
```

Then stabilise the optional features already selected by the team. REST and a client that calls it can be added afterwards using the existing service layer.

Reserve time before presentation for one deployment rehearsal and one demonstration rehearsal. Use a disposable staging database and known sample accounts. Verify the packaged JAR against MySQL, environment settings, seed data, upload paths and restarts. A persistent deployment should not drop its database whenever the application restarts.

The 20-minute team presentation should explain responsibility split, architecture/dependencies, class or ER diagram, technologies and lessons learnt. Demonstrate one staff submission and manager decision, required-reason validation, a rejected invalid application and role-specific history. Every member should be able to explain the changes they contributed.

Uptrail's public demo is temporary: it resets its data and is shared by visitors. It is useful for reviewing the reference flow; the final demonstration and submission should use the team's own CATS application.

## Reference checks

```bash
./mvnw -B -ntp verify          # unit and MySQL integration tests, including MVC-only flows
./mvnw -B -ntp -Pe2e verify   # browser flows with and without the REST enhancements
```

The MVC reference checks cover form submission, assigned-manager approval, the manager's employee workspace, server-rendered calendar navigation, and the absence of REST controllers and browser API requests. Use these behaviours as regression goals when adapting the corresponding team modules.
