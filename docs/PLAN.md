# Development Plan — Movie Ticket Booking System

Working plan used during development. Decisions are recorded here as they are made;
final assumptions and design reasoning live in `README.md`.

## Decisions

| Topic | Decision | Why |
|---|---|---|
| Auth | JWT login (HS256, 24h expiry) + BCrypt, roles `ADMIN` / `CUSTOMER` | Real login step; password sent once, then a short-lived token. Replaced HTTP Basic after phase 1. OAuth/SSO out of scope |
| Schema | Flyway migrations, Hibernate `ddl-auto=validate` | Versioned, reviewable schema; same scripts run on MySQL and H2 |
| Main DB / test DB | MySQL / H2 (MySQL mode) | As requested |
| Seat hold duration | 10 minutes (configurable) | Typical checkout window |
| Payment | Mock gateway behind an interface | Real providers are out of scope |
| Concurrency | Pessimistic row lock on `show_seat` (ordered by id) + `@Version` + unique constraints | Hot seats see heavy contention; locking gives deterministic "first wins" |
| Notifications | `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` | Never blocks or rolls back a booking |

## Requirement → feature map

| Requirement | Feature |
|---|---|
| Cities, theaters, shows | Admin CRUD: City → Theater → Screen, Movie, Show |
| Seat-level booking | Seat layout per screen; `ShowSeat` per show |
| Time-bound holds | 10-min hold; scheduled release + expiry check at payment |
| No double allocation | Row locking + version + unique constraints; concurrency test |
| Pricing tiers (regular, premium, weekend) | `ShowPrice` per seat type + weekend `PricingRule` |
| Discount codes | Percent/flat, validity window, usage limit, min amount, max cap |
| Payment + confirmation | Mock gateway, idempotent pay, booking `CONFIRMED` |
| Refunds with configurable policies | `RefundPolicy` with time-band rules; admin show cancel = 100% |
| Non-blocking notifications | Async after-commit events + reminder job |
| Roles | Spring Security; `/api/admin/**` ADMIN only |
| Browse / book / cancel / history | Customer APIs |
| Validation + errors | Bean Validation + global handler, uniform JSON error |
| Tests | Unit (services), integration (MockMvc/H2), concurrency test |

## Domain model

```
User ─────────────< Booking >───────── Show
                      │                  │
                      ├─< BookingSeat >─ ShowSeat >── Seat >── Screen >── Theater >── City
                      ├── Payment        │
                      ├── Refund         Show >── Movie
                      └─< Notification   Show ──< ShowPrice (per seat type)

DiscountCode    PricingRule (weekend %)    RefundPolicy ──< RefundRule
```

## Booking flow

```
GET    /api/shows/{id}/seats             seat map with status + price
POST   /api/shows/{id}/holds {seatIds}   booking HELD, seats HELD for 10 min
POST   /api/bookings/{id}/pay            price → mock payment → CONFIRMED → async notification
DELETE /api/bookings/{id}/hold           release early
POST   /api/bookings/{id}/cancel         refund per policy, seats AVAILABLE
```

## Phases

| Phase | Scope | Status |
|---|---|---|
| 0 | Skeleton, GitHub, CLAUDE.md | Done |
| 1 | Foundation: Flyway, auditing base entity, error handling, security, registration, admin bootstrap | Done |
| 2 | Catalog: City, Theater, Screen, seat layout, Movie (admin CRUD + browse) | |
| 3 | Shows: overlap check, ShowSeat + price generation, seat map | |
| 4 | Seat holds: locking, expiry scheduler, concurrency test | |
| 5 | Pricing rules + discount codes | |
| 6 | Mock payment + confirmation (idempotent) | |
| 7 | Async notifications + reminder job | |
| 8 | Refund policies, cancellation, admin show cancel | |
| 9 | Booking history, pagination, Swagger | |
| 10 | Test hardening | |
| 11 | README, seed data, `.http` requests, video | |
