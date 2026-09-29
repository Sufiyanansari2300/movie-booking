# API reference

Generated from the OpenAPI spec ([openapi.json](openapi.json)) by `docs/generate_api_md.py`. The live,
interactive version is Swagger UI at `/swagger-ui.html` once the app runs.

**Auth:** `public` = no token; `token` = `Authorization: Bearer <accessToken>` from `POST /api/auth/login`.
Routes under `/api/admin/**` also need the ADMIN role.

**60 operations** in 17 areas. Errors use the standard `ApiError` shape; see the [error catalogue](DESIGN.md#error-catalogue).

## Auth
_Registration, login and the current user_

| Method | Path | Auth | Description | Responses |
|---|---|---|---|---|
| `POST` | `/api/auth/login` | public | Log in with email + password and receive a 24h JWT access token | 200, 400, 401 |
| `GET` | `/api/auth/me` | token | Get the currently logged-in user | 200, 401 |
| `POST` | `/api/auth/register` | public | Register a new customer account | 201, 400, 409 |

## Browse - Cities & Theaters
_Public, no token needed_

| Method | Path | Auth | Description | Responses |
|---|---|---|---|---|
| `GET` | `/api/cities` | public | List all cities | 200 |
| `GET` | `/api/cities/{id}` | public | Get a city | 200, 400, 404 |
| `GET` | `/api/cities/{id}/theaters` | public | List theaters in a city | 200, 400, 404 |
| `GET` | `/api/theaters/{id}` | public | Get a theater | 200, 400, 404 |
| `GET` | `/api/theaters/{id}/screens` | public | List screens of a theater with seat counts | 200, 400, 404 |

## Browse - Movies
_Public, no token needed_

| Method | Path | Auth | Description | Responses |
|---|---|---|---|---|
| `GET` | `/api/movies` | public | Search movies by title, language and genre (paginated, sortable by title, releaseDate, durationMinutes, language, genre) <br>Params: `title`, `language`, `genre`, `page`, `size`, `sort` | 200, 400 |
| `GET` | `/api/movies/{id}` | public | Get a movie | 200, 400, 404 |

## Browse - Shows
_Public, no token needed_

| Method | Path | Auth | Description | Responses |
|---|---|---|---|---|
| `GET` | `/api/shows` | public | Find upcoming shows by city, theater, movie and date (paginated, sorted by startTime) <br>Params: `cityId`, `theaterId`, `movieId`, `date`, `page`, `size`, `sort` | 200, 400 |
| `GET` | `/api/shows/{id}` | public | Get a show with prices and available seat count | 200, 400, 404 |
| `GET` | `/api/shows/{id}/seats` | public | Seat map of a show: every seat with its status and price | 200, 400, 404 |

## Bookings
_Hold seats and manage your bookings (logged-in customer)_

| Method | Path | Auth | Description | Responses |
|---|---|---|---|---|
| `GET` | `/api/bookings/{id}` | token | Get one of your bookings (admins can see any) | 200, 400, 401, 404 |
| `POST` | `/api/bookings/{id}/discount` | token | Apply a discount code to a held booking (replaces any previous code) | 200, 400, 401, 404, 409 |
| `DELETE` | `/api/bookings/{id}/discount` | token | Remove the discount code from a held booking | 200, 400, 401, 404, 409 |
| `DELETE` | `/api/bookings/{id}/hold` | token | Release a hold before paying; the seats become available again | 200, 400, 401, 404, 409 |
| `POST` | `/api/shows/{showId}/holds` | token | Hold seats of a show for 10 minutes (all or nothing); pay before the hold expires | 201, 400, 401, 404, 409 |

## Payments
_Pay for a held booking (mock gateway) and see payment attempts_

| Method | Path | Auth | Description | Responses |
|---|---|---|---|---|
| `POST` | `/api/bookings/{id}/pay` | token | Pay for a held booking and confirm it. Retrying with the same Idempotency-Key never charges twice <br>Params: `Idempotency-Key*` | 200, 400, 401, 402, 404, 409, 502 |
| `GET` | `/api/bookings/{id}/payments` | token | List payment attempts of one of your bookings | 200, 400, 401, 404 |

## Cancellations & Refunds
_Cancel bookings (customer) or whole shows (ADMIN) with refunds_

| Method | Path | Auth | Description | Responses |
|---|---|---|---|---|
| `POST` | `/api/admin/shows/{id}/cancel` | token | Cancel a show (ADMIN): full refund for every paid booking, open holds released; re-run to resume | 200, 400, 401, 403, 404, 409 |
| `POST` | `/api/bookings/{id}/cancel` | token | Cancel a paid booking before the show starts; refunds per the policy in force when it was paid | 200, 400, 401, 404, 409 |
| `GET` | `/api/bookings/{id}/refund-quote` | token | What cancelling this booking right now would refund (per its refund policy) | 200, 400, 401, 404 |

## Booking History
_Your bookings, and admin booking search / show sales_

| Method | Path | Auth | Description | Responses |
|---|---|---|---|---|
| `GET` | `/api/admin/bookings` | token | Search all bookings (ADMIN) by status, upcoming/past, show, customer id or email <br>Params: `status`, `when`, `showId`, `userId`, `email`, `page`, `size`, `sort` | 200, 400, 401, 403 |
| `GET` | `/api/admin/shows/{id}/sales` | token | Occupancy and revenue of a show (ADMIN): booked/held/free seats, paid, refunded, net | 200, 400, 401, 403, 404 |
| `GET` | `/api/bookings/me` | token | Your booking history, newest first (filter by status and upcoming/past; sortable by createdAt, confirmedAt, show.startTime, totalAmount) <br>Params: `status`, `when`, `page`, `size`, `sort` | 200, 400, 401 |

## Notifications
_Confirmation, reminder and cancellation messages_

| Method | Path | Auth | Description | Responses |
|---|---|---|---|---|
| `GET` | `/api/admin/notifications` | token | All notifications, optionally by status (ADMIN), newest first <br>Params: `status`, `page`, `size`, `sort` | 200, 400, 401, 403 |
| `GET` | `/api/bookings/{id}/notifications` | token | Notifications sent for one of your bookings | 200, 400, 401, 404 |

## Admin - Cities
_Manage cities (ADMIN)_

| Method | Path | Auth | Description | Responses |
|---|---|---|---|---|
| `POST` | `/api/admin/cities` | token | Create a city | 201, 400, 401, 403, 409 |
| `PUT` | `/api/admin/cities/{id}` | token | Update a city | 200, 400, 401, 403, 404, 409 |
| `DELETE` | `/api/admin/cities/{id}` | token | Delete a city (blocked while it has theaters) | 204, 400, 401, 403, 404, 409 |

## Admin - Theaters
_Manage theaters (ADMIN)_

| Method | Path | Auth | Description | Responses |
|---|---|---|---|---|
| `POST` | `/api/admin/theaters` | token | Create a theater in a city | 201, 400, 401, 403, 409 |
| `PUT` | `/api/admin/theaters/{id}` | token | Update a theater | 200, 400, 401, 403, 404, 409 |
| `DELETE` | `/api/admin/theaters/{id}` | token | Delete a theater (blocked while it has screens) | 204, 400, 401, 403, 404, 409 |

## Admin - Screens & Seat Layouts
_Manage screens and their seat layouts (ADMIN)_

| Method | Path | Auth | Description | Responses |
|---|---|---|---|---|
| `PUT` | `/api/admin/screens/{id}` | token | Rename a screen | 200, 400, 401, 403, 404, 409 |
| `DELETE` | `/api/admin/screens/{id}` | token | Delete a screen and its seats (blocked once it has shows) | 204, 400, 401, 403, 404, 409 |
| `GET` | `/api/admin/screens/{id}/layout` | token | Get the seat layout row by row | 200, 400, 401, 403, 404 |
| `PUT` | `/api/admin/screens/{id}/layout` | token | Replace the whole seat layout from row sections, e.g. rows A-H x 12 REGULAR (blocked once it has shows) | 200, 400, 401, 403, 404, 409 |
| `POST` | `/api/admin/theaters/{theaterId}/screens` | token | Add a screen to a theater | 201, 400, 401, 403, 404, 409 |

## Admin - Movies
_Manage movies (ADMIN)_

| Method | Path | Auth | Description | Responses |
|---|---|---|---|---|
| `POST` | `/api/admin/movies` | token | Create a movie | 201, 400, 401, 403, 409 |
| `PUT` | `/api/admin/movies/{id}` | token | Update a movie | 200, 400, 401, 403, 404, 409 |
| `DELETE` | `/api/admin/movies/{id}` | token | Delete a movie (blocked once it has shows) | 204, 400, 401, 403, 404, 409 |

## Admin - Shows
_Schedule shows and set prices (ADMIN)_

| Method | Path | Auth | Description | Responses |
|---|---|---|---|---|
| `POST` | `/api/admin/shows` | token | Schedule a show (rejects overlaps on the screen; creates a seat map for the show) | 201, 400, 401, 403, 409 |
| `DELETE` | `/api/admin/shows/{id}` | token | Delete a show that has never been booked | 204, 400, 401, 403, 404, 409 |
| `PUT` | `/api/admin/shows/{id}/prices` | token | Change a show's base prices per seat type (affects new bookings only) | 200, 400, 401, 403, 404, 409 |

## Admin - Pricing Rules
_Weekend / prime-time surcharges on base prices (ADMIN)_

| Method | Path | Auth | Description | Responses |
|---|---|---|---|---|
| `GET` | `/api/admin/pricing-rules` | token | List all pricing rules | 200, 401, 403 |
| `POST` | `/api/admin/pricing-rules` | token | Create a pricing rule (WEEKEND, or PRIME_TIME with a time window); applicable rules add up | 201, 400, 401, 403, 409 |
| `PUT` | `/api/admin/pricing-rules/{id}` | token | Update a pricing rule (affects new holds only) | 200, 400, 401, 403, 404, 409 |
| `DELETE` | `/api/admin/pricing-rules/{id}` | token | Delete a pricing rule | 204, 400, 401, 403, 404, 409 |

## Admin - Discount Codes
_Percent / flat discount codes with limits (ADMIN)_

| Method | Path | Auth | Description | Responses |
|---|---|---|---|---|
| `GET` | `/api/admin/discount-codes` | token | List all discount codes with usage counts | 200, 401, 403 |
| `POST` | `/api/admin/discount-codes` | token | Create a discount code (PERCENT with optional cap, or FLAT; optional validity, limits, minimum order) | 201, 400, 401, 403, 409 |
| `GET` | `/api/admin/discount-codes/{id}` | token | Get a discount code | 200, 400, 401, 403, 404 |
| `PUT` | `/api/admin/discount-codes/{id}` | token | Update a discount code (the code text itself is fixed); set active=false to disable | 200, 400, 401, 403, 404, 409 |
| `DELETE` | `/api/admin/discount-codes/{id}` | token | Delete a discount code that has never been used | 204, 400, 401, 403, 404, 409 |

## Admin - Refund Policies
_Time-band refund policies; one active at a time (ADMIN)_

| Method | Path | Auth | Description | Responses |
|---|---|---|---|---|
| `GET` | `/api/admin/refund-policies` | token | List refund policies with their bands | 200, 401, 403 |
| `POST` | `/api/admin/refund-policies` | token | Create a refund policy, e.g. 48h -> 100%, 24h -> 50% (active=true makes it the active one) | 201, 400, 401, 403, 409 |
| `GET` | `/api/admin/refund-policies/{id}` | token | Get a refund policy | 200, 400, 401, 403, 404 |
| `PUT` | `/api/admin/refund-policies/{id}` | token | Update a refund policy that no booking uses yet | 200, 400, 401, 403, 404, 409 |
| `DELETE` | `/api/admin/refund-policies/{id}` | token | Delete an inactive refund policy that no booking uses | 204, 400, 401, 403, 404, 409 |
| `POST` | `/api/admin/refund-policies/{id}/activate` | token | Make this the active refund policy for new bookings | 200, 400, 401, 403, 404, 409 |

`*` = required parameter. Paginated endpoints accept `page` (0-based), `size` (max 100) and `sort=field,asc|desc`.
