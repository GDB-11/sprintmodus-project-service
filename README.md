# sprintmodus-project-service

Projects and sprints (port **8092**, reached through the api-gateway at `/api/projects/**` and `/api/sprints/**`).

Every request under `/api/**` needs a JWT. common-lib's `TenantSecurityFilter` verifies it, checks the user is an active
member of the tenant named in the token, and selects that tenant's database for the rest of the request (see the
common-lib README). Subscription limits come from the token's claims, so this service never reads the master database.

## Endpoints

Ids in URLs and JSON are UUID codes (`projectCode`, `sprintCode`); internal database ids never leave the persistence layer.

| Method | Path | Notes |
|---|---|---|
| GET | `/api/projects` | Active projects, by name |
| POST | `/api/projects` | `{name, description?, key?}` → `201`. Without `key` one is derived from the name (`WAR` for "Web App Rewrite") and made unique (`WAR2`). `402 PROJECT_LIMIT_REACHED` at the plan's `maxProjects` (from the JWT), `409` key taken, `400` invalid |
| GET / PUT | `/api/projects/{projectCode}` | PUT `{name, description?}`; the key never changes |
| DELETE | `/api/projects/{projectCode}` | Soft delete (with its sprints), `204`. Owners and admins only. Frees the plan slot, the key stays reserved |
| GET | `/api/projects/{projectCode}/members` | Active members (the creator joins automatically) |
| GET | `/api/sprints?projectCode=` | A project's sprints, by start date |
| POST | `/api/sprints` | Owners/admins only (`403 FORBIDDEN` otherwise, like every write to a sprint below). `{projectCode, name, startDate?, plannedVelocity?}` → `201`. `endDate = startDate + the tenant's configured days`. Without `startDate`: the next configured start weekday. `409 SPRINT_OVERLAP` |
| GET / PUT | `/api/sprints/{sprintCode}` | PUT `{name, startDate?, plannedVelocity?}`; dates only while PLANNED (the sprint keeps its own length); a closed sprint cannot change |
| POST | `/api/sprints/{sprintCode}/start` | PLANNED → ACTIVE; `409 ANOTHER_SPRINT_ACTIVE` if the project already has one. Begins the burndown: records day 0 (the hours in the sprint on the eve of its first day) and today's point, in the same transaction |
| POST | `/api/sprints/{sprintCode}/close` | ACTIVE → CLOSED, `204`. Takes the burndown's last snapshot first, in the same transaction; from then on velocity and burndown never change |
| GET | `/api/sprints/{sprintCode}/burndown` | `{sprintCode, sprintName, status, startDate, endDate, days, baselineHours, points: [{day, date, idealRemainingHours, remainingHours}]}`. Day 0 is the eve of the first day (the starting scope); days `1..days` are the sprint's own. `remainingHours` is `null` for a day that has not happened (or, in a closed sprint, came after it closed); a day nobody touched carries the last value forward. The ideal falls linearly from day 0 to 0 on the last day. Any member may read it |
| GET | `/api/sprints/velocity-history?projectCode=&limit=` | The last `limit` (default 6, 1-24) CLOSED sprints of the project, oldest first, and their `averageVelocity` (one decimal); only closed sprints count because only their velocity is final |
| GET | `/api/sprints/{sprintCode}/velocity` | Recorded velocity next to what the items add up to (`view_Sprint_Overview`) |
| PUT | `/api/sprints/{sprintCode}/velocity` | `{velocity}`, `204`. Called by workitem-service with the caller's own token. `409` once the sprint is closed (frozen); succeeds without recording when velocity tracking is off |
| GET / PUT | `/api/sprints/config` | `{defaultSprintDays (1-90), sprintStartDay, velocityTrackingEnabled}`; PUT for owners and admins only. Affects sprints created afterwards |

Errors are `{code, message, timestamp}`: 400 invalid input, 401 no/invalid token, 402 plan limit, 403 not allowed, 404 not
found (also for another tenant's ids), 409 conflict.

## Rules worth knowing

- **Planning sprints is for owners and admins** (creating, changing, starting, closing; decided 2026-09-24 with Phase 10, matching workitem-service's rule for moving items between sprints). Reading is for every member.
- **The burndown is recorded as it happens** (there is no daily job: the services have no list of tenants). `BurndownData` rows are written by the statement `INSERT ... SELECT ... FROM SprintBurndownToday ON DUPLICATE KEY UPDATE` (view of tenant migration V1.11): this service on start and close, workitem-service after every change to a sprint's remaining hours. The last write of a day is that day's end-of-day value. *Remaining hours* = the `RemainingHours` of the sprint's leaf items that are not in a terminal status (`SprintRemainingHours`), so a PBI and its Tasks are never counted twice. Only ACTIVE sprints have a snapshot view, so a planned sprint has no burndown and a closed sprint's is locked.
- **Sprints are half-open** `[startDate, endDate)`, so back-to-back sprints do not overlap. A project has at most one active sprint.
- **Concurrency-safe limits.** Creating a project counts and inserts in one transaction that first locks the tenant's
  singleton `TenantSprintConfig` row; sprint changes lock the project's row. Parallel requests cannot exceed the plan or
  create overlapping sprints (integration tests fire 12 and 8 parallel requests; mutation-tested).
- A new project also gets its `WorkItemSequence` (first work item is 1000) and its creator as member, atomically.

## Layout

Clean Architecture: `domain` (plain Java) ← `application` (use cases returning `Result<T, E>`, ports) ← `adapter/rest`
(controllers, DTOs, exhaustive error-to-status mapping) and `infrastructure` (configuration, persistence).

**Every query is native SQL.** JPA/Hibernate only maps results to entities. Repository interfaces are ports in
`application/port/persistence`; in `infrastructure/persistence` there is, per table, an `XxxEntity`, an `XxxQueries`
interface (bare Spring Data `Repository`, each method a hand-written `@Query(nativeQuery = true)`) and the
`Jpa*Repository` implementations. `ArchitectureRulesTest` fails the build on JPQL, derived queries, `save`/`persist`, or
persistence technology outside `infrastructure`.

Two JPA settings matter here: `PhysicalNamingStrategyStandardImpl` (columns are PascalCase, Boot would snake_case them) and
`@Modifying(clearAutomatically = true)` on native updates (a native query returning an entity reuses an instance already
loaded in the transaction, so a read after an update would be stale).

## Running and tests

Needs MySQL with tenant databases (register an organization through auth-service), Eureka, and the same `JWT_SECRET` as
auth-service.

```bash
./mvnw spring-boot:run     # `dev` profile: development-only secret; logs which database each request is routed to
./mvnw verify              # Docker required
```

The service **never runs Flyway**: tenant databases are created and migrated by auth-service at onboarding (schema in
`sprintmodus-db-tenant`); tests build their own with the same migrations. Unit tests use in-memory fakes.
`ProjectsAndSprintsApiIntegrationTest` starts `mysql:9.7` and exercises every endpoint through the real security filter with
real JWTs (limits from the token, concurrency, tenant isolation, roles, the JSON contract with workitem-service).
`TenantRoutingIntegrationTest` covers the tenant routing itself.

No OpenFeign dependency: this service makes no outgoing calls.
