# Uptrail

**Staff Training & Approvals** — a web application for applying for training, approving it and claiming course fees.

Employees apply for internal training, external courses and professional certifications; managers approve or reject each application with a reason; administrators maintain staff, approval routing, entitlements, the course catalogue and the public holiday calendar. Training days and budgets are tracked per year in a ledger that separates pending reservations, approved commitments and reimbursed fee claims.

**Live demo: <https://uptrail-demo.vercel.app>** — the sign-in page lists demo accounts you can use with one click.

**For the CATS team:** [MVC-first implementation reference](docs/cats-team-reference.md) maps common blockers to working code, explains the model differences, and includes a no-REST reference mode. Start there when adapting a small implementation into the team repository.

## What it does

| Role | Main functions |
| --- | --- |
| Employee | Apply with a live eligibility check (training days, holidays, overlaps, remaining days and budget); update, delete or cancel; confirm attendance after the course; claim the fee of a completed course with receipt and certificate; see the year's balance and every movement behind it |
| Manager | Decide applications grouped by staff member, with the person's usage and the team's courses in the same period; decide fee claims after checking the documents; team history; participation and budget reports with CSV export and print view |
| Administrator | Staff and roles, approval routing, annual entitlements, catalogue, public holidays and calendar years, reimbursement registration, audit trail, email outbox and ledger check |
| Everyone signed in | Training calendar of approved and completed courses |

Every change is audited, emails are sent through an outbox after the change is saved, and each email links to the related page after sign-in.

## Technology

Java 21 · Spring Boot 4.1 (Spring MVC, Thymeleaf, Spring Security, Spring Data JPA) · MySQL 8.4 · Flyway · Bootstrap 5.3 (served by the application) · JUnit 5, Testcontainers, GreenMail, ArchUnit, Playwright.

## Try it

### Online demo

Open <https://uptrail-demo.vercel.app>. Staff sign in at `/login`, administrators at `/admin/login`; both pages list demo accounts.

- Everything is synthetic sample data, created fresh when the demo starts.
- The demo sleeps after a few idle minutes and forgets all changes. The first page after a pause can take up to a minute.
- Other visitors use the same demo at the same time and may change the same records. If you are signed out unexpectedly, the demo has restarted; sign in again.
- Emails are written to files inside the demo instead of being sent. Uploads are limited to 4.5 MB per request by the hosting platform.

### With Docker only

The demo runs from one self-contained image (Uptrail, MySQL and a small proxy). No Java installation is needed:

```bash
git clone https://github.com/narmi924/Uptrail.git
cd Uptrail
docker build -f Dockerfile.vercel -t uptrail-demo .
docker run --rm -p 8080:80 uptrail-demo
```

Open <http://localhost:8080> once the log shows "Sample data loaded". The first build downloads the base images and Maven dependencies and takes several minutes. Stopping the container discards its data.

### For development

Prerequisites: JDK 21 and Docker. Maven is not needed; use the wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

```bash
docker compose up -d                                     # MySQL on 127.0.0.1:3307, Mailpit on 1025 (SMTP) and 8025 (web)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev    # http://localhost:8080
```

The first start with the `dev` profile creates the schema and loads the sample organisation into the empty database. Then open:

- Staff sign-in: <http://localhost:8080/login>
- Administration sign-in: <http://localhost:8080/admin/login>
- Emails sent by the application: <http://localhost:8025> (Mailpit)
- Health: <http://localhost:8080/actuator/health>

For ordinary MVC forms without the optional REST enhancements, start with both profiles:

```bash
./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev,mvc-reference"
```

The application still supports submission and approvals. Eligibility is checked on submit, catalogue search is hidden, and the calendar uses server-rendered lists and form navigation. Remove `mvc-reference` later to enable the live checks, search and interactive calendar.

To run the packaged application instead:

```bash
./mvnw -DskipTests package
java -jar target/uptrail-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

To start again from an empty database: stop the application, run `docker compose down -v` (removes only this project's containers and volumes) and `docker compose up -d`, then start the application again.

### Publishing the demo

`vercel deploy --prod` from the repository root publishes the demo. `vercel.json` defines two parts: stylesheets, scripts, fonts, images and Bootstrap are collected by `deploy/demo/build-assets.sh` and served from Vercel's CDN; every other request goes to the container built from `Dockerfile.vercel`. Only the files listed in `.vercelignore` are uploaded.

## Sample accounts

All sample people, addresses and documents are synthetic. Every sample account has the password **`Uptrail#2026`** (change it with `UPTRAIL_SAMPLE_PASSWORD` before the first start). Staff sign in at `/login`, administrators at `/admin/login`.

| Username | Name | Roles | Notes |
| --- | --- | --- | --- |
| `alex` | Alex Tan | Administrator | Registers reimbursements |
| `nurul` | Nurul Huda | Administrator, Employee | Uses both workspaces, one per sign-in; cannot register their own claims |
| `grace` | Grace Lim | Manager | Director; approves the other managers and has no approver |
| `daniel` | Daniel Wong | Manager | Leads Software Engineering |
| `priya` | Priya Nair | Manager | Leads Data and Analytics |
| `siti`, `marcus`, `weiling`, `arjun`, `hannah`, `farid`, `jasmine`, `kelvin` | | Employee | Report to Daniel; `jasmine` has no account for next year |
| `meiling`, `rahul`, `nora`, `junhao`, `aisha`, `clara` | | Employee | Report to Priya |
| `benjamin` | Benjamin Ong | Employee | Inactive: cannot sign in |
| `ethan` | Ethan Koh | Employee | Has no approving manager, so cannot apply |

The sample activity is created through the application's own services, relative to today's date, and covers every application status (including a course that runs into next year) and every claim status:

| To see | Sign in as |
| --- | --- |
| Applications waiting for a decision | `daniel` or `priya` |
| A reimbursed claim and the reimbursement in My Entitlement | `siti` |
| A claim revised after a rejection, waiting for the manager | `weiling` (manager: `daniel`) |
| A rejected claim that can be revised | `rahul` |
| An approved claim waiting for registration | `alex` → Reimbursements |
| A completed course whose fee can still be claimed | `farid` |

## Configuration

The application reads environment variables; defaults suit the Docker Compose services. `.env` (copied from `.env.example`) is read by Docker Compose only: export the same values in the shell that starts the application.

| Variable | Default | Purpose |
| --- | --- | --- |
| `UPTRAIL_DB_URL` | `jdbc:mysql://localhost:3307/uptrail?…` | JDBC URL |
| `UPTRAIL_DB_USER` | `uptrail` | Database user |
| `UPTRAIL_DB_PASSWORD` | `uptrail_dev_password` in `dev`, empty otherwise | Database password (must match the Compose database) |
| `UPTRAIL_PORT` | `8080` | HTTP port |
| `UPTRAIL_REST_ENABLED` | `true` | Optional JSON enhancements; the `mvc-reference` profile disables them |
| `UPTRAIL_BASE_URL` | `http://localhost:8080` | Base of the links in emails |
| `UPTRAIL_DOCUMENTS_ROOT` | `var/documents` | Private folder for claim documents |
| `UPTRAIL_MAIL_TRANSPORT` | `smtp` | `smtp`, or `file` to write emails as text files |
| `UPTRAIL_MAIL_HOST`, `UPTRAIL_MAIL_PORT` | `localhost`, `1025` | SMTP server (Mailpit) |
| `UPTRAIL_MAIL_CAPTURE_DIR` | `var/mail-capture` | Folder of the `file` transport |
| `UPTRAIL_SAMPLE_PASSWORD` | `Uptrail#2026` | Password of the sample accounts (`dev` profile) |

Compose-only variables: `UPTRAIL_DB_PORT` (3307), `UPTRAIL_DB_ROOT_PASSWORD`, `UPTRAIL_MAILPIT_UI_PORT` (8025). Without the `dev` profile no sample data is loaded and templates are cached.

## Build and test

```bash
./mvnw test                                  # unit and architecture tests
./mvnw verify                                # plus integration tests against MySQL 8.4 in Testcontainers
./mvnw -Pe2e verify                          # browser end-to-end flow (Playwright, headless Chromium)
./mvnw -Pdiagrams generate-resources         # compile docs/diagrams/*.puml to SVG
./mvnw -DskipTests package                   # executable JAR in target/
```

Integration and end-to-end tests need Docker. Without Docker, point the integration tests at an isolated, disposable database with `UPTRAIL_TEST_DB_URL`, `UPTRAIL_TEST_DB_USER` and `UPTRAIL_TEST_DB_PASSWORD`; the tests empty its tables. The first end-to-end run downloads Playwright and a Chromium build. Screenshots of the browser run are written to `target/e2e/`.

## Project layout

```text
src/main/java/com/uptrail/     one package per business area (see docs/architecture.md)
src/main/resources/db/migration/   Flyway migrations V1–V5
src/main/resources/templates/  Thymeleaf pages
src/main/resources/mail/       plain-text email templates
src/main/resources/holidays/   bundled Singapore public holidays 2026–2027 with source note
src/test/java/                 unit and integration tests
src/e2e/java/                  Playwright end-to-end tests
docs/                          architecture, data model, API, diagrams
Dockerfile.vercel, deploy/demo/    self-contained demo image (Uptrail, MySQL, proxy)
var/                           runtime files (documents, captured mail); not in git
```

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| The application cannot connect to MySQL | Check `docker compose ps`. Port 3307 in use: set `UPTRAIL_DB_PORT` for Compose and the matching `UPTRAIL_DB_URL` for the application. |
| `Access denied for user 'uptrail'` | The application's password differs from the one the database volume was created with. Export the same `UPTRAIL_DB_PASSWORD`, or recreate the database with `docker compose down -v`. |
| No sample data | Sample data loads only with the `dev` profile into an empty database. Reset as described under "For development". |
| Sign-in fails for a correct password, or a page shows 403 | Administrators sign in at `/admin/login`, staff at `/login`; each session belongs to the workspace it signed in to. |
| Applications are refused because the holiday calendar is not confirmed | An administrator confirms the year on Public Holidays. The bundled 2026–2027 holidays were retrieved from the Ministry of Manpower on 2026-09-28; check them against the official list. |
| No emails in Mailpit | Check that Mailpit runs and look at Operations → Email outbox for errors. Failed emails are retried automatically and can be queued again there. |
| "Files too large" when claiming | Each document may be at most 5 MB (PDF, PNG or JPEG). |
| Integration tests fail at start-up | Docker is not running and no `UPTRAIL_TEST_DB_URL` is set. The tests fail on purpose instead of skipping. |
| Windows startup fails with "Unable to establish loopback connection" and `UnixDomainSockets` in the stack | Try an existing directory with its full path for `-Djdk.net.unixdomain.tmpdir`, through `JAVA_TOOL_OPTIONS` in the current shell. The Windows browser tests passed with this setting. |
| `./mvnw package` fails with "Unable to rename … .jar" on Windows | The JAR is still running; stop the application first. |

## Documents

- [docs/cats-team-reference.md](docs/cats-team-reference.md) — MVC-first source map, routing, ID/session contracts, adaptation and delivery checklist
- [docs/architecture.md](docs/architecture.md) — modules, security, write protocol, ledger, email, documents, tests
- [docs/data-model.md](docs/data-model.md) — tables, constraints and how balances are computed
- [docs/api.md](docs/api.md) — JSON API, CSV exports and document downloads
- [docs/diagrams/](docs/diagrams/README.md) — PlantUML sources and SVG diagrams
- [DESIGN.md](DESIGN.md) — user interface design system
- [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) — open-source components and their licences
- [CLAUDE.md](CLAUDE.md) — working notes for AI coding assistants

## Limitations

Reimbursement registration is a record only: Uptrail has no payment interface and issues simulated `SIM-` references. Emails are delivered at least once, so a recipient may occasionally receive one twice. Uploaded files are checked for type and size but not scanned for malware. Approval has one level. Approved schedules are not recalculated automatically when a holiday changes; affected applications are listed to the administrator instead.
