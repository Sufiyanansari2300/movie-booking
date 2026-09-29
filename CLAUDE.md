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
- Auth: JWT (HS256, 24h) via Spring Security's resource-server JWT support (Nimbus) — no OAuth flow
- **MySQL** — main database
- **H2** (MySQL mode) — tests only (`test` scope; config in `src/test/resources/application.properties`)

## Commands

```bash
./mvnw clean verify                       # build + unit tests (*Test) + integration tests (*IT), H2
./mvnw test -Dtest=ClassName              # run a single unit test class
./mvnw verify -Dit.test=ClassNameIT       # run a single integration test class
# Never `clean` while the app is running from IntelliJ: it deletes target/classes and kills the app.
./mvnw spring-boot:run                    # run the app against local MySQL (port 8090; 8081 is taken by a local nginx)
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo   # same, plus sample catalogue if the DB is empty
```

DB settings come from env vars with defaults: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`.
Local secrets go in `local.properties` (project root, git-ignored, auto-imported);
copy `local.properties.example` to create it. Bootstrap admin: `app.admin.email` / `app.admin.password`
(`ADMIN_PASSWORD`), created on startup by `AdminBootstrap` if missing.
JWT signing key: `app.jwt.secret` (`JWT_SECRET`, min 32 bytes) — the app refuses to start without it.

Auth flow: `POST /api/auth/login {email, password}` → `accessToken`; send `Authorization: Bearer <token>`
on protected calls. `POST /api/auth/register` is public and always creates a CUSTOMER.

API docs: Swagger UI at `/swagger-ui.html` (spec `/v3/api-docs`, springdoc 3.x). Click **Authorize** and paste a token.
Manual API walkthrough: `http/catalog.http` (IntelliJ HTTP client; see `http/README.md`).

## Secrets — hard rule

- **Never** put passwords, tokens or keys in any committed file, including as `${VAR:default}` defaults.
- Before every commit, check the staged diff for secrets. `local.properties` and `.env` must stay git-ignored.
- Exception: obvious test fixtures in `src/test/resources` (e.g. `admin-test-password`) are fine.

## Code structure

Base package `com.sufiyan.moviebooking`, organised by layer:

```
config/        security, JPA auditing, admin bootstrap, async/scheduling configuration
security/      JWT issuing + claim→principal conversion, UserDetailsService, AppUserPrincipal, JSON 401/403 handler
controller/    REST controllers (thin: validate input, call service, return DTO)
service/       business logic and transactions
repository/    Spring Data JPA repositories
entity/        JPA entities
dto/           request/response records
exception/     custom exceptions + global @RestControllerAdvice handler
```

Conventions:
- Controllers never return entities — map to response DTOs (Java `record`s). Because `open-in-view` is off,
  services that touch lazy relations return DTOs (mapping happens inside the transaction); use
  `@EntityGraph` finders (e.g. `findWithCityById`) to avoid N+1 queries.
- Relations are unidirectional `@ManyToOne(fetch = LAZY)`; query children through their repository.
- Validate request DTOs with Bean Validation annotations and `@Valid`.
- `@Transactional` belongs on service methods, not controllers.
- Use `BigDecimal` for money, `Instant` / `LocalDateTime` (UTC) for time.
- REST paths: `/api/admin/**` for admin, `/api/**` for customer and public browsing.
- Errors return one consistent `ApiError` JSON shape (timestamp, status, error code, message, path, field errors).
  Throw `BusinessException` subclasses (`ResourceNotFoundException` 404, `ConflictException` 409,
  `BadRequestException` 400) with a stable UPPER_SNAKE error code; `GlobalExceptionHandler` maps them.
- Names are trimmed and duplicate checks are case-insensitive (`...IgnoreCase` finders).
- Deleting a parent that still has children is blocked with 409 (e.g. `CITY_HAS_THEATERS`).
- Paginated lists return `PageResponse<T>`; client sort fields must go through `SortValidator.requireAllowed`
  (custom `@Query` methods otherwise turn a bad sort into a 500).
- Entities extend `BaseEntity` (id + audited `createdAt`/`updatedAt`).
- Schema changes go in a new Flyway migration `src/main/resources/db/migration/V<n>__<desc>.sql`
  (never edit an applied one). SQL must run on both MySQL and H2 (MySQL mode). Hibernate only validates.
- Every controller has `@Tag`, every endpoint `@Operation(summary = ...)`. Admin controllers (and any endpoint that
  needs a token) carry `@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)`; `Pageable` params use
  `@ParameterObject`. Standard error responses are added automatically by `OpenApiConfig`.
  `OpenApiIT` fails if any `/api/**` endpoint is missing from the spec or lacks a summary/tag/correct lock.
- Get the caller with `@AuthenticationPrincipal AppUserPrincipal` (has `id()` and `role()`; built from JWT claims
  `sub`=user id, `email`, `role`). Never trust a user id sent in the request body.
- Time-dependent code injects `java.time.Clock` (bean in `TimeConfig`) instead of calling `Instant.now()`.
- Times: store `Instant` (UTC). APIs accept/return `OffsetDateTime`; responses render in the business zone
  (`ZoneId businessZone` bean, `app.timezone`, default Asia/Kolkata). Calendar logic (a show "date", weekends) uses
  that zone.
- Shows: `end_time` = start + movie duration + `app.shows.cleanup-buffer`. Overlap = same screen, SCHEDULED,
  `start < otherEnd && end > otherStart` (back-to-back is fine). Scheduling locks the screen row
  (`ScreenRepository.findByIdForUpdate`) to serialize concurrent admins.
- `show_seats` (one per seat per show, unique `(show_id, seat_id)`, `@Version`) is what gets held/booked —
  never book against `seats`. Once a screen has shows its layout, and deletes of the screen/movie, are blocked.
- Seat holds (`BookingService.hold`): lock the requested `show_seats` with `ShowSeatRepository.lockForHold`
  (PESSIMISTIC_WRITE, ordered by id) **before** checking them; all-or-nothing. A seat is holdable when AVAILABLE or
  HELD with an expired `hold_expires_at` (`ShowSeat.isHoldable`). Never check-then-update without the lock.
- Booking status changes out of HELD use conditional bulk updates (`BookingRepository.endHold` / `expireIfDue`:
  `... where status = HELD`) so concurrent transitions have exactly one winner. Seat release is scoped to the
  booking (`releaseHeldByBooking`) so it never frees a seat someone else has held since.
- Expired holds are treated as free/EXPIRED on read (seat map, availability, booking view); `HoldExpiryJob` only
  tidies up. Background jobs are off in tests (`app.scheduling.enabled=false`) and called directly.
- Customers only see their own bookings; someone else's booking id returns 404 (not 403).
- Money: `BigDecimal`, 2 decimals, `RoundingMode.HALF_UP`. Seat price = base (`show_prices`) adjusted by the sum of
  matching active pricing rules (`PricingService`, WEEKEND / PRIME_TIME in the business zone), computed once at hold
  time and stored on `booking_seats` (base_price + price). Rule changes never reprice existing bookings.
- Booking totals: `subtotal_amount` (seats after rules) - `discount_amount` = `total_amount`.
  `DiscountCalculator` holds the discount math/validation (each rejection has its own error code). Applying a code
  only validates; the usage count is consumed at confirmation under `DiscountCodeRepository.findByIdForUpdate`
  and recorded in `discount_redemptions` (source of truth for per-customer limits).
- Payment (`PaymentService.pay`): lock the booking row first (`BookingRepository.findByIdForUpdate`), then replay by
  `Idempotency-Key` (keys are single use and bound to one booking), then require an unexpired HELD booking, re-check
  the discount under its row lock, charge through the `PaymentGateway` port (mock: token decides the outcome), and on
  success confirm with **entity** updates (not bulk queries) so `@Version` applies. Every attempt is stored;
  declines (402) / provider errors (502) leave the booking HELD. `BookingConfirmedEvent` is published on success.
- Refunds: the active `RefundPolicy` is frozen onto the booking at confirmation (`bookings.refund_policy_id`); a
  policy referenced by bookings is immutable. `RefundCalculator`: the band with the largest threshold met wins
  (inclusive), below all bands = 0%; refund = amount actually paid x percent. Cancellation (`CancellationService`)
  locks the booking row, refunds through `PaymentGateway.refund` (failure = nothing changes, 502), releases seats,
  publishes `BookingCancelledEvent`. Show cancellation: mark show CANCELLED first, then settle each booking in its
  own transaction (100% refund / release hold); re-running resumes. Payment rejects non-SCHEDULED shows.
- Lists with optional filters use JPA Specifications (`BookingSpecifications`, null = no filter) with an
  `@EntityGraph` on the overridden `findAll(Specification, Pageable)`, then batch-load children for the page
  (`findByBookingIdIn`) — never one query per row. Status filters use the *effective* status (overdue HELD = EXPIRED).
- Notifications: react to domain events with `@Async(AsyncConfig.NOTIFICATION_EXECUTOR)` +
  `@TransactionalEventListener(AFTER_COMMIT)` — never send from inside a booking/payment transaction.
  `NotificationService.notify` records first (unique booking+type = idempotent), calls the `NotificationSender`
  port outside any transaction, then marks SENT/FAILED. Listeners must swallow exceptions (the booking is committed).
  `NotificationJobs` sends reminders (lead time `app.notifications.reminder-lead-time`) and retries failures.
- Tests of after-commit/async behaviour cannot be `@Transactional` (nothing commits); use their own H2 URL, a
  `RecordingNotificationSender` (`@Primary` test bean) and Awaitility.
- Prefer entity updates over `@Modifying` bulk queries when the rows may already be in the persistence context; bulk
  queries bypass it (and `clearAutomatically` detaches everything).
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
  Annotate with `@Transactional` so each test rolls back. Use `AuthTestSupport.login(...)` + `bearer(token)`
  for authenticated calls.
- Spring Boot 4 notes: MockMvc annotations live in `org.springframework.boot.webmvc.test.autoconfigure`;
  JSON is Jackson 3 (`tools.jackson.databind`); `PropertyReferenceException` is in `org.springframework.data.core`.
- All test contexts share one in-memory H2 DB. A test whose data is committed (e.g. startup runners) must use
  its own `spring.datasource.url` so it cannot leak rows into other tests (see `DemoDataSeederIT`).
- Concurrency: `concurrency/BookingConcurrencyScenarios` (20 threads race for one seat; overlapping multi-seat
  holds; last use of a discount code; 10 payments for one booking; same idempotency key sent concurrently) runs on H2 (`BookingConcurrencyIT`) and on real MySQL (`BookingConcurrencyMySqlIT`) when `MYSQL_IT_URL`,
  `MYSQL_IT_USERNAME`, `MYSQL_IT_PASSWORD` are set — use a dedicated schema such as `movie_booking_it`.
- Time travel in tests: `@Import(MutableClockConfig.class)` and `MutableClock.advance(...)`.
  `support/TestData` builds users and a ready show (A-B regular, C premium) with unique names.
- Every new feature ships with tests; `./mvnw clean verify` must pass before committing.

## Git workflow

- Small, focused commits with clear messages (reviewers read the history).
- Run `./mvnw clean verify` before committing.
- Keep this file and `README.md` up to date as design decisions are made.
