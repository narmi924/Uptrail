# Uptrail — Implementation Plan

**Uptrail — Staff Training & Approvals.** An implementation of the SA63 Course Application Tracking System (CATS).

This file is the single active plan. It records the approved scope, accepted decisions, architecture, tasks, traceability to course requirements, verification evidence, owner actions and the resume checkpoint. The design baseline lives read-only in `References/CATS_Design_Pack_v1/` (git-ignored) and is referenced by path and section, not copied.

Conventions:

- `[R]` course requirement, `[D]` design-pack decision, `[Q]` decision-log item, `[T]` technical reference, `[I]` implementation decision made in this plan.
- Status vocabulary: `PLANNED`, `IN_PROGRESS`, `IMPLEMENTED_NOT_VERIFIED`, `VERIFIED`, `BLOCKED`, `NOT_VERIFIED`, `OUT_OF_SCOPE`.
- Nothing is marked verified unless a command was run and its result is recorded in section 9.
- Chinese mirror: `tmp/docs/Plan.md` (local only, see `CLAUDE.md`).

---

## 1. Project status

| Field | Value |
| --- | --- |
| Plan version | 0.7.0 |
| Last updated | 2026-09-28 (Asia/Singapore) |
| Approval status | **APPROVED** — owner message of 2026-09-28 approving plan 0.1.1 with amendments A1–A7 below |
| Approved scope | Sections 3–8 of this version: the complete system (all mandatory features R01–R19, all eight optional features O01–O08, enhancements D01–D07, `training_calendar_year`). Presentation, demo script, delivery packaging and team distribution are out of scope. |
| Current phase | Phase B — M0–M5 complete; M6 fee claims next |
| Next concrete action | M6-T1: private document storage for claim receipts and payment proofs |
| Real blockers | None known. Docker availability is verified in M0-T3. |

### Amendments given with the approval

| ID | Amendment | Effect in this plan |
| --- | --- | --- |
| A1 | Plan approved | Phase B started |
| A2 | Docker Desktop started by the owner | Former blocker resolved; Testcontainers + Compose MySQL are the primary database path |
| A3 | Presentation, demo, report and delivery activities are not needed; focus on the system itself | Demo script, delivery package and rehearsal removed; R20/R21 marked `OUT_OF_SCOPE`; sample data kept because R18 requires sufficient test data and the system must be usable |
| A4 | No team work distribution; the team is the owner and Claude | Team documentation and peer-evaluation items removed |
| A5 | Public repository `https://github.com/narmi924/Uptrail`; `.gitignore` must also exclude `References/` and `tmp/` | Git workflow in 6.10; risk R-PUB in section 11 |
| A6 | Previous `DESIGN.md` deleted; pick a suitable front-end reference from the owner's local design library, copy it, desensitise it and adapt it | New `DESIGN.md` (enterprise back-office style), see I33 |
| A7 | No `Co-Authored-By: Claude` (or similar) in commits, pushes or PRs of this repository | Recorded in `CLAUDE.md` section 5 |

---

## 2. Source inventory and precedence

### 2.1 Sources read (2026-09-28)

All paths are relative to the project root. `References/` is git-ignored and never published.

| # | Source | How it was read | Notes |
| --- | --- | --- | --- |
| C1 | `References/SA63 CA Instructions.pdf` (v2.0, 11 pages) | Every page rendered to PNG and viewed; text extracted per page | Use case diagram (p4) and state diagram (p5) inspected visually |
| C2 | `References/assignment.txt` | Read in full | Deliverables text; submission platform differs from C1 (not relevant under A3) |
| C3 | `References/CATS_p6-Page-1.drawio.svg` | All 62 labels extracted | Team discussion class diagram; discussion input only |
| C4 | `References/handwrite picture.jpeg` | Viewed | Role/function notes; open question "Admin apply — can or not?" answered by Q10 |
| P0–P8 | `References/CATS_Design_Pack_v1/*.md`, `schema_blueprint.json`, `VALIDATION_STATUS.json`, `diagrams/*.puml`, `index.html` | Read in full (index.html compared by headings) | Business and technical baseline |
| X1 | Owner's local design library, file `sap.design.md` | Read in full | Source for the adapted `DESIGN.md` (I33); not copied verbatim, not committed in original form |

### 2.2 Precedence

1. Course materials (C1, C2) define the course requirements.
2. The project prompt and the approval amendments define the product name, process and accepted defaults.
3. The design pack defines the business and technical baseline.
4. This plan defines the implementation arrangement and every recorded deviation.
5. External technical documentation verifies implementation methods only.

### 2.3 Interpretation points

| Item | Sources | Resolution |
| --- | --- | --- |
| Authorization depth: C1 p5 allows URL bypass for the mandatory part | C1 p5 vs O08 | Full role and data-scope authorization is implemented because O08 is in scope; this deliberately exceeds the mandatory minimum |
| "REST Controller and REST Repository" | C1 p8 | Q11: REST controllers over services and JPA repositories, called by browser JavaScript; no Spring Data REST |
| Admin "delete Employee" | C1 p8 | Q09: deactivate when referenced; physical delete only when unreferenced |
| Holidays/entitlements "for the current year" | C1 p8 | Q16: current and next year supported (superset) |
| "Admin apply?" / manager "included CEO" | C4 | Q10: an account applies only with the EMPLOYEE role; a top-level manager without an approver is blocked with a clear message until an admin configures routing |
| Team sketch uses balances on the user and a multi-level approval field | C3 | Design pack supersedes (ledger-derived balances, single-level approval) |
| Package/class names in the pack (`sg.edu.iss.cats`, `CatsApplication`) | P1 §4.2 | `com.uptrail`, `UptrailApplication`; business terms, statuses and requirement IDs unchanged |
| Reference number prefix | P2 | Kept as `CATS-YYYY-NNNNNN` (I04) |

---

## 3. Scope and completion criteria

### 3.1 Mandatory features (in scope)

R01 three categories; R02 fee/half-day rules; R03 two login entry points with database accounts; R04 application form, server validation, Applied; R05 update → Updated; R06 delete → Deleted; R07 cancel approved → Cancelled; R08 complete after end with comments → Completed; R09 mandatory fields, future start, date order; R10 working days, weekends/holidays excluded, working-day boundaries, designation-based annual entitlement; R11 fee within remaining budget; R12 no overlap with own Applied/Updated/Approved; R13 personal current-year history with detail, decision and reason; R14 pending list grouped by subordinate name; R15 mandatory reason for approve and reject; R16 review shows days/budget used this year and other subordinates' approved courses in the same period; R17 subordinate course history; R18 Spring components, database, sufficient test data; R19 layering, encapsulation, exception handling, server validation, tests, utility classes.

### 3.2 Optional features (all eight in scope)

O01 Administration; O02 REST controller + repository with browser client (eligibility preview, calendar, catalogue search); O03 course fee claims with receipt and certificate, manager decision with reason, training ledger; O04 reporting with CSV export and print view; O05 training calendar; O06 pagination (10/20/25); O07 email interaction through an outbox; O08 Spring Security (login, roles, data scope, CSRF, private attachments).

### 3.3 Enhancements (in scope)

D01 eligibility preview; D02 reserved/committed/reimbursed ledger; D03 version conflicts and per-employee serialised writes; D04 outbox with retry; D05 audit timeline; D06 simulated reimbursement registration; D07 clean start from an empty database and restart persistence. N01 `training_calendar_year` (holiday calendar preparation and confirmation per year).

### 3.4 Out of scope

Presentation, recorded video, demo script and rehearsal, delivery packaging/upload, team work distribution and peer evaluation (R20, R21; A3, A4); microservices, message brokers, workflow engines, multi-level approval, LLM approval, OCR, mobile app, real payments, antivirus scanning, JWT/SPA/WebFlux, Redis/Kafka, cloud deployment, split reimbursements, automatic retroactive recalculation after holiday changes, cancellation fees, DRAFT application status, Spring Data REST.

### 3.5 Definition of complete

1. Every R01–R19 and O01–O08 item has a working page or endpoint, server-side rules and mapped tests with status `VERIFIED` (or an honest `NOT_VERIFIED`/`BLOCKED` with the reason).
2. Flyway migrations run from an empty MySQL database and `ddl-auto=validate` passes.
3. Transaction, authorization and concurrency rules (AC-A…AC-E, T42–T50) pass against real MySQL.
4. The main flows (employee apply → manager decide → employee complete → claim → manager decide → admin register) run end to end in a browser on sample data.
5. No page contains placeholder actions, hard-coded business data or fake numbers.
6. README commands match the project; architecture/data/state diagrams match the code and compile.
7. Known limitations and unverified items are listed truthfully.

---

## 4. Accepted decisions

### 4.1 Q01–Q16 (owner-accepted defaults; none confirmed by the lecturer)

| ID | Topic | Adopted value | Impact |
| --- | --- | --- | --- |
| Q01 | Pending reservations | APPLIED/UPDATED reserve units and budget; APPROVED converts to commitment; REJECTED/DELETED release the reservation; CANCELLED releases the commitment; COMPLETED keeps it (no release, no second debit) | ledger, balances, T22–T24, T39–T41 |
| Q02 | Single-day course | `startDate = endDate` allowed; start strictly after today; both boundaries working days | calculator, T13 |
| Q03 | Same-day AM/PM | Overlap judged on inclusive date ranges; AM/PM cannot bypass it; statuses APPLIED/UPDATED/APPROVED | overlap query, T25–T29 |
| Q04 | Cross-year | Units allocated to the year of each training date; full fee charged to the start year | application days, ledger, reports, T30, T49 |
| Q05 | Current-year history | Included when at least one `application_day.training_date` is in the current year | history queries, T61 |
| Q06 | Cancellation | Allowed while APPROVED and not COMPLETED; releases the commitment; no fee model; rule stated on the page | T39 |
| Q07 | Entitlement display | Reserved, committed and completed shown separately; available = entitlement − reserved − committed; completed is a statistic | balance panel, reports |
| Q08 | Half-day | INTERNAL: AM/PM on first and last day, middle days full; EXTERNAL/CERTIFICATION full days only | calculator, T18–T19 |
| Q09 | Delete employee | Deactivate when referenced (removed from active lists, login disabled, sessions expired, no cascade); physical delete only when unreferenced; pending items assigned to a manager must be reassigned first | staff admin, T09 |
| Q10 | Routing | One direct approver per employee; managers also hold EMPLOYEE; no self-approval; submission blocked without a valid route; approver stored on the application; explicit, audited reassignment; past decisions keep their reviewer | routing, T06–T07 |
| Q11 | REST | REST Controller → Service → JPA Repository; browser fetch for preview, calendar, catalogue; no Spring Data REST | O02 |
| Q12 | Claim revisions | One claim per application; 0 < amount ≤ approved fee; rejected claims revised and resubmitted as the same claim (revision + 1); old documents kept | T51–T57 |
| Q13 | Reimbursement | Admin registers a simulated REIMBURSED status with a `SIM-` reference; no bank integration; stated on every related page; never debits the budget again | T59–T60 |
| Q14 | Calendar | APPROVED and COMPLETED shown; CANCELLED hidden; name, title, category, dates only | T63–T65 |
| Q15 | Holiday changes | No silent rewrite; pending applications re-checked at approval; approved-not-completed applications listed for explicit handling; no automatic retroactive engine | I05, I07 |
| Q16 | Future year | Current and next business year; each touched year needs a confirmed holiday calendar and training accounts; missing configuration never means unlimited budget or "no holidays" | I05, I06 |

Q17 (submission platform) and Q18 (presentation timing) are out of scope under A3.

### 4.2 Technical decisions from the pack (A01–A08)

A01 layered monolith; A02 server-side rendering plus targeted REST; A03 annual accounts + append-only ledger; A04 READ_COMMITTED and lock order employee → accounts (ascending year) → target record; A05 catalogue as template with application snapshots; A06 transactional outbox; A07 private file storage; A08 versions locked after a smoke build.

### 4.3 Implementation decisions (I01–I45)

| ID | Decision | Rationale |
| --- | --- | --- |
| I01 | Spring Boot 4.1.1, Java 21 | Latest GA; matches the local course sample project's major version; BOM already cached |
| I02 | `mysql:8.4` for Compose and Testcontainers; Compose host port 3307 | The owner's local MySQL uses 3306 and is never touched |
| I03 | No H2, no Lombok, no Spring Data REST | One database engine; explicit encapsulation; no bypass of the business layer |
| I04 | Reference number `CATS-YYYY-NNNNNN` (NNNNNN = zero-padded id, set in the same transaction) | Unique without `MAX(id)+1` |
| I05 | `training_calendar_year` (PK `calendar_year`; `status` DRAFT/CONFIRMED; `source_note`; `confirmed_by`; `confirmed_at`; `confirmed_holiday_count`; `version`; timestamps). Confirmation needs ≥1 holiday and a source note. Adding/removing a holiday date of a CONFIRMED year reverts it to DRAFT (audited, impact list shown); renaming does not | Supplement to the 16-table baseline, not in the original dictionary |
| I06 | Applications may touch only the current and next business year; every touched year must be CONFIRMED; accounts must exist for every year with units and for the start year | Q16 |
| I07 | Approval re-check: the schedule is recalculated with the current holidays; a difference refuses approval with `SCHEDULE_OUTDATED`; a DRAFT year refuses with `HOLIDAY_CALENDAR_UNCONFIRMED` | Q15 |
| I08 | `MailTransport` with SMTP (Mailpit locally, GreenMail in tests) and a file sink (`var/mail-capture/*.eml`); worker every 10 s, lease 60 s, exponential backoff, max 5 attempts → FAILED, manual retry | A06 |
| I09 | Documents under a private root (`var/documents/`), random keys, magic-byte detection for PDF/PNG/JPEG, 5 MB limit, SHA-256, temp-then-final write, deletion on rollback, orphan sweep; no antivirus | A07, OWASP S7 |
| I10 | In-house CSV writer (RFC 4180 quoting, UTF-8 BOM, formula-injection guard) | S8; tiny scope |
| I11 | Session expiry on deactivation/role change via `SessionRegistry` + `HttpSessionEventPublisher` | Single instance |
| I12 | Two security filter chains: `/admin/**` (login `/admin/login`) and default (login `/login`); shared `UserDetailsService`; delegating BCrypt encoder; wrong-entry logins fail with the generic message | R03 |
| I13 | MVC forms re-render with field errors and preserved input; `/api/**` returns the JSON error shape of P5 §3 with a correlation id | P5 |
| I14 | Balances are ledger sums; completed usage derived from COMPLETED applications; no stored balance columns | A03, Q07 |
| I15 | Designations `ADMINISTRATIVE`, `PROFESSIONAL`, `MANAGEMENT` with configurable default entitlements (10/20/20 half-day units, SGD 2,000) used only to prefill admin forms | R10 example values |
| I16 | Page sizes 10/20/25 (default 10, server maximum 50); approvals paginate by subordinate group | O06 |
| I17 | Test tiers: unit → MySQL integration (Testcontainers `mysql:8.4` or `UPTRAIL_TEST_DB_URL`) with MockMvc and real security → Playwright e2e (profile `e2e`). Integration tests fail loudly without a database | Prompt §9 |
| I18 | PUML sources in `docs/diagrams/`, compiled by a Maven profile with `plantuml-mit` (smetana layout, no Graphviz) | No local PlantUML/Graphviz |
| I19 | ArchUnit rules: controllers do not use repositories; entities are not returned from REST controllers | R19 |
| I20 | Sample data seeder (`uptrail.sample-data.enabled=true`, empty database only) | R18 |
| I21 | Idempotent create: hidden `clientRequestId` + SHA-256 of the canonical request; same key + same hash → existing application; different hash → 409 | P1 §9 |
| I22 | Every by-id read goes through `AccessScopePolicy`; foreign/unknown → 404; missing role → 403; unauthenticated API → 401 JSON | P1 §5 |
| I23 | Admins do not see application bodies by default; they see what admin pages need | P1 §5 |
| I24 | Brand Uptrail, package `com.uptrail`, class `UptrailApplication` | Prompt §1 |
| I25 | Simulated reimbursement reference `SIM-YYYYMMDD-<claimId padded to 6>` | Q13 |
| I26 | Business time only from an injectable `Clock` (Asia/Singapore); fixed/mutable clocks only in tests | Prompt §6 |
| I27 | Cannot deactivate the last active admin; cannot deactivate a manager with assigned pending items unless reassigned in the same operation | Q09 |
| I28 | Singapore public holidays 2026 and 2027 from the MOM page, retrieved 2026-09-28 by automated fetch; stored with source and retrieval date; must be re-checked manually; test fixtures labelled `FIXTURE` | Prompt §10 |
| I29 | Sessions are bound to the entry used: admin login adds authority `ENTRY_ADMIN`, staff login adds `ENTRY_STAFF`; `/admin/**` needs `ROLE_ADMIN` + `ENTRY_ADMIN`; staff pages need `ENTRY_STAFF` + the role; the calendar accepts both | Makes the two entry points real separate workspaces |
| I30 | `client_request_id` and `create_request_hash` stored as `VARCHAR(36)`/`VARCHAR(64)` instead of `CHAR` | Avoids Hibernate schema-validation type mismatch; same content |
| I31 | Git: `main` + one branch per milestone, PR merged after the milestone's verification | A5 |
| I32 | `BusinessClock` offers a thread-scoped time override used only by the sample-data seeder at startup to create historical records through the normal services; it is not reachable from HTTP or configuration | Consistent sample history without writing ledger rows by hand |
| I33 | `DESIGN.md` adapted from an enterprise back-office design description (vendor names, proprietary fonts, URLs and framework APIs removed; tokens renamed; critical text darkened for contrast) | A6 |
| I34 | Admin workspace composition controllers (dashboard, operations) live in `com.uptrail.admin.web` | Keeps module packages focused |
| I35 | Routing admin page at `/admin/routing` (not `/admin/approvals`) | Avoids confusion with manager approvals |
| I36 | The mail health indicator is disabled | SMTP availability must not mark the application DOWN; mail goes through the outbox |
| I37 | MySQL CHECK violations (error 3819) are not categorised by Spring; services validate every rule before writing, so a CHECK violation reaching the handlers is treated as a defect (500) | Database constraints are a second line of defence |
| I38 | Current-year history uses the date range (start on or before 31 Dec and end on or after 1 Jan): start and end are always counted working days, so this equals "has a training day in the year" (Q05) | Index-friendly query |
| I39 | A training provider is required for external courses and certifications; internal training defaults to "ISS (In-house)" | The course form captures the provider; claims need it |
| I40 | Milestones M2 and M3 were delivered in one branch and pull request, because the application detail page needs the M3 actions to avoid inactive buttons | Git workflow (I31) adjusted |
| I41 | Administration services live in `com.uptrail.admin.service` because they orchestrate identity, organisation, entitlement and application data; this avoids dependency cycles between modules | Extends I34 |
| I42 | Holiday date changes are two-step on the page: the first POST shows the affected applications and the draft consequence; only a confirmed second POST applies the change. Renaming a holiday keeps the year confirmed | Q15 |
| I43 | The training calendar is open to every signed-in user in both workspaces. The page renders the month on the server as a no-script fallback and the browser then loads months from `GET /api/v1/calendar`; the month picker offers a year either side of today plus the selected month | Q14; bounded page size |
| I44 | Participation report: APPROVED and COMPLETED courses of current direct reports that overlap the period; training days count only days inside the period; fees are summed once per course and only for courses starting in the period. Budget report: balances from the ledger for the selected year (previous, current or next), claim amounts grouped by the year the course started, totals over staff with an account | Q04, Q05; no double counting |
| I45 | Short administrative lists (catalogue courses and providers) are paginated in memory with the shared pager; the holiday list is bounded by one calendar year and dashboard panels show at most five rows, so they are not paginated | O06 |

---

## 5. Environment and dependency baseline

### 5.1 Local environment (checked 2026-09-28)

| Item | Result | Status |
| --- | --- | --- |
| OS | Windows 11; Git Bash and PowerShell 7; time zone UTC+8 | Verified |
| JDK | OpenJDK 21.0.12 | Verified |
| Maven | 3.9.16 (wrapper generated in M0) | Verified |
| Docker | Engine 29.2.0, Compose v5.0.2, daemon running after A2 | Verified 2026-09-28 |
| MySQL (owner's) | 8.0.46 service on 3306, not used by this project | Verified |
| Ports | 8080, 3307, 1025, 8025 free at check time | Verified |
| Node / browsers | Node 24, Chrome, Edge; Playwright Chromium cached | Verified |
| PlantUML / Graphviz | Not installed (I18) | Verified |
| GitHub | `gh` 2.86 authenticated as the repository owner; remote repository exists, empty, public | Verified |

### 5.2 Dependency versions

"Verified" = coordinates and versions confirmed in the cached Spring Boot 4.1.1 BOM or on Maven Central on 2026-09-28. Lock-down happens after the M0 build.

| Dependency | Version | Managed by | Status |
| --- | --- | --- | --- |
| spring-boot-starter-parent | 4.1.1 | — | Verified |
| Spring Framework / Security / Data | 7.0.9 / 7.1.1 / 2026.0.1 | BOM | Verified |
| Hibernate ORM / Validator | 7.4.5.Final / 9.1.3.Final | BOM | Verified |
| Thymeleaf | 3.1.5.RELEASE | BOM | Verified |
| Starters | webmvc, thymeleaf, security, data-jpa, validation, mail, flyway, actuator; test starters for webmvc, security | BOM | Verified names |
| Flyway core + flyway-mysql | 12.4.0 | BOM | Verified; MySQL 8.4 compatibility checked in M0 |
| MySQL Connector/J | 9.7.0 | BOM | Verified |
| Testcontainers (`testcontainers-mysql`) | 2.0.5 | BOM | Verified; class `org.testcontainers.mysql.MySQLContainer` |
| JUnit / Mockito / AssertJ / Awaitility | 6.0.3 / 5.23.0 / 3.27.7 / 4.3.0 | BOM | Verified |
| Bootstrap webjar (`org.webjars.npm:bootstrap`) | 5.3.8 | explicit | Verified |
| GreenMail JUnit 5 | 2.1.14 | explicit | Verified |
| ArchUnit JUnit 5 | 1.5.1 | explicit | Verified |
| Playwright for Java (profile `e2e`) | 1.63.0 | explicit | Verified |
| PlantUML MIT (profile `diagrams`) | 1.2026.8 | explicit | Verified |
| Maven Wrapper | 3.3.4 | — | Verified |
| Images | `mysql:8.4`, `axllent/mailpit:v1.31` | compose | Tags verified |

---

## 6. Implementation architecture

### 6.1 Repository layout

```text
uptrail/
  Plan.md  CLAUDE.md  DESIGN.md  README.md
  pom.xml  mvnw  mvnw.cmd  .mvn/  .gitignore  .gitattributes  .editorconfig  .env.example
  docker-compose.yml                 # mysql:8.4 on 3307, mailpit on 1025/8025
  docs/                              # architecture.md, data-model.md, api.md, diagrams/*.puml (+ compiled svg)
  src/main/java/com/uptrail/
    UptrailApplication.java
    shared/{config,error,time,web,csv}
    identity/{domain,repository,service,web}
    organisation/{domain,repository,service,web}
    catalogue/{domain,repository,service,web,api}
    entitlement/{domain,repository,service,web}
    application/{domain,repository,service,web,api}
    approval/{service,web}
    claim/{domain,repository,service,web}
    reporting/{service,web,api}
    notification/{domain,repository,service}
    audit/{domain,repository,service}
    admin/web
    sample/
  src/main/resources/
    application.yml  application-dev.yml
    db/migration/V1__…V5__*.sql
    templates/{layout,fragments,auth,employee,manager,admin,shared,error}
    static/{css/uptrail.css, js/*.js}
    sample/ (holiday data with source notes, sample receipt/certificate files marked SAMPLE)
  src/test/java/com/uptrail/…        # unit, integration (MySQL), e2e (Playwright), architecture
  tmp/docs/  References/  var/       # git-ignored
```

### 6.2 Module responsibilities and call rules

- Controllers bind DTOs, call one service method, build the view model or JSON; they never use repositories.
- Services own transactions, validation, scope checks (`AccessScopePolicy`), the lock protocol and audit/ledger/outbox writes.
- Repositories are Spring Data JPA interfaces with a few projections and `@Lock(PESSIMISTIC_WRITE)` finders.
- Entities change state only through intention-revealing methods; no public setters for status, approver, fee snapshot or version.
- Lifecycle commands: `ApplicationCommandService` (submit, update, delete, cancel, complete, approve, reject, reassign) and `ClaimCommandService` (submit, resubmit, decide, register reimbursement). `approval` holds manager pages and queue queries.
- Pages, REST and CSV share the same query services.
- Core classes with focused tests: `TrainingDayCalculator`, `ApplicationEvaluator`, `ApplicationStatus` transitions, `EntitlementService`, `AccessScopePolicy`, `ClaimPolicy`, `CsvWriter`, `DocumentTypeDetector`.

### 6.3 Data model (17 tables)

The 16 baseline tables follow `02_Data_Dictionary.md` (types, keys, unique constraints, indexes) with these notes: ids `BIGINT AUTO_INCREMENT`; timestamps `DATETIME(6)` in UTC; money `DECIMAL(12,2)`; JSON columns mapped as strings; CHECK constraints where MySQL enforces them; all FKs `ON DELETE RESTRICT`; request id/hash as VARCHAR (I30).

Added table (supplement, not in the original dictionary):

```text
training_calendar_year
  calendar_year            SMALLINT     PK
  status                   VARCHAR(16)  NOT NULL   -- DRAFT | CONFIRMED
  source_note              VARCHAR(400) NOT NULL
  confirmed_by             BIGINT       NULL FK employee(id)
  confirmed_at             DATETIME(6)  NULL
  confirmed_holiday_count  INT          NULL
  version                  BIGINT       NOT NULL
  created_at / updated_at  DATETIME(6)  NOT NULL
```

A holiday can be added only to a year that has a `training_calendar_year` row.

Migrations: V1 identity/organisation → V2 catalogue, holidays, calendar years, training accounts → V3 applications, application days → V4 audit events, ledger → V5 claims, documents, outbox. `ddl-auto=validate` everywhere.

### 6.4 State machines

- Application: `APPLIED/UPDATED → UPDATED` (owner update), `→ DELETED` (owner), `→ APPROVED | REJECTED` (assigned manager, reason, expectedVersion); `APPROVED → CANCELLED` (owner, reason) or `→ COMPLETED` (owner, today > endDate, comments). Terminal records are kept.
- Claim: `SUBMITTED → APPROVED | REJECTED` (assigned manager, reason); `REJECTED → SUBMITTED` (owner resubmits, revision + 1, both documents again); `APPROVED → REIMBURSED` (admin, once).
- Calendar year: `DRAFT → CONFIRMED` (admin); `CONFIRMED → DRAFT` on holiday date change (audited).
- Outbox: `PENDING → SENDING → SENT | PENDING (retry) | FAILED`; admin retry `FAILED → PENDING`.

### 6.5 Permissions and data scope

| Capability | Staff entry, EMPLOYEE | Staff entry, MANAGER | Admin entry, ADMIN |
| --- | --- | --- | --- |
| Own applications, entitlement, claims | yes | yes (manager also holds EMPLOYEE) | no (use the staff entry if the account also has EMPLOYEE) |
| Decide applications and claims | no | items assigned to self, never own | no |
| Subordinate history and reports | no | current direct subordinates | no |
| Training calendar | yes | yes | yes |
| Staff, roles, routing, entitlements, catalogue, holidays, calendar years | no | no | yes |
| Reimbursement registration, audit, outbox | no | no | yes |
| Attachment download | own claims | claims assigned to self | claims awaiting or after reimbursement |

404 for foreign/unknown records, 403 for missing role or wrong workspace, 401 JSON for unauthenticated API calls, CSRF everywhere, POST for every state change including logout.

### 6.6 Transactions, locks and ledger effects

Every write path (including admin adjustments, reassignment, cancellation, reimbursement):

1. `@WriteTransaction` = `@Transactional(isolation = READ_COMMITTED)`; the pool default is also READ_COMMITTED.
2. Lock the applicant's `employee` row, then the involved `training_account` rows in ascending year (union of old and new years on update), then the target application/claim; compare `expectedVersion`.
3. Re-validate inside the locks (identity, route, status, calendar years, schedule, overlap, units, budget, idempotency).
4. Write the record, application days, one `audit_event`, one net `training_ledger` row per involved account (`UNIQUE(event_id, account_id)`) and `email_outbox` rows; commit.
5. Mail is sent later by the worker. Lock timeouts/deadlocks map to 503 `TEMPORARY_CONTENTION`.

Ledger deltas: Submit +reserved; Update new − old per account; Approve −reserved +committed; Reject/Delete −reserved; Cancel −committed; Complete none; Claim decisions none; Register reimbursement +reimbursed.

### 6.7 Front end

Follows `DESIGN.md`: one Thymeleaf layout (top bar, side navigation, content), fragments for pagination, status badges, message strips, timeline, balance panel and confirmation dialogs; Bootstrap 5.3.8 from the webjar plus `uptrail.css`; vanilla JS modules `csrf.js`, `preview.js`, `calendar.js`, `catalogue-search.js`, `confirm.js`; no CDN. Every page handles normal, empty, validation, load-failed, forbidden, not-found and stale-version states.

### 6.8 Endpoints

Staff: `/login`, `POST /logout`, `/employee/dashboard`, `/employee/applications` (+ `/new`, `/{id}`, `/{id}/edit`, `POST` create, `/{id}/update|delete|cancel|complete`), `/employee/entitlement`, `/employee/claims` (+ `/new`, `/{id}`, `POST` create, `/{id}/resubmit`), `/manager/approvals`, `/manager/applications/{id}` (+ `/decision`), `/manager/claims/{id}` (+ `/decision`), `/manager/team/history`, `/manager/team/applications/{id}`, `/manager/reports` (+ `/training.csv`, `/budget.csv`), `/calendar`, `/claims/documents/{id}`.

Admin: `/admin/login`, `POST /admin/logout`, `/admin/dashboard`, `/admin/staff…`, `/admin/routing…`, `/admin/entitlements…`, `/admin/catalogue…`, `/admin/holidays…`, `/admin/reimbursements`, `/admin/claims/{id}` (+ `/reimburse`), `/admin/claims/documents/{id}`, `/admin/operations` (+ `/admin/outbox/{id}/retry`).

API (session + CSRF): `POST /api/v1/applications/preview`, `GET /api/v1/calendar?month=YYYY-MM[&category=]`, `GET /api/v1/catalogue?q=&category=`.

### 6.9 Differences from the design pack

Naming (I24); `training_calendar_year` (I05–I07); lifecycle commands in command services; concrete versions (section 5); mail file sink (I08); in-house CSV (I10); entry-bound sessions (I29); VARCHAR request ids (I30); routing URL (I35); admin composition package (I34); read-only ledger consistency check on the operations page; sample data instead of a demo dataset (A3).

### 6.10 Git workflow

`main` is created with the plan, conventions and design system, then each milestone is developed on its own branch, verified, pushed and merged through a pull request. `References/`, `tmp/`, `var/`, `target/` and `.env` are ignored. No force-push to `main`. Commit and PR texts carry no Claude attribution (A7).

---

## 7. Milestones and tasks

Each task ends with the related tests, fixes, and an update of sections 8, 9 and 12. Target dates are sequencing targets only.

### Milestone status

| Milestone | Status | Notes |
| --- | --- | --- |
| M0 Engineering baseline | VERIFIED (except M0-T5 API error JSON, verified with the first REST endpoint in M2) | Evidence in 9.2 |
| M1 Identity, roles, entry points | VERIFIED | Two chains with entry-bound sessions (I29), CSP, session expiry, safe redirects, scope policy, sample organisation; evidence in 9.2 |
| M2 Rules, accounts, ledger, submission | VERIFIED | Evaluator shared by preview and submit; lock protocol; idempotency; preview and catalogue APIs; employee pages. The administrator side of M2-T2 (holiday maintenance) moves to M4-T5 |
| M3 Manager review and lifecycle | VERIFIED | Update/delete/cancel/complete/approve/reject with ledger effects; approval-time schedule re-check (I07); grouped worklist, review page, team history; sample activity in every status; concurrency tests |
| M4 Administration | VERIFIED | Staff and roles with deactivation guards and session expiry, routing with explicit audited reassignment and team moves, entitlements with lower bounds and defaults, catalogue, holiday calendars with impact confirmation, bundled list import |
| M5 Calendar, pagination, reports, CSV | VERIFIED | Calendar API and page (I43), participation and budget reports with print view (I44), CSV exports sharing the report queries, pager on the remaining long lists (I45) |
| M6 Fee claims | PLANNED | — |
| M7 Email, audit, operations | PLANNED | — |
| M8 Hardening | PLANNED | — |
| M9 Documentation and final verification | PLANNED | — |

### M0 — Engineering baseline

| Task | Deliverables | Acceptance | Verification |
| --- | --- | --- | --- |
| M0-T1 Skeleton and repository | `pom.xml`, Maven Wrapper, `UptrailApplication`, `application*.yml`, `.gitignore`, `.gitattributes`, `.editorconfig`, `.env.example`, README skeleton; git repository, `main` pushed | Compiles; remote has `main` | `./mvnw -q -DskipTests compile`, `git ls-remote` |
| M0-T2 Migrations and entities | `V1…V5`, entities for all 17 tables, repositories | Migrations apply to empty MySQL; schema validation passes | `SchemaMigrationIT` |
| M0-T3 Test infrastructure | Testcontainers base class (or `UPTRAIL_TEST_DB_URL`), table cleaner, mutable test clock, GreenMail, ArchUnit, surefire/failsafe split, `e2e` and `diagrams` profiles | Unit and IT run; IT fails loudly without a database | `./mvnw verify` |
| M0-T4 Runtime configuration | `docker-compose.yml`, dev profile, run instructions | App starts on a clean database; health OK | manual run + `curl /actuator/health` |
| M0-T5 Shared kernel and layout | Clock, error codes, exception handlers (MVC + API), correlation id, layout, `uptrail.css` per `DESIGN.md`, fragments, error pages | Error pages render; API error JSON shape correct | IT |

### M1 — Identity, roles, entry points

| Task | Deliverables | Acceptance | Verification |
| --- | --- | --- | --- |
| M1-T1 Identity domain | accounts, roles, principal, `UserDetailsService` | Seeded user logs in; disabled/inactive cannot | IT T01–T02 |
| M1-T2 Security | Two chains (I12, I29), CSRF, API 401/403 JSON, POST logout, safe `next` redirect, session registry | Wrong-entry login fails; CSRF-less POST rejected; admin/staff workspaces separated | IT T01, T03, T08, T09 |
| M1-T3 Login pages, dashboards, navigation | P01, P02 shells with real queries | Pages render with real data | IT + manual |
| M1-T4 Access scope policy | `AccessScopePolicy` | Foreign record → 404 on page and API | IT T04 |
| M1-T5 Sample data base | Seeder: calendar years 2026/2027 with holidays, providers, catalogue, staff with roles, routing, accounts | Seeder idempotent on empty DB only | IT |

### M2 — Calendar rules, accounts, ledger, submission

| Task | Deliverables | Acceptance | Verification |
| --- | --- | --- | --- |
| M2-T1 `TrainingDayCalculator` | Working days, holidays, sessions, per-year allocation, excluded dates | Parameterised cases pass | unit T13–T21, T30 |
| M2-T2 Holidays and calendar years | Services for years, holidays, confirm/reopen, impact query | Rules of I05 hold | IT |
| M2-T3 Accounts and balances | `EntitlementService` snapshot and ascending-year locks | Snapshot = ledger sums; missing account error | IT T31 |
| M2-T4 Audit, ledger, outbox primitives | `AuditService`, `LedgerService`, `NotificationService.enqueue` | Same-transaction writes; duplicates rejected | IT |
| M2-T5 Submit and preview | Full protocol, preview without writes, reference number, idempotency | AC-A, AC-C, idempotent replay | IT T10–T12, T22–T25, T27, T31, T42, T43, T46–T48 |
| M2-T6 Employee pages and APIs | Form with live eligibility panel, catalogue search, My Applications, detail with timeline and allowed actions | Browser fetch used; failure shows "Unable to check now"; current-year list only | IT T61 + e2e |

### M3 — Manager review and lifecycle

| Task | Deliverables | Acceptance | Verification |
| --- | --- | --- | --- |
| M3-T1 Update and delete | Net ledger diff, self-excluded overlap, union of years, version check, soft delete | AC-B; cross-year edit releases old year | IT T26, T29, T34, T40, T49 |
| M3-T2 Approve and reject | Reason, assignment, no self-approval, expectedVersion, I07 re-check, ledger conversion/release, outbox | AC-E; duplicate POST → 409 | IT T06, T07, T35, T36, T41, T44, T45 |
| M3-T3 Cancel and complete | APPROVED only; reason; release; completion after end with comments | Early completion refused | IT T37–T39 |
| M3-T4 Manager pages | Grouped approvals, review page (balances, same-period team courses, decision panel), team history | Non-subordinate → 404; groups not split across pages | IT T62, T07 |
| M3-T5 State machine coverage | Parameterised legal/illegal transitions | All transitions covered | unit T33, T34 |
| M3-T6 Sample applications | Applications across all states, including past approved/completed and cross-year | Ledger reconciles | IT |

### M4 — Administration

| Task | Deliverables | Acceptance | Verification |
| --- | --- | --- | --- |
| M4-T1 Staff and roles | Search, create, edit, roles, password reset, deactivate/reactivate, delete when unreferenced, guards (I27), session expiry | Guards hold | IT T09 |
| M4-T2 Routing | Assign manager, cycle/self/role checks, audited reassignment of pending items | Cycle rejected; event recorded | IT |
| M4-T3 Entitlements | Current/next year accounts, designation defaults, lower bound, reason + audit | Lowering below usage refused | IT T32 |
| M4-T4 Catalogue | Categories (name/description), providers, courses (INTERNAL fee 0) | Snapshots unchanged by edits | IT |
| M4-T5 Holidays and calendar years | Year selector, create year, holiday CRUD, impact preview, confirm/reopen | DRAFT year blocks submission | IT |
| M4-T6 Admin dashboard | Real counts (pending reimbursements, failed emails, unconfirmed years, staff without route/account) | Real queries only | IT |

### M5 — Calendar, pagination, reports, CSV

| Task | Deliverables | Acceptance | Verification |
| --- | --- | --- | --- |
| M5-T1 Training calendar | API + page (month grid, list, previous/next, month select, category) | Cancelled hidden, completed kept, no sensitive fields | IT T63–T65 |
| M5-T2 Pagination | Shared fragment and resolver on all lists | Filters kept; size clamped | IT T70 |
| M5-T3 Reports | Participation and budget/claims reports, filter summary, print view | Subordinates only; selected-year allocation | IT T66 |
| M5-T4 CSV | CSV writer and endpoints sharing the report queries | CSV equals page; escaping and formula guard | unit + IT T67–T69 |

### M6 — Fee claims

| Task | Deliverables | Acceptance | Verification |
| --- | --- | --- | --- |
| M6-T1 Document storage | Storage service, detector, rollback cleanup, orphan sweep, authorised download | Bad type/size refused; no orphans | IT T71–T73, T05 |
| M6-T2 Claim submit/resubmit | Eligibility, revisions, documents | Internal/unfinished/duplicate refused | IT T51–T57 |
| M6-T3 Manager claim decisions | Claims tab, review page, decision | Reason required; assigned manager only | IT T58 |
| M6-T4 Reimbursement registration | Admin queue, `SIM-` reference, +reimbursed ledger, idempotent | AC-D; second registration → 409 | IT T59, T60 |
| M6-T5 Employee claim pages | List, form, detail with revisions and documents | Registration notice shown | IT |

### M7 — Email, audit, operations

| Task | Deliverables | Acceptance | Verification |
| --- | --- | --- | --- |
| M7-T1 Outbox worker and transports | Worker, SMTP and file transports, templates, login deep link | SMTP down → business succeeds; retried; later SENT | IT T74–T76 |
| M7-T2 Operations page | Audit search, outbox list and retry, ledger consistency check | No state edits except retry | IT |
| M7-T3 Timelines and ledger view | Timelines on detail pages; employee ledger page | Every balance change traceable | IT |
| M7-T4 Sample claims and mail | Claims in every state, outbox rows | Ledger reconciles | IT |

### M8 — Hardening

| Task | Deliverables | Acceptance | Verification |
| --- | --- | --- | --- |
| M8-T1 Authorization matrix | Parameterised role/ownership tests over pages, APIs, CSV, downloads | No bypass | IT |
| M8-T2 Concurrency | Barrier-synchronised tests on MySQL (budget race, same period, duplicate approve, duplicate reimburse) | Exactly one success; balances correct | IT T42–T44, T60 |
| M8-T3 Ledger reconciliation | Recompute vs ledger over sample data | Zero difference | IT T50 |
| M8-T4 Browser e2e | Playwright flows for the three roles; screenshots in `target/e2e/` | Flows pass | `./mvnw -Pe2e verify` |
| M8-T5 Clean rebuild and restart | Empty-DB start, restart, data kept, offline static assets | Commands work as documented | manual, recorded in section 9 |
| M8-T6 UI state review | Normal/empty/error/forbidden/stale, narrow width, print | No placeholders or fake numbers | manual + screenshots |

### M9 — Documentation and final verification

| Task | Deliverables | Acceptance | Verification |
| --- | --- | --- | --- |
| M9-T1 Documentation | README (run, test, accounts, troubleshooting), `docs/architecture.md`, `docs/data-model.md`, `docs/api.md`, third-party notices; Chinese mirrors | Commands verified by running them | manual |
| M9-T2 Diagrams | PUML (architecture, data model, application/claim/calendar-year states, submit sequence) compiled to SVG | Diagrams compile and match code | `./mvnw -Pdiagrams generate-resources` |
| M9-T3 Final verification | Full test run, e2e, packaging of the executable JAR, final plan status | Section 9 has real results | full command set |

---

## 8. Requirement-to-implementation traceability

### 8.1 Mandatory

| ID | Requirement (PDF) | Pages / endpoints | Modules / data | Tests | Status |
| --- | --- | --- | --- | --- | --- |
| R01 | Three categories (p3, p5) | form, catalogue admin | catalogue, `course_category` | category rules, catalogue IT | PLANNED |
| R02 | Internal free/half-day; others fee/full-day | form, preview | calculator, evaluator | T18–T21 (TrainingDayCalculatorTest, ApplicationSubmissionIT) | VERIFIED |
| R03 | Two entry points, DB credentials (p5) | `/login`, `/admin/login` | identity, security | T01–T03 (AuthenticationIT) | VERIFIED |
| R04 | Form, validation, Applied (p6) | `/employee/applications/new` | application | T10–T32, AC-A (ApplicationSubmissionIT) | VERIFIED |
| R05 | Update → Updated | `/{id}/edit`, `/{id}/update` | application | T29, T34, T49, AC-B (ApplicationLifecycleIT) | VERIFIED |
| R06 | Delete → Deleted | `/{id}/delete` | application, ledger | T40 | VERIFIED |
| R07 | Cancel → Cancelled | `/{id}/cancel` | application, ledger | T39 | VERIFIED |
| R08 | Complete after end with comments | `/{id}/complete` | application | T37, T38 | VERIFIED |
| R09 | Mandatory fields, future start, date order | form, preview | evaluator, calculator | T10–T13 | VERIFIED |
| R10 | Working days, holidays, boundaries, entitlement | preview, admin entitlements/holidays | calculator, entitlement, catalogue | T14–T19, T24, T30, T31, AdministrationIT | VERIFIED |
| R11 | Fee within remaining budget | submit/update | entitlement, ledger | T22, T23, T42 (ConcurrencyIT) | VERIFIED |
| R12 | No overlap with own active applications | submit/update | overlap query | T25–T29, T43 | VERIFIED |
| R13 | Current-year personal history with decision/reason | `/employee/applications`, `/{id}` | query service | T61 (page tests) | VERIFIED |
| R14 | Pending grouped by subordinate | `/manager/approvals` | approval queue | T62 (page test) | VERIFIED |
| R15 | Reason mandatory for both decisions | `/manager/applications/{id}/decision` | command service | T35, T36 | VERIFIED |
| R16 | Used days/budget and same-period team courses on review | `/manager/applications/{id}` | entitlement, team query | review page test | VERIFIED |
| R17 | Subordinate history | `/manager/team/history` | query service | T07, T61 | VERIFIED |
| R18 | Spring, database, sufficient test data | all | Flyway, sample seeder | T77, seeder IT | PLANNED |
| R19 | Layering, encapsulation, exceptions, validation, tests, utilities | all | ArchUnit, handlers | architecture test, suite | PLANNED |
| R20 | Team contribution, peer evaluation | — | — | — | OUT_OF_SCOPE (A4) |
| R21 | Presentation and deliverable packaging | — | — | — | OUT_OF_SCOPE (A3) |

### 8.2 Optional

| ID | Feature | Pages / endpoints | Modules / data | Tests | Status |
| --- | --- | --- | --- | --- | --- |
| O01 | Administration | `/admin/staff`, `/admin/routing`, `/admin/entitlements`, `/admin/catalogue`, `/admin/holidays` | admin services over organisation, identity, catalogue, entitlement | T09, T32, AdministrationIT (16) | VERIFIED |
| O02 | REST + client | `/api/v1/applications/preview`, `/api/v1/calendar`, `/api/v1/catalogue`; JS modules | application/api, reporting/api, catalogue/api | API IT, e2e | IN_PROGRESS (all three APIs verified by IT and in the browser; e2e in M8) |
| O03 | Fee claims + ledger | `/employee/claims…`, `/manager/claims/{id}`, `/admin/reimbursements` | claim, ledger | T51–T60, T71–T73, AC-D | PLANNED |
| O04 | Reporting + CSV | `/manager/reports`, `/manager/reports/training.csv`, `/manager/reports/budget.csv` | reporting, `shared.csv` | T66–T69 (CalendarAndReportIT, CsvWriterTest) | VERIFIED |
| O05 | Training calendar | `/calendar`, `/api/v1/calendar` | reporting | T63–T65 (CalendarAndReportIT) | VERIFIED |
| O06 | Pagination | all lists (I45) | shared | T62, T70 (team history, report and catalogue paging tests) | VERIFIED |
| O07 | Email | outbox, templates, operations page | notification | T74–T76 | PLANNED |
| O08 | Spring Security | chains, scope policy, CSRF, private files | identity, shared | T01–T09, T71, T72 | IN_PROGRESS (sign-in, roles, workspaces, CSRF, session expiry, headers verified; private files in M6) |

### 8.3 Enhancements

| ID | Item | Tasks | Tests | Status |
| --- | --- | --- | --- | --- |
| D01 | Eligibility preview | M2-T5/T6 | preview IT, browser check | VERIFIED |
| D02 | Separate reserved/committed/reimbursed | M2-T3/T4 | T50 | PLANNED |
| D03 | Versions and serialised writes | M2-T5, M3-T2, M8-T2 | T42–T48 (ConcurrencyIT, lifecycle, idempotency) | VERIFIED |
| D04 | Outbox and retry | M7-T1 | T74–T76 | PLANNED |
| D05 | Audit timeline | M7-T3 | IT | PLANNED |
| D06 | Reimbursement registration | M6-T4 | T59, T60 | PLANNED |
| D07 | Clean start, restart persistence | M8-T5 | T77, T78 | PLANNED |
| N01 | `training_calendar_year` | M2-T2, M4-T5 | confirm/reopen, DRAFT blocks submission, `SCHEDULE_OUTDATED` (AdministrationIT, ApplicationLifecycleIT) | VERIFIED |

---

## 9. Verification and evidence

### 9.1 Commands

| Purpose | Command | Prerequisite |
| --- | --- | --- |
| Compile | `./mvnw -q -DskipTests compile` | JDK 21 |
| Unit tests | `./mvnw test` | — |
| All tests incl. MySQL | `./mvnw verify` | Docker, or `UPTRAIL_TEST_DB_URL`/`_USER`/`_PASSWORD` for an isolated database |
| Browser e2e | `./mvnw -Pe2e verify` | as above + network on first browser download |
| Diagrams | `./mvnw -Pdiagrams generate-resources` | network on first download |
| Local services | `docker compose up -d` | Docker |
| Run with sample data | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` | Compose services running |
| Package | `./mvnw -DskipTests package` | — |

### 9.2 Evidence log

| Date/time (SGT) | Command | Result | Notes |
| --- | --- | --- | --- |
| 2026-09-28 | `docker info` | Engine 29.2.0 reachable | A2 confirmed |
| 2026-09-28 | `./mvnw -q -DskipTests compile` | Success | Boot 4.1.1 stack resolved; key classes located on the test classpath (Testcontainers 2 `org.testcontainers.mysql.MySQLContainer`, Boot 4 `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, Jackson 3) |
| 2026-09-28 | `./mvnw verify -Dit.test=SchemaMigrationIT` (first run) | 6 tests, 2 failures | CHECK violations surface as uncategorized SQL exceptions (MySQL error 3819); assertions changed to require the specific constraint name |
| 2026-09-28 | `./mvnw verify` | BUILD SUCCESS: unit 6/6 (ArchitectureTest), IT 9/9 (SchemaMigrationIT 6, RuntimeBaselineIT 3) | Migrations V1–V5 on empty `mysql:8.4`; Hibernate `ddl-auto=validate` passed for all 17 tables; health first returned 503 because of the mail health indicator (fixed, I36) |
| 2026-09-28 | `docker compose up -d`; `java -jar target/uptrail-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev`; `curl /actuator/health` | Compose MySQL and Mailpit healthy; 5 migrations applied; health `UP`; `/employee/dashboard` → 403 under the baseline lock | M0-T4 |
| 2026-09-28 | `./mvnw verify` (M1) | First runs: 2 failures (non-HTML requests got the JSON entry point; test URL pattern) fixed by an explicit login entry point for non-API paths. Final: BUILD SUCCESS, unit 20/20 (ArchitectureTest 6, TrainingDayCalculatorTest 14), IT 29/29 (AuthenticationIT 14, AccessScopePolicyIT 4, SampleOrganisationIT 2, RuntimeBaselineIT 3, SchemaMigrationIT 6) | T01–T04, T08, T09, T13–T21, T30 |
| 2026-09-28 | JAR with `dev` profile + `agent-browser` at 1366×768 | Sample data loaded (21 accounts); staff sign-in → staff dashboard; admin sign-in → admin dashboard with real counts; screenshots in `target/screens/` (not committed) | Found and fixed: grey input background |
| 2026-09-28 | `./mvnw verify` (M2–M3) | First runs: test compile errors, a fragment named `body` clashing with the `<body>` selector, a test using a Sunday date; all fixed. Final: BUILD SUCCESS, unit 41/41, IT 67/67 (ApplicationSubmissionIT 18, ApplicationLifecycleIT 15, ConcurrencyIT 4, SampleActivityIT 1, earlier suites) | T10–T31, T33–T45, T47–T50, AC-A–AC-E |
| 2026-09-28 | `docker compose down -v` (this project's dev volume only), dev JAR, `agent-browser` | 18 sample applications created through the services; form with catalogue search (`GET /api/v1/catalogue`) and live preview (`POST /api/v1/applications/preview`) seen in the browser network log; dashboard, manager worklist and review page checked | Found and fixed: dates in the browser locale (UI locale fixed to English), wrapped reference numbers, side-navigation background |
| 2026-09-28 | `./mvnw verify` (M4) | First run: 1 failure (test expected 3 applicants, correct value 2). Final: BUILD SUCCESS, unit 41/41, IT 83/83 (AdministrationIT 16 plus earlier suites) | T09, T32, routing, catalogue snapshot, holiday rules |
| 2026-09-28 | Restart of the dev JAR on the existing database; `agent-browser` on all admin pages | Seeder skipped because data existed (restart persistence); staff, routing, entitlements, catalogue, holidays pages render with real data and no page errors | Found and fixed: cluttered inline editors on holidays and catalogue (moved into per-row edit popovers); inline styles blocked by CSP replaced with classes; approver selection by id |
| 2026-09-28 | `./mvnw verify -Dit.test=CalendarAndReportIT -Dtest=CsvWriterTest` (M5, first run) | 11 IT, 1 failure | The budget CSV printed empty claim totals as `0` instead of `0.00`; report totals now start from a two-decimal zero |
| 2026-09-28 | `./mvnw verify` (M5) | BUILD SUCCESS: unit 50/50 (CsvWriterTest 9 added), IT 95/95 (CalendarAndReportIT 11, AdministrationIT 17 plus earlier suites) | T63–T70 |
| 2026-09-28 | Dev JAR on the existing database; `agent-browser` as a manager and as the administrator | Calendar month grid, list view, previous/next, month and category filters load through `GET /api/v1/calendar` without page errors; both report tabs render with sample data; both CSV downloads return `attachment` with `text/csv;charset=UTF-8` and the page rows | Found and fixed: the budget table overflowed the screen (regrouped into day, budget and claim column groups); tabs hidden when printing; unencoded category in calendar links |

Rules: failures recorded with output; skipped or blocked runs marked `NOT_VERIFIED`/`BLOCKED`; no pre-filled counts, coverage or timings.

---

## 10. Sample data and runtime

- Loaded by the seeder only when `uptrail.sample-data.enabled=true` (on in the `dev` profile) and the database has no employees. All names and addresses are fictional (`@example.com`); sample documents are labelled SAMPLE.
- Content: one administrator, managers (one of them reporting to another manager), a top-level manager without an approver, about 18 employees, inactive employees, an employee without a next-year account, an employee without an approver; applications in every status, including single-day, half-day and cross-year cases; claims in every status; accounts for the current and next year; calendar years 2026 and 2027 confirmed with source notes.
- Historical records are created through the normal services with the seeder-only time override (I32).
- Account list and the shared sample password are documented in the README only.
- Local mail goes to Mailpit (`http://localhost:8025`) or to `var/mail-capture/` when the file transport is selected; never to real recipients.
- Rebuild from empty: `docker compose down -v` (removes only this project's volumes), `docker compose up -d`, run with the `dev` profile.

### 10.1 Public holiday data

Retrieved from the Ministry of Manpower public holidays page on 2026-09-28 by automated fetch; the team must re-check the lists against the page (I28).

2026: 01-01 New Year's Day; 02-17, 02-18 Chinese New Year; 03-21 Hari Raya Puasa (Sat); 04-03 Good Friday; 05-01 Labour Day; 05-27 Hari Raya Haji; 05-31 Vesak Day (Sun) with 06-01 in lieu; 08-09 National Day (Sun) with 08-10 in lieu; 11-08 Deepavali (Sun) with 11-09 in lieu; 12-25 Christmas Day.

2027: 01-01 New Year's Day; 02-06, 02-07 Chinese New Year (Sat, Sun) with 02-08 in lieu; 03-10 Hari Raya Puasa; 03-26 Good Friday; 05-01 Labour Day (Sat); 05-17 Hari Raya Haji; 05-20 Vesak Day; 08-09 National Day; 10-28 Deepavali; 12-25 Christmas Day (Sat).

---

## 11. Owner actions, risks and limitations

### 11.1 Owner actions

| # | Type | Action | Affected |
| --- | --- | --- | --- |
| H1 | Check | Re-verify the 2026/2027 holiday lists in 10.1 against the official page | holiday data |
| H2 | Decision | Review and merge policy: PRs are merged by Claude after verification unless the owner asks to review first | workflow |

### 11.2 Risks

| ID | Risk | Mitigation |
| --- | --- | --- |
| R-PUB | The repository is public while the course brief (PDF p11) asks students to avoid sharing code | Owner decision (A5); course material stays git-ignored; flagged to the owner |
| R-VER | Spring Boot 4 / Security 7 / Hibernate 7 / Jackson 3 API differences from older examples | M0 smoke build; official references |
| R-REST | "REST Repository" read as Spring Data REST | Q11 explanation in README |
| R-TIME | Scope is large for the remaining time | Dependency-ordered milestones; unfinished items reported, never silently dropped |
| R-DL | First-run downloads (Maven, images, Playwright, PlantUML) | Recorded in evidence |

### 11.3 Known limitations

At-least-once email delivery; no antivirus scanning; simulated reimbursement only; single-level approval; no automatic recalculation of approved schedules after holiday changes; the audit trail does not resist database-administrator tampering; performance figures only if measured.

---

## 12. Resume checkpoint

| Field | Value |
| --- | --- |
| Completed | Phase A; approval; M0–M5 (identity, submission with preview, application lifecycle, manager review, sample activity, reconciliation, administration, training calendar, reports, CSV, pagination) |
| Last verified | `./mvnw verify` green (145 tests); browser check of the calendar and reports |
| Next task | M6: fee claims (document storage, submit and revise, manager decision, reimbursement registration) |
| Open issues | Navigation links to later milestones' pages (employee claims, employee entitlement, manager claims tab, admin reimbursements and operations) return 404 until implemented; claim columns in the budget report stay zero until M6 creates claims |
| How to resume | Read sections 1, 7, 9, 12; `git status`, `git log --oneline -20`, `gh pr list`; run `./mvnw verify`; continue with the first task not `VERIFIED` |

---

## Version history

| Version | Date | Change summary | Approval / scope impact | Verification evidence |
| --- | --- | --- | --- | --- |
| 0.1.0 | 2026-09-28 | Initial plan from Phase A | WAITING_FOR_USER_APPROVAL | None |
| 0.1.1 | 2026-09-28 | Added `CLAUDE.md` and the English-source/Chinese-mirror rule | No scope change | None |
| 0.2.0 | 2026-09-28 | Approval recorded with amendments A1–A7; demo/delivery/team scope removed; public repository and git workflow; new `DESIGN.md`; decisions I29–I35; personal details removed for the public repository | APPROVED; system-only scope | `docker info` |
| 0.3.0 | 2026-09-28 | M0 engineering baseline done; milestone status table; decisions I36–I37 | No scope change | `./mvnw verify` green; JAR health UP (9.2) |
| 0.4.0 | 2026-09-28 | M1 done: identity, entry points, scope policy, sample organisation; calculator | No scope change | `./mvnw verify` 49 tests green; browser check (9.2) |
| 0.5.0 | 2026-09-28 | M2 and M3 done in one PR (I40): submission, preview, lifecycle, manager review, concurrency, sample activity; decisions I38–I40 | No scope change | `./mvnw verify` 108 tests green; browser check (9.2) |
| 0.6.0 | 2026-09-28 | M4 administration done; decisions I41–I42 | No scope change | `./mvnw verify` 124 tests green; browser check (9.2) |
| 0.7.0 | 2026-09-28 | M5 done: training calendar, participation and budget reports, CSV exports, pagination of the remaining lists; decisions I43–I45 | No scope change | `./mvnw verify` 145 tests green; browser check (9.2) |
