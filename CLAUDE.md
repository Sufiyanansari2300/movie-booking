# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

Backend for a **Movie Ticket Booking System** (SDE-2 take-home, Spring Boot).
Cities → theaters → screens → shows → seat-level booking, with time-bound seat holds,
pricing tiers, discount codes, payment, confirmation, cancellation with refund policies,
and async notifications.

**Roles**
- `ADMIN` — manages cities, theaters, screens, seat layouts, shows, pricing tiers, discount codes, refund policies.
- `CUSTOMER` — browses shows, holds/books/cancels seats, views booking history.

**In scope:** REST APIs, persistence, basic role-based access control, validation and error handling, unit + integration tests.
**Out of scope — do not build:** UI, Docker/deployment/CI, microservices, OAuth/SSO/MFA, production observability.

Every meaningful assumption must be recorded in `README.md` under "Assumptions".

## Tech stack

- Java **25** (pinned via `.java-version` for jenv and `maven-toolchains-plugin` in `pom.xml` — do not lower it)
- Spring Boot **4.1.1**, Maven (use the wrapper `./mvnw`)
- Spring Web MVC, Spring Data JPA (Hibernate), Bean Validation, Spring Security, Lombok
- **MySQL** — main database
- **H2** (MySQL mode) — tests only (`test` scope; config in `src/test/resources/application.properties`)

## Commands

```bash
./mvnw clean verify                       # build + unit tests (*Test) + integration tests (*IT), H2
./mvnw test -Dtest=ClassName              # run a single unit test class
./mvnw verify -Dit.test=ClassNameIT       # run a single integration test class
./mvnw spring-boot:run                    # run the app against local MySQL (port 8081)
```

DB settings come from env vars with defaults: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`.
Local secrets go in `local.properties` (project root, git-ignored, auto-imported);
copy `local.properties.example` to create it. Bootstrap admin: `app.admin.email` / `app.admin.password`
(`ADMIN_PASSWORD`), created on startup by `AdminBootstrap` if missing.

## Secrets — hard rule

- **Never** put passwords, tokens or keys in any committed file, including as `${VAR:default}` defaults.
- Before every commit, check the staged diff for secrets. `local.properties` and `.env` must stay git-ignored.
- Exception: obvious test fixtures in `src/test/resources` (e.g. `admin-test-password`) are fine.

## Code structure

Base package `com.sufiyan.moviebooking`, organised by layer:

```
config/        security, JPA auditing, admin bootstrap, async/scheduling configuration
security/      UserDetailsService, AppUserPrincipal, JSON 401/403 handler
controller/    REST controllers (thin: validate input, call service, return DTO)
service/       business logic and transactions
repository/    Spring Data JPA repositories
entity/        JPA entities
dto/           request/response records
exception/     custom exceptions + global @RestControllerAdvice handler
```

Conventions:
- Controllers never return entities — map to response DTOs (Java `record`s).
- Validate request DTOs with Bean Validation annotations and `@Valid`.
- `@Transactional` belongs on service methods, not controllers.
- Use `BigDecimal` for money, `Instant` / `LocalDateTime` (UTC) for time.
- REST paths: `/api/admin/**` for admin, `/api/**` for customer and public browsing.
- Errors return one consistent `ApiError` JSON shape (timestamp, status, error code, message, path, field errors).
  Throw `BusinessException` subclasses (`ResourceNotFoundException`, `ConflictException`, …) with a stable
  UPPER_SNAKE error code; `GlobalExceptionHandler` maps them.
- Entities extend `BaseEntity` (id + audited `createdAt`/`updatedAt`).
- Schema changes go in a new Flyway migration `src/main/resources/db/migration/V<n>__<desc>.sql`
  (never edit an applied one). SQL must run on both MySQL and H2 (MySQL mode). Hibernate only validates.
- Get the caller with `@AuthenticationPrincipal AppUserPrincipal` (has `id()` and `role()`).
- Emails are normalised to lower case (`UserService.normalizeEmail`).

## Core design rules

- **No double booking.** Seat hold/booking must be serialized per show-seat: lock the rows
  (pessimistic `SELECT ... FOR UPDATE` or optimistic `@Version`) and back it with a DB unique
  constraint. Keep the final choice and reasoning documented in `README.md`.
- **Seat holds expire.** Holds have an expiry time; expired holds are released automatically
  (scheduled job) and must never be treated as valid when confirming a booking.
- **Notifications are async.** Confirmation/reminder notifications must not block or fail the
  booking transaction (send after commit, asynchronously).
- **Payment is mocked** behind an interface so it can be swapped.
- Pricing (tier, weekend, discount) and refund calculations live in dedicated services and are unit-tested.

## Testing

- Unit tests for services (JUnit 5 + Mockito), named `*Test` (Surefire).
- Integration tests for REST flows (`@SpringBootTest` + `@AutoConfigureMockMvc`, H2), named `*IT` (Failsafe).
  Annotate with `@Transactional` so each test rolls back.
- Spring Boot 4 notes: MockMvc annotations live in `org.springframework.boot.webmvc.test.autoconfigure`;
  JSON is Jackson 3 (`tools.jackson.databind`).
- Must include a **concurrency test**: many threads try to book the same seat, exactly one succeeds.
- Every new feature ships with tests; `./mvnw clean verify` must pass before committing.

## Git workflow

- Small, focused commits with clear messages (reviewers read the history).
- Run `./mvnw clean verify` before committing.
- Keep this file and `README.md` up to date as design decisions are made.
