# CLAUDE.md

Working notes for AI coding assistants (and anyone else) changing Uptrail. Start with [README.md](README.md); the design is described in [docs/architecture.md](docs/architecture.md) and the user interface rules in [DESIGN.md](DESIGN.md).

## Language

- Talk with the owner in Chinese.
- Write code, comments, UI text, commit messages and documents in English.
- Keep a Chinese translation of every English document under `tmp/docs/` with the same path (for example `README.md` → `tmp/docs/README.md`) and update it together with the English file. `tmp/` is not tracked.

## Code

- Stack: Java 21, Spring Boot 4.1, Thymeleaf, Spring Security, Spring Data JPA, MySQL 8.4, Flyway. Do not add other frameworks or infrastructure.
- Controllers call services; services call repositories. Services own transactions, business rules, access checks and the audit, ledger and outbox writes.
- Change the schema only with a new Flyway migration. Never edit a migration that has already been released.
- Keep CSRF protection and the role and data-scope checks on every page, API, export and download. Never change data on a `GET`.
- Money is `BigDecimal` in SGD with two decimals; training time is counted in half days; business dates come from `BusinessClock` (Asia/Singapore).
- Follow DESIGN.md. No CDN assets and no inline scripts or styles (the Content-Security-Policy blocks them).
- Sample data stays synthetic.

## Tests

- `./mvnw verify` must pass before merging; it needs Docker. Never delete tests or weaken assertions to make a build pass.
- Use the Docker Compose MySQL (port 3307) or Testcontainers, never another database on the machine.

## Git

- Work on a branch and merge into `main` through a pull request. No force-push to `main`.
- No `Co-Authored-By: Claude`, "Generated with Claude Code" or similar attribution in commits, pull requests or releases.
- Never commit `tmp/`, `var/`, `.env` files or secrets.

## Public demo

`Dockerfile.vercel` builds a self-contained demo image (Uptrail, MySQL and a proxy). `vercel deploy --prod` from the repository root rebuilds and publishes it.
