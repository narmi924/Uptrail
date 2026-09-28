# CLAUDE.md — Uptrail project conventions

Uptrail — Staff Training & Approvals. An implementation of the SA63 Course Application Tracking System (CATS).

These conventions apply to every session working in this repository. They complement, and never override, the owner's global instructions.

## 1. Language rules

- Communication with the project owner: Chinese.
- Generated code, identifiers, code comments, SQL, configuration, UI text, error messages, demo data, commit messages: English.
- Generated documents (`README.md`, `Plan.md`, everything under `docs/`, runbooks, test reports, delivery notes, this file): English. The English file is always the authoritative version.
- Every English document must have a Chinese mirror at `tmp/docs/<same relative path>`:
  - `README.md` → `tmp/docs/README.md`
  - `Plan.md` → `tmp/docs/Plan.md`
  - `CLAUDE.md` → `tmp/docs/CLAUDE.md`
  - `docs/architecture.md` → `tmp/docs/docs/architecture.md`
  - `docs/diagrams/README.md` → `tmp/docs/docs/diagrams/README.md`
- Mirror rules:
  - Same file name and same relative path under `tmp/docs/`.
  - First lines of the mirror state: "Chinese mirror of `<path>` (source version/date). The English file is authoritative."
  - Keep identifiers, commands, file paths, table and column names, status names, error codes, requirement IDs (R/O/D/T/Q/I/N, M-tasks) and version numbers in English inside the Chinese text.
  - Update the mirror in the same work unit as the English change. For `Plan.md`, refresh the mirror at least whenever a version-history row is added, and always before a session ends.
  - Delete or rename the mirror when the English source is deleted or renamed.
- Not mirrored: source code, Javadoc, SQL migrations, YAML/properties, Thymeleaf templates, mail templates, test fixtures, `LICENSE`-type files, third-party notices copied verbatim, and PlantUML sources (the compiled diagrams are not documents).
- `tmp/` is git-ignored, so the Chinese mirrors stay local and are never pushed; they are regenerated from the English sources when needed.

## 2. Plan and reference material

- `Plan.md` in the project root is the only active plan. Do not create `TODO.md`, `tasks.md`, `progress.md`, `roadmap.md`, `Plan_v2.md` or a separate active decision log.
- `References/` is the read-only design baseline. Never modify, move or overwrite anything inside it. Record deviations in `Plan.md`.
- Phase gate: implementation (Phase B) was approved by the owner on 2026-09-28 (recorded in `Plan.md` section 1). Continue milestone by milestone without asking again, except for the escalation cases listed in `Plan.md` section 11.
- Scope: the system itself (code, database, tests, runtime configuration, README and technical docs). Presentation, demo script, delivery packaging and team work distribution are out of scope. The team is the owner and Claude.
- UI work follows `DESIGN.md` (tokens implemented in `src/main/resources/static/css/uptrail.css`).
- Before resuming in a new session: read `Plan.md` sections 1, 7, 9 and 12, then `git status` and `git log`, then run the baseline tests.

## 3. Engineering conventions (summary; details in `Plan.md` section 6)

- Stack: Java 21, Spring Boot 4.1.x (Spring MVC, Thymeleaf, Spring Security, Spring Data JPA), MySQL 8, Flyway, Maven Wrapper. Do not switch frameworks or add JWT, Redis, Kafka, workflow engines, or cloud services.
- Package root `com.uptrail`; main class `UptrailApplication`; Maven artifactId `uptrail`.
- Layering: Controller → Service → Repository. Controllers bind DTOs and call services; services own transactions, validation, authorization scope and audit/ledger/outbox writes; entities change state through intention-revealing methods only.
- Money is `BigDecimal`/`DECIMAL(12,2)`; training time is integer half-day units; business dates come from the injectable `Clock` in `Asia/Singapore`.
- Flyway owns the schema; `spring.jpa.hibernate.ddl-auto=validate` in every profile.
- Security stays on: CSRF enabled, no state change via GET, server-side role and data-scope checks on every page, API, CSV and download.
- Write protocol: `READ_COMMITTED`; lock employee → training accounts in ascending year → target record; re-validate inside the lock; application, ledger, audit event and outbox rows in one transaction; mail sent after commit.
- Course terminology, application statuses (APPLIED, UPDATED, DELETED, APPROVED, REJECTED, CANCELLED, COMPLETED) and requirement IDs from the design pack are kept unchanged.
- Comments explain business reasons, limits and non-obvious implementation only.

## 4. Tests and verification

- Tiers: pure unit → MockMvc slices → MySQL integration (Testcontainers `mysql:8.4`, or `UPTRAIL_TEST_DB_URL` pointing to an isolated database) → Playwright e2e (Maven profile `e2e`).
- Never make a build green by deleting tests, weakening assertions, ignoring return values or disabling validation. Integration tests must fail loudly when no database is available, never skip silently.
- Report "compiles", "implemented" and "verified" separately. Record executed commands and real results in `Plan.md` section 9; never pre-fill pass rates, coverage or timings.
- Never use the owner's local MySQL on port 3306 unless the owner provides credentials for an isolated database. Docker Compose maps MySQL to 3307.

## 5. Git and GitHub

- Remote: `https://github.com/narmi924/Uptrail` (public). `References/` (course material and design pack) and `tmp/` (Chinese mirrors) are git-ignored and must never be committed or pushed. Never commit secrets, `.env` files, runtime data under `var/`, or personal data.
- Workflow: `main` is the integration branch. Work happens on one branch per milestone (for example `m0-engineering-baseline`), pushed and merged through a pull request after the milestone's verification passes. No force-push to `main`; no history rewriting of pushed commits.
- Commit, push, pull-request and release texts must not contain `Co-Authored-By: Claude` (or any Claude co-author trailer), "Generated with Claude Code", or similar Claude attribution.
- Commits contain project changes only and use the owner's configured git identity; no fabricated authorship, contributions or reviews.
- Deployment or publishing anywhere other than this repository requires explicit authorization.

## 6. Sample data

- Sample data is synthetic and clearly labelled (`@example.com` addresses, fictional names, files marked SAMPLE); no real people, credentials or receipts.
- It is loaded only when `uptrail.sample-data.enabled=true` and the database has no employees; it goes through the same services as user actions, never through a business-time override reachable from HTTP.
- Public holiday data must name its source and retrieval date; unverifiable data is labelled `FIXTURE`.
