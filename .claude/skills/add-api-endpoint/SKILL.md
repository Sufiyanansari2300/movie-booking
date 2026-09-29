---
name: add-api-endpoint
description: Add or change a REST endpoint in the movie-booking service end to end (DTOs, service rules, controller, security, OpenAPI, tests, walkthrough, docs). Use whenever a feature adds or modifies an /api/** route.
---

# Add an API endpoint

The checklist followed for every endpoint in this project. `OpenApiIT` fails the build if a step is skipped for
documentation, so do them all.

1. **Schema first (if needed).** New Flyway migration `src/main/resources/db/migration/V<n>__<desc>.sql`. Never edit
   an applied one. SQL must run on MySQL and on H2 in MySQL mode (`DATETIME(6)`, `DECIMAL(10,2)`, named constraints).
   Hibernate only validates.
2. **Entity.** Extend `BaseEntity`; `@ManyToOne(fetch = LAZY)` only; enums `@Enumerated(STRING)`; `@Version` when
   concurrent writers are possible. Behaviour goes in small entity methods (`hold`, `confirm`, `cancel`).
3. **DTOs** in `dto/` as records. Validation annotations on requests (`@NotBlank`, `@Positive`, `@Digits`, …) and
   `@Schema(example = …)` for anything non-obvious.
4. **Service.** `@Transactional` on public methods. Throw `BadRequestException` (400), `ResourceNotFoundException`
   (404) or `ConflictException` (409) with a stable UPPER_SNAKE code. Inject `Clock` for time. Lock rows
   (`findByIdForUpdate`) **before** checking state that others can change. Prefer entity updates over bulk
   `@Modifying` queries. Return DTOs when lazy relations are touched (`open-in-view` is off).
5. **Controller.** Thin: `@Valid` body, caller via `@AuthenticationPrincipal AppUserPrincipal`, never a user id from
   the body. Add `@Tag` on the class, `@Operation(summary = …)` on every method, and
   `@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)` unless the route is public. Use `@ParameterObject` for
   `Pageable` and check sort fields with `SortValidator`.
6. **Security.** Admin routes live under `/api/admin/**`. Public routes must be added to `SecurityConfig` **and** to
   `OpenApiIT.isPublic`.
7. **Tests.**
   - Unit tests for pure logic (`*Test`).
   - An integration test (`*IT`, `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional`) covering the happy
     path, validation (400), auth (401/403), not found (404) and each business rejection by error code. Use
     `TestData`, `AuthTestSupport.login/bearer`, and `MutableClock` for time.
   - Anything that must commit (async, concurrency) uses its own H2 URL.
8. **Walkthrough.** Add a request to `http/catalog.http` (chain ids with `client.global.set`).
9. **Docs.** Regenerate `docs/openapi.json` + `docs/API.md` (see `docs/generate_api_md.py`); add rules to `CLAUDE.md`
   and the README if they are new decisions; add new error codes to the catalogue in `docs/DESIGN.md`.
10. Finish with the `verify-and-commit` skill.
