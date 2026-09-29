# Movie Ticket Booking System

Backend for booking cinema tickets across **multiple cities, theaters, screens and shows**, with **seat-level
booking**, **time-bound seat holds**, **pricing tiers** (regular / premium seats, weekend and prime-time surcharges),
**discount codes**, **payment**, **confirmation**, **cancellation with configurable refund policies** and
**non-blocking notifications**. Two roles: **admin** (manages the catalogue, prices, codes and policies) and
**customer** (browses, books, pays, cancels, sees history).

Built with **Java 25 + Spring Boot 4.1**, **MySQL** (H2 for tests), **Flyway**, **JWT** and **springdoc-openapi**.

**Highlights**

- **No double booking under concurrency.** Requested seat rows are locked (`SELECT … FOR UPDATE`, in id order)
  before they are checked. This is proven by tests where 20 threads race for one seat on **real MySQL**: exactly one wins.
- **Holds expire automatically**, and an expired hold is treated as free straight away, before the background
  cleanup job has run.
- **Idempotent payments.** Retrying with the same `Idempotency-Key` never charges twice, including when the retry
  arrives concurrently with the original.
- **Notifications never block or break a booking.** They are sent after the transaction commits, on a separate
  thread pool, recorded first and retried on failure.
- **214 automated tests** (unit, integration, concurrency, end-to-end). Coverage is **~99% of lines and ~92% of
  branches**, and the build fails if it drops below 95% / 88%.
- **60 documented endpoints** in Swagger UI. A test fails the build if any endpoint is missing from the docs.

| Document | What's in it |
|---|---|
| [docs/HAPPY_FLOW.md](docs/HAPPY_FLOW.md) | The main flow step by step with `curl` commands and real responses |
| [docs/DESIGN.md](docs/DESIGN.md) | Data model, state machines, sequence diagrams, concurrency analysis, error catalogue |
| [docs/API.md](docs/API.md) | Every endpoint by area (generated from the OpenAPI spec); raw spec in [docs/openapi.json](docs/openapi.json) |
| [docs/PLAN.md](docs/PLAN.md) | The phased development plan and decisions log used while building |
| [CLAUDE.md](CLAUDE.md) | The project guidance file used with Claude Code during development |
| [http/catalog.http](http/catalog.http) | 55 ready-made requests covering the whole product (IntelliJ HTTP client) |

---

## Contents

1. [Features](#features)
2. [Tech stack and why](#tech-stack-and-why)
3. [Running it](#running-it)
4. [Configuration](#configuration)
5. [API overview](#api-overview)
6. [Architecture](#architecture)
7. [Key design decisions](#key-design-decisions)
8. [Assumptions](#assumptions)
9. [Out of scope and production next steps](#out-of-scope-and-production-next-steps)
10. [Testing approach](#testing-approach)
11. [AI-assisted workflow](#ai-assisted-workflow)
12. [Repository layout](#repository-layout)

---

## Features

**Admin**
- Cities → theaters → screens, with case-insensitive duplicate checks. A parent that still has children cannot be
  deleted.
- **Seat layouts in one call** from row ranges (e.g. `A-H` × 12 REGULAR + `I-J` × 10 PREMIUM = 116 seats). A layout
  is frozen once the screen has shows.
- Movies (a title in another language, i.e. a dub, is a separate movie).
- **Shows:** start time must be in the future; end = start + duration + 15-minute cleaning gap; **overlaps on a
  screen are rejected**; a base price is required for every seat type on the screen.
- **Pricing rules:** `WEEKEND` and `PRIME_TIME` (a time window), as ± percentages. Rules that apply are added together.
- **Discount codes:** percent (with optional cap) or flat; validity window, minimum order, total and per-customer
  usage limits.
- **Refund policies:** time bands (e.g. 48h+ → 100%, 24–48h → 50%). One is active at a time.
- **Cancel a show:** every paid booking gets a full refund and open holds are released. Re-running it resumes.
- Booking search (by show, customer, status, upcoming/past), **show sales report** (occupancy, gross, refunded, net),
  notification log.

**Customer**
- Register and log in (JWT, 24h). Browsing cities, theaters, movies, shows and seat maps needs **no login**.
- **Hold up to 10 seats for 10 minutes** (all or nothing); release early; one active hold per show.
- Apply / swap / remove a discount code on a hold, then **pay** (mock gateway, idempotent).
- **Refund quote** ("what would I get back now?") and **cancellation** with a refund per the policy.
- **Booking history** filtered by status and upcoming/past; payment attempts; notifications received.

**Platform**
- Background jobs: hold expiry sweep (30s), show reminders 2h before the show (5 min), notification retries (60s).
- A consistent JSON error format with stable error codes, validation messages per field, and no internal details
  leaked.
- Swagger UI with a JWT **Authorize** button; a demo data profile; a Flyway-versioned schema that is identical on
  MySQL and H2.

---

## Tech stack and why

| Choice | Why |
|---|---|
| **Java 25, Spring Boot 4.1** | Current LTS JDK and Boot line; records, pattern-matching `switch`, text blocks keep the code compact |
| **MySQL** (main DB) | Required stack. InnoDB row locks (`FOR UPDATE`) are the core concurrency tool |
| **H2 in MySQL mode** (tests) | Fast, isolated tests; the same Flyway scripts run on both. Concurrency tests **also** run on real MySQL (opt-in) |
| **Flyway** + `ddl-auto=validate` | Versioned, reviewable schema (10 migrations). Hibernate only checks it, it never changes it |
| **Spring Data JPA / Hibernate** | Entities with `@Version`, pessimistic locks, Specifications for filterable lists |
| **Spring Security + JWT (HS256)** | A real login step, a stateless API, role checks per route. Uses Spring's own JWT support (Nimbus); no OAuth flow |
| **springdoc-openapi** | Swagger UI and a machine-readable spec that is checked against the real endpoints in a test |
| **JUnit 5, Mockito, MockMvc, Awaitility, JaCoCo** | Unit + integration + async tests, with an enforced coverage floor |
| **Lombok** | Removes getter / constructor boilerplate on entities and services |

Maven builds with **JDK 25 even if `JAVA_HOME` points elsewhere** (via `maven-toolchains-plugin`), and `.java-version`
pins it for jenv.

---

## Running it

### Prerequisites
- **JDK 25** (the Maven wrapper is included, no Maven install needed)
- **MySQL 8+** running locally (developed on MySQL 9.6)

### 1. Local settings (secrets stay out of git)
```bash
cp local.properties.example local.properties
```
Fill in `local.properties` (git-ignored, loaded automatically):
```properties
spring.datasource.password=<your MySQL password>
app.admin.password=<password for the bootstrap admin>
app.jwt.secret=<at least 32 characters, e.g. output of: openssl rand -base64 48>
```
Database settings can also come from environment variables: `DB_HOST`, `DB_PORT`, `DB_NAME` (default
`movie_booking`, created automatically), `DB_USERNAME` (default `root`), `DB_PASSWORD`.

### 2. Start the app (port 8090)
```bash
./mvnw spring-boot:run
```
On startup Flyway creates the schema, and the **bootstrap admin** is created from `app.admin.email` (default
`admin@moviebooking.local`) and `app.admin.password`.

**With demo data**, on an empty database: 3 cities, 4 theaters, 8 screens, 5 movies, 48 shows over the next 3 days,
pricing rules, `WELCOME10` / `FLAT50`, and the "Standard" refund policy:
```bash
DB_NAME=movie_booking_demo ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

### 3. Try it
- **Swagger UI:** <http://localhost:8090/swagger-ui.html>. Call `POST /api/auth/login`, click **Authorize**, and
  paste the `accessToken`.
- **IntelliJ HTTP client:** [http/catalog.http](http/catalog.http). Put the admin password in the git-ignored
  `http/http-client.private.env.json` (see [http/README.md](http/README.md)).
- **curl, step by step:** [docs/HAPPY_FLOW.md](docs/HAPPY_FLOW.md).

### 4. Run the tests
```bash
./mvnw verify
```
This runs 208 H2 tests plus 6 MySQL tests that skip unless configured, and writes a coverage report to
`target/site/jacoco/index.html`. The build fails below 95% line / 88% branch coverage.

**Concurrency tests on real MySQL**, in a separate schema because they commit data:
```bash
MYSQL_IT_URL='jdbc:mysql://localhost:3306/movie_booking_it?createDatabaseIfNotExist=true' MYSQL_IT_USERNAME=root MYSQL_IT_PASSWORD=... ./mvnw verify -Dit.test=BookingConcurrencyMySqlIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false
```
Any integration test can be pointed at MySQL the same way through `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD`
/ `_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver`.

---

## Configuration

All in [application.properties](src/main/resources/application.properties); every value can be overridden by an
environment variable.

| Key | Default | Meaning |
|---|---|---|
| `server.port` | `8090` | HTTP port |
| `app.timezone` | `Asia/Kolkata` | Business time zone for calendar logic (show dates, weekends, prime time) |
| `app.jwt.expiration` | `24h` | Access token lifetime |
| `app.booking.hold-duration` | `10m` | How long held seats stay reserved while paying |
| `app.booking.max-seats-per-booking` | `10` | Seats per booking |
| `app.booking.expiry-sweep-interval` | `30s` | How often expired holds are cleaned up |
| `app.shows.cleanup-buffer` | `15m` | Gap after a movie before the next show on a screen |
| `app.payment.currency` | `INR` | Currency recorded on payments |
| `app.notifications.reminder-lead-time` | `2h` | Reminder is sent this long before the show |
| `app.notifications.reminder-interval` | `5m` | How often the reminder job runs |
| `app.notifications.retry-interval` | `60s` | How often failed notifications are retried |
| `app.notifications.max-attempts` | `3` | Delivery attempts before a notification stays FAILED |
| `spring.data.web.pageable.max-page-size` | `100` | Upper bound for `size` on list endpoints |

---

## API overview

60 operations. The full list is in [docs/API.md](docs/API.md), and the live spec at `/v3/api-docs`.

| Area | Endpoints | Auth |
|---|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` | public / token |
| Browse | `GET /api/cities`, `/api/cities/{id}/theaters`, `/api/theaters/{id}/screens`, `/api/movies`, `/api/shows?cityId&theaterId&movieId&date`, `/api/shows/{id}`, `/api/shows/{id}/seats` | **public** |
| Bookings | `POST /api/shows/{id}/holds`, `DELETE /api/bookings/{id}/hold`, `POST/DELETE /api/bookings/{id}/discount`, `GET /api/bookings/{id}` | customer |
| Payments | `POST /api/bookings/{id}/pay` (header `Idempotency-Key`), `GET /api/bookings/{id}/payments` | customer |
| Cancellation | `GET /api/bookings/{id}/refund-quote`, `POST /api/bookings/{id}/cancel` | customer |
| History | `GET /api/bookings/me?status&when&sort`, `GET /api/bookings/{id}/notifications` | customer |
| Admin catalogue | CRUD under `/api/admin/cities`, `/theaters`, `/screens` (+ `/layout`), `/movies`, `/shows` (+ `/prices`) | admin |
| Admin commercial | `/api/admin/pricing-rules`, `/api/admin/discount-codes`, `/api/admin/refund-policies` (+ `/activate`) | admin |
| Admin operations | `POST /api/admin/shows/{id}/cancel`, `GET /api/admin/shows/{id}/sales`, `GET /api/admin/bookings`, `GET /api/admin/notifications` | admin |

**Errors** always look like this (`fieldErrors` only for validation failures):
```json
{"timestamp": "2026-09-29T15:58:36.416Z", "status": 400, "error": "VALIDATION_FAILED",
 "message": "Request validation failed", "path": "/api/shows/15/holds",
 "fieldErrors": [{"field": "showSeatIds", "message": "must not be empty"}]}
```
`error` is a stable code (e.g. `SEATS_UNAVAILABLE`, `HOLD_EXPIRED`, `DISCOUNT_EXHAUSTED`). All codes are listed in
[docs/DESIGN.md](docs/DESIGN.md#error-catalogue).

---

## Architecture

A modular monolith with the usual layers:

```
controller  →  service  →  repository  →  MySQL
   (DTOs)      (rules,       (Spring Data JPA,
               transactions)  locks, specs)
                  │
                  ├── payment/       PaymentGateway port  ← MockPaymentGateway
                  ├── notification/  NotificationSender port ← LoggingEmailSender, async listeners, jobs
                  └── events         BookingConfirmedEvent, BookingCancelledEvent (handled after commit)
```

- **Controllers are thin.** They validate input (`@Valid`), take the caller from the JWT
  (`@AuthenticationPrincipal`), and return DTO records, never entities.
- **Services own the transactions and business rules.** Pure logic sits in small, heavily unit-tested classes:
  `PricingService`, `DiscountCalculator`, `RefundCalculator`, `SeatLayoutPlanner`, `NotificationComposer`.
- **External systems sit behind ports** (`PaymentGateway`, `NotificationSender`) with mock implementations. A real
  provider is one new class.
- **The schema is owned by Flyway** (`src/main/resources/db/migration`, V1–V10). There are 19 tables; see the ER
  diagram in [DESIGN.md](docs/DESIGN.md#data-model).

Package layout: `config` (security, JWT, OpenAPI, async, scheduling, bootstrap and demo data), `controller`, `dto`,
`entity`, `event`, `exception`, `notification`, `payment`, `repository`, `security`, `service`.

---

## Key design decisions

The full reasoning, diagrams and alternatives are in [docs/DESIGN.md](docs/DESIGN.md).

### 1. No double allocation of a seat
- Every show gets its own copy of the seats (`show_seats`, unique `(show_id, seat_id)`). This is the row that is
  held and booked.
- A hold **locks the requested rows first** (`SELECT … FOR UPDATE`, **ordered by id** so two overlapping requests
  cannot deadlock), **then** checks them. It is all or nothing: one taken seat means `409 SEATS_UNAVAILABLE` and
  nothing is held.
- Backups: `@Version` on each seat row and the unique constraint.
- **Why pessimistic locking?** Popular seats are contended by definition. A lock gives a clean "first request wins"
  without retry loops, and the lock is held for milliseconds.
- **Proof:** 20 threads for one seat → exactly 1 winner; 30 overlapping multi-seat requests, half in reverse order →
  no deadlock, no seat given twice. Both run on H2 and MySQL.

### 2. Time-bound holds that release automatically
- A hold lasts 10 minutes. `HoldExpiryJob` runs every 30 seconds and expires overdue holds with a **conditional
  update** (`… WHERE status = 'HELD' AND hold_expires_at <= now`), so it can never override a payment happening at
  the same moment.
- **Expired holds are also treated as free when read**: in the seat map, availability counts, new holds, booking
  status and history filters. Correctness never depends on how quickly the job runs.
- The job only frees seats **still tied to the expired booking**. If another customer already took the seat over, the
  job leaves it alone. This exact case is a test.

### 3. Pricing tiers
- Price = the show's **base price for the seat type** (REGULAR / PREMIUM) × (1 + **sum of the matching active
  rules**), rounded half-up to 2 decimals.
- `WEEKEND` = Saturday/Sunday and `PRIME_TIME` = a start-time window, both in the business time zone.
- Prices are **fixed when seats are held** and stored per booked seat (base and charged), so rule changes never
  reprice existing bookings.

### 4. Discount codes
- Applying a code only **validates** it and shows the breakdown (subtotal − discount = total). The code's **usage is
  consumed at payment**, with the code row locked and re-checked. Two customers racing for the **last use** of a code
  cannot both get it (a concurrency test).
- Each rejection has its own error code (`DISCOUNT_EXPIRED`, `DISCOUNT_MIN_ORDER_NOT_MET`,
  `DISCOUNT_USER_LIMIT_REACHED`, …).

### 5. Payment, idempotency and confirmation
- `POST /api/bookings/{id}/pay` needs an `Idempotency-Key`. The booking row is **locked first**, so duplicate
  requests, a second payment and the expiry job all queue behind it. Then:
  1. A known key **replays the stored result** (`Idempotent-Replayed: true`) without charging; a key used for
     another booking gets `409`.
  2. The hold must be unexpired, the show still scheduled, and the discount still valid.
  3. The gateway is charged.
  4. The booking becomes CONFIRMED and the seats BOOKED.
- Declines (`402`) and provider errors (`502`) are stored as attempts, and the booking stays HELD for a retry.
- **Proof:** 10 concurrent payments with different keys → charged once; the same key sent 10 times concurrently →
  one charge, and all 10 callers get that payment.

### 6. Notifications that never block booking
- Payment publishes `BookingConfirmedEvent`. An `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` listener
  handles it on a dedicated thread pool.
- A rolled-back payment never notifies, and a slow or failing provider never delays or undoes a booking (a test makes
  the provider hang).
- Each notification is **recorded, then sent, then marked**, with a unique `(booking, type)` rule so it is never sent
  twice. Failures are retried up to 3 times. Reminders go out 2 hours before the show.

### 7. Cancellation and configurable refunds
- The refund policy **active at payment time is frozen onto the booking**, so later policy changes never apply
  retroactively. A policy used by any booking cannot be edited.
- **Refund = amount actually paid × the percent of the band met** (thresholds inclusive; below every band → 0%). It
  is refunded through the gateway. If the refund fails, **nothing changes** (`502`) and it can be retried. Seats go
  back on sale.
- **Show cancellation:** the show is marked CANCELLED first (no new holds or payments), then each booking is settled
  in **its own transaction** (paid → 100% refund, held → released). Failures are reported and a re-run resumes them.

### 8. Other decisions
- **Time:** stored as UTC `Instant`s; the API speaks ISO-8601 with an offset; calendar logic uses `app.timezone`. A
  single injectable `Clock` makes time testable (tests move it forward to expire holds or reach refund bands).
- **Security:** JWT with `sub` = user id, `email`, `role`. `/api/admin/**` requires ADMIN; browsing is public;
  everything else needs a token. Customers get **404 for other people's bookings**, so ids cannot be probed.
- **Consistency lesson:** state changes that other code may already have loaded use **entity updates, not bulk
  queries**. This came from two bugs the tests caught; see [CLAUDE.md](CLAUDE.md).

---

## Assumptions

The brief is intentionally open, so these are the decisions made where it was silent:

**Catalogue and shows**
1. A **city name is unique**; a theater name is unique within its city; a screen name is unique within its theater.
   All comparisons ignore letter case.
2. A **seat layout belongs to a screen** and uses rows `A`–`Z` with up to 50 seats per row (at most 1000 seats). Each
   row has one seat type. There are two seat types: **REGULAR** and **PREMIUM**.
3. A layout (and deleting the screen or movie) is **frozen once shows exist**, because shows copy the layout into
   their own seats.
4. The **same title in another language is a different movie** (a dub has its own shows).
5. A show occupies its screen for **movie length + 15 minutes** of cleaning. Shows on the same screen may not
   overlap; back-to-back is allowed.
6. Shows can only be scheduled **in the future**. Changing a show's price affects **new holds only**.
7. All theaters operate in **one business time zone** (configurable, default Asia/Kolkata). "Weekend", "prime time"
   and "shows on a date" are decided in that zone.

**Booking and pricing**
8. A **hold lasts 10 minutes** and covers up to **10 seats** of one show. It is all or nothing. A customer can have
   **one active hold per show** (this stops seat hoarding).
9. Base prices are **per show and per seat type**. `WEEKEND` and `PRIME_TIME` rules are **percentages that add up**,
   and prices never go below zero.
10. Prices are **fixed at hold time**; the customer pays what the seat map showed.
11. Holding and paying require an **account**. Browsing does not.

**Discounts and payment**
12. **One discount code per booking**, stored in upper case. A **percent** code may have a cap; a **flat** code never
    exceeds the order.
13. Usage limits count **paid bookings only**. Applying a code to a hold does not use it up.
14. A **used code is not given back** when a booking is cancelled. The refund is based on the amount actually paid.
15. **Payment is mocked.** A token picks the outcome (`tok_success`, `tok_declined`, `tok_insufficient_funds`,
    `tok_error`). No card data is ever sent or stored. The currency is a single configured value (INR).
16. A fully discounted booking (total 0) is confirmed **without calling the gateway**.
17. Idempotency keys are **single use and bound to one booking**. Retrying a failed payment needs a new key.

**Cancellation and refunds**
18. Only **paid** bookings are cancelled; unpaid holds are **released** instead. Cancelling is possible until the
    **show starts**, even when the refund is 0%.
19. Refund bands are **inclusive**: exactly 48h before a "48h → 100%" band refunds 100%. With **no active policy**
    when the booking was paid, the refund is 0%.
20. **Exactly one refund policy is active** at a time. When a show is cancelled by the cinema, the refund is **100%**
    regardless of policy.
21. Refunds complete synchronously through the (mock) gateway, and a booking is refunded **at most once**.

**Accounts and notifications**
22. Anyone can **self-register as a customer**. **Admins are not self-registered**: the first admin is created at
    startup from configuration.
23. JWTs are valid for **24h** and cannot be revoked early. A role change takes effect on the next login.
24. Notifications are **email-only and simulated** (logged). Customers get a confirmation, a **reminder 2h before the
    show**, and a cancellation notice with the refund.

---

## Out of scope and production next steps

As the brief says, there is **no** UI, deployment, containerisation, CI/CD, microservices, OAuth/SSO/MFA or
production observability. Beyond that, these are the known trade-offs and what would come next:

| Area | Today | Next step for production |
|---|---|---|
| Payment | The mock gateway is called while the booking row is locked (fine for an instant mock) | Record a PENDING payment, commit, charge, then confirm, or confirm via the provider's webhook |
| Refunds | Synchronous; on failure nothing changes | Refund state machine with async retries |
| Show cancellation | Settled in-process, resumable by re-running | Background job with progress tracking for large shows |
| Tokens | Stateless JWT, no revocation | Short-lived access tokens + refresh tokens, a deny-list |
| Notifications | Logged "emails", in-process thread pool | Real provider behind `NotificationSender`, outbox table + queue |
| Hot shows | Seat locks are per row; hold creation also checks one active hold per customer per show | Load-test, and consider a per-show queue or a Redis-based hold layer at very high traffic |
| Multi-region | One business time zone | Time zone per theater |
| Holds on a cancelled show | A hold created in the instant before cancellation commits can exist, but can never be paid and expires | Share-lock the show row on hold, if needed |

---

## Testing approach

**214 tests.** There are 208 on H2 plus 6 that run the concurrency scenarios on MySQL when configured. Unit tests
(`*Test`, Surefire) and integration tests (`*IT`, Failsafe) both run in `./mvnw verify`.

| Level | What | Examples |
|---|---|---|
| **Unit** | Pure business logic, table-driven | Pricing (weekend/prime time/time zone/rounding), discounts (every rejection reason), refund bands (boundaries), layout planner, JWT claims, notification text, error handler, job resilience |
| **Integration** | Real Spring context + MockMvc + H2, each test rolled back | Every endpoint's happy path, validation, role checks (401/403/404), and business rules per feature |
| **Concurrency** | Real threads, committed data, **H2 and MySQL** | 20-way seat race; overlapping multi-seat holds; last use of a code; 10 payments for one booking; same key ×10; 5 concurrent cancels |
| **Async** | After-commit listeners on the real thread pool (Awaitility) | Payment not blocked by a hanging provider; failures retried up to the limit; duplicates sent once; reminders only in the lead time |
| **End-to-end** | `CustomerJourneyIT`, public API only | Admin sets up a cinema → customer browses → holds premium seats → discount → pays (retry not charged) → cancels 30h before → 50% refund → sales report balances |
| **Contract** | `OpenApiIT` | Every `/api/**` endpoint is in Swagger with a summary, section and the correct lock |

Test techniques:
- **A movable clock** (`MutableClock`) lets tests expire holds, reach refund bands, or trigger reminders without
  waiting.
- **A spy on the mock gateway** simulates refund failures.
- **A recording notification sender** can fail on command or hang.
- **Tests that commit data use their own H2 database**, so they never leak rows into other tests.

**Coverage** (JaCoCo, unit and integration combined): **~99% lines, ~92% branches**. `verify` fails below
**95% / 88%**.

---

## AI-assisted workflow

Built with **Claude Code** (Anthropic's coding agent) as a pair programmer, in the desktop app:

1. **Plan first.** The brief was broken into a requirement → feature map, a data model and phases 0–11
   ([docs/PLAN.md](docs/PLAN.md)). Each phase started with a short design proposal (e.g. "lock first, then check",
   "record-then-send") and the trade-offs were agreed before any code was written.
2. **[CLAUDE.md](CLAUDE.md) as the living rulebook.** It holds the stack, commands, conventions, the "hard rules"
   (secrets, locking, entity vs bulk updates, time handling) and the test patterns. It was updated at the end of every
   phase with what that phase established, so later phases followed earlier decisions.
3. **Tight loop per change:** implement → tests → `./mvnw verify` → check the new migration on real MySQL (a second
   instance on a spare port) → a scripted **secret check** of the staged diff → small, descriptive commit → push. The
   Git history (one or more commits per phase) shows this.
4. **Tests as the reviewer.** Several real bugs were caught by tests written in the same phase, and fixed before
   commit:
   - an off-by-one in token `expiresIn`;
   - an unknown sort field turning into a 500;
   - bulk updates leaving stale entities in memory (twice, which led to a rule in CLAUDE.md);
   - demo data leaking between tests through a shared in-memory database;
   - Maven silently skipping `*IT` tests until Failsafe was added.
5. **Human in the loop.** The developer made the product and stack decisions (MySQL, JWT over HTTP Basic, a 24h
   token, port, scope) and reviewed each phase summary.

The repeatable procedures from that loop are written down as project skills in [.claude/skills](.claude/skills)
(adding an endpoint end to end; verifying and committing safely; checking against real MySQL), so the same workflow
can be reused on this codebase.

---

## Repository layout

```
├── README.md                  this file
├── CLAUDE.md                  guidance file used with Claude Code during development
├── .claude/skills/            the development workflow written down as reusable Claude Code skills
├── docs/
│   ├── PLAN.md                phased plan + decisions log used during development
│   ├── DESIGN.md              data model, state machines, sequences, concurrency, error catalogue
│   ├── HAPPY_FLOW.md          the main flow with curl commands and real responses
│   ├── API.md                 all endpoints by area (generated from the spec)
│   ├── generate_api_md.py     regenerates API.md from openapi.json
│   └── openapi.json           OpenAPI 3.1 spec snapshot
├── http/
│   ├── catalog.http           55-request walkthrough of the whole product (IntelliJ HTTP client)
│   ├── http-client.env.json   shared variables (no secrets)
│   └── README.md              how to run it (secrets go in the git-ignored private env file)
├── local.properties.example   template for the git-ignored local secrets file
├── pom.xml                    Java 25 toolchain, Failsafe, JaCoCo (with coverage floor), springdoc
└── src/
    ├── main/java/…            config, controller, dto, entity, event, exception, notification, payment,
    │                          repository, security, service
    ├── main/resources/        application.properties, db/migration V1–V10
    └── test/java/…            unit, integration, concurrency, notification, journey tests + support helpers
```
