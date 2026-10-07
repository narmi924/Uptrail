# Uptrail architecture

Java 21, Spring Boot 4.1, Thymeleaf MVC, MySQL 8.4 and Flyway. HTML forms are the default; rest-enhanced enables optional JSON enhancements.

![Components](diagrams/svg/architecture.svg)

## Layers

model contains User -> Staff -> Manager, User -> Admin and the business entities/value types. repo contains Spring Data JPA repositories. service owns use cases, validation, scope and transactions. controller contains MVC pages/forms, view advice and optional REST. shared contains clocks/errors/transaction/CSV utilities; sample creates synthetic data.

Architecture tests enforce controller -> service -> repo, keep model independent and prevent REST returning entities.

## One identity

Abstract User uses SINGLE_TABLE inheritance and users.user_type. Staff extends User, Manager extends Staff, Admin extends User. They share userId/staffId/profile. Employee and UserAccount are no longer separate entities.

UserService loads credentials; EntrySuccessHandler writes a password-free subtype to session "user". CurrentUserService resolves the authenticated database identity. Business controllers receive User with binding disabled; services enforce its permissions and data scope.

The primary subtype follows Manager/Admin/Staff priority. Manager includes Staff permission. Optional administrator permission keeps both workspaces available for an existing multi-role manager; normal CATS examples use the simple hierarchy.

## Security and pages

Staff/Manager login is /employee/login, Admin /admin/login; /login remains a GET alias. Successful login opens /staff/home, /manager/home or /admin/home. Spring Security provides BCrypt, CSRF, fixation protection and session expiry; workspaces remain separate.

Client parameters/forged sessions cannot choose another identity. Services check ownership, approver, state/version; out-of-scope records return 404. Mutations/logout use POST; success redirects; errors remain beside the form. Safe redirects stay in the signed-in workspace.

CSP uses local assets and blocks inline scripts/styles/framing. Private documents use authorised download controllers.

## Business and transactions

CourseApplicationService owns submission/lifecycle. ApplicationEvaluator checks category, dates, days, holidays, overlaps, budget and routing on every write. ManagerService exposes approveCourseApplication/rejectCourseApplication through that write protocol. Reasons are mandatory.

ApprovalHierarchy is the current relationship. Applications snapshot their approver; routing changes do not rewrite history. Pending reassignment is explicit; self-assignment/approval is rejected.

READ COMMITTED writes lock the user row, annual entitlements in year order, then the versioned record; validate; write business data, ledger, audit and outbox atomically. Client request IDs prevent duplicate submissions. Concurrency tests cover budget/period and duplicate-decision races.

TrainingDayCalculator counts half-day units, excludes weekends/holidays and permits half days only for internal training. TrainingEntitlement owns annual limits; ledger separates reservations/commitments/reimbursements. Money is BigDecimal SGD; BusinessClock uses Singapore dates.

## Schema and complete features

V1-V5 remain unchanged. V6 unifies identities and aligns names while preserving person IDs, credentials, foreign keys and decisions. Hibernate validates the schema. An occupied V5-to-V6 test complements empty-database startup.

Completed-course claims/documents, manager decisions, administrator reimbursement, private storage, reports/CSV, audit, reconciliation and outbox retries remain available. Mail runs after commit, so SMTP failure cannot undo a business action.

REST preview/catalogue/calendar controllers are absent by default. rest-enhanced enables them; MVC always validates on the server.

## Verification

verify runs unit/architecture and real MySQL integration tests. e2e runs browser flows for MVC/enhanced modes. See [the CATS guide](cats-team-reference.md) for the files needed for one task.
