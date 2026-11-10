# Notes service

A REST backend for capturing notes and sharing them with individuals or whole
teams. Built with Spring Boot 3.5 on Java 17.

- **Runnable in one command**, with demo data seeded at startup.
- **28 tests** covering the access-control matrix end to end.
- **Interactive docs** at <http://localhost:8080/swagger-ui.html>.

---

## Running it

Requires a JDK 17+ on `PATH` or `JAVA_HOME`. The bundled Maven Wrapper fetches
Maven itself, so no Maven installation is needed.

```bash
./mvnw spring-boot:run
```

```bash
./mvnw test
```

```bash
./mvnw package && java -jar target/notes-service-0.1.0.jar
```

On Windows use `mvnw.cmd` in place of `./mvnw`.

The service listens on **8080**. Useful URLs:

| URL | What it is |
| --- | --- |
| `/swagger-ui.html` | Interactive API docs (authorise with any demo user) |
| `/v3/api-docs` | OpenAPI 3 spec |
| `/actuator/health` | Liveness check |
| `/h2-console` | Database browser (JDBC URL `jdbc:h2:mem:notes`, user `sa`, no password) |

### Demo data

The in-memory database is seeded at startup. Every demo user has the password
`password`.

| User | Teams | Notes |
| --- | --- | --- |
| `alice` | platform (admin), design (member) | *Release runbook*, *Sprint 14 retro* |
| `bob` | platform (member) | *Scratch pad* |
| `carol` | design (admin) | *Brand voice notes* |
| `dave` | design (member) | — |

Shares in the seed data: *Release runbook* → team **platform** (WRITE),
*Sprint 14 retro* → **bob** (READ), *Brand voice notes* → team **design**
(READ) and **alice** (WRITE). That last pair is deliberate: alice reaches the
note two ways, and the stronger grant wins.

Set `app.seed.enabled=false` to start empty.

---

## Try it

```bash
curl -s -u alice:password http://localhost:8080/api/v1/notes
```

```bash
curl -s -u alice:password -X POST http://localhost:8080/api/v1/notes -H 'Content-Type: application/json' -d '{"title":"Standup topics","content":"Discuss flaky CI","tags":["ci"]}'
```

```bash
curl -s -u alice:password -X POST http://localhost:8080/api/v1/notes/1/shares -H 'Content-Type: application/json' -d '{"teamName":"design","permission":"READ"}'
```

```bash
curl -s -u bob:password 'http://localhost:8080/api/v1/notes?q=runbook&scope=SHARED'
```

---

## API

All endpoints are under `/api/v1` and require authentication.

### Notes

| Method | Path | Who may call it |
| --- | --- | --- |
| `GET` | `/notes` | Any user — returns only what they can see |
| `POST` | `/notes` | Any user; caller becomes the owner |
| `GET` | `/notes/{id}` | Owner, or anyone with a READ/WRITE share |
| `PUT` | `/notes/{id}` | Owner, or a WRITE share |
| `DELETE` | `/notes/{id}` | Owner only |
| `GET` | `/notes/{id}/shares` | Anyone who can read the note |
| `POST` | `/notes/{id}/shares` | Owner only |
| `DELETE` | `/notes/{id}/shares/{shareId}` | Owner only |

`GET /notes` query parameters:

| Parameter | Default | Meaning |
| --- | --- | --- |
| `scope` | `ALL` | `ALL`, `OWNED`, or `SHARED` (shared *with* the caller) |
| `q` | — | Case-insensitive substring match on title **and** content |
| `tag` | — | Exact tag match |
| `page`, `size` | `0`, `20` | Paging |
| `sort` | `updatedAt,desc` | Any note field, e.g. `title,asc` |

### Teams

| Method | Path | Who may call it |
| --- | --- | --- |
| `GET` | `/teams` | Any user — returns their own teams |
| `POST` | `/teams` | Any user; caller becomes the first admin |
| `GET` | `/teams/{id}/members` | Team members only |
| `POST` | `/teams/{id}/members` | Team admins only |
| `DELETE` | `/teams/{id}/members/{userId}` | Team admins only |

### Users

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/users/me` | Identity of the authenticated caller |

### Shapes

A note:

```json
{
  "id": 1,
  "title": "Release runbook",
  "content": "1. Freeze main. 2. Cut the release branch.",
  "tags": ["release", "ops"],
  "owner": { "id": 1, "username": "alice", "displayName": "Alice Nguyen" },
  "access": "OWNER",
  "shares": [
    { "id": 1, "targetType": "TEAM", "targetId": 1, "targetName": "platform",
      "permission": "WRITE", "createdAt": "2026-11-10T17:12:23.11Z" }
  ],
  "version": 0,
  "createdAt": "2026-11-10T17:12:23.11Z",
  "updatedAt": "2026-11-10T17:12:23.11Z"
}
```

A list response (`content` items carry an `excerpt` instead of full `content`):

```json
{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0, "last": true }
```

Errors are RFC 7807 `application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid",
  "instance": "/api/v1/notes",
  "errors": { "title": "title must not be blank" }
}
```

---

## How access control works

One rule, applied in one place ([`NoteAccessPolicy`](src/main/java/com/example/notes/service/NoteAccessPolicy.java)):

> A user's access to a note is the **strongest** of: owner (if they created it),
> the permission on a share aimed at them directly, and the permission on a
> share aimed at any team they belong to.

That yields `OWNER` > `WRITE` > `READ` > `NONE`, and three deliberate choices:

- **A note the caller cannot read returns 404, not 403.** A 403 would confirm
  that note 42 exists and belongs to someone. 403 is used only once the caller
  can already see the note but is reaching past their permission — editing with
  a READ share, or deleting as a non-owner.
- **Team shares are evaluated live, not expanded on write.** Add someone to a
  team and they immediately see that team's notes; remove them and access ends.
  There is a test for exactly this (`newMemberGainsAccessToTeamNotes`).
- **Only the owner can re-share or delete.** A WRITE grant lets you edit the
  content, not hand it onward.

Listing resolves access for a whole page with one extra query rather than one
per note, via a flat `ShareAccessRow` projection. The access filter itself is a
pair of correlated `EXISTS` subqueries
([`NoteSpecifications`](src/main/java/com/example/notes/repository/NoteSpecifications.java)),
so a note shared several ways still appears once and `totalElements` stays
accurate without `DISTINCT`.

---

## Design notes

**Layering.** Controllers handle HTTP and nothing else; services own the
transactions and all the rules; repositories own the queries. Services return
DTOs rather than entities, so no lazy-loading escapes its transaction and the
JSON contract cannot drift when the schema changes. `spring.jpa.open-in-view` is
off for the same reason.

**Authentication** is HTTP Basic against BCrypt hashes in the `users` table.
That keeps the service self-contained and inspectable with `curl`. Next step here would be to implement token handling via JWT or OAuth 2.0.

**Concurrent edits.** Notes carry a JPA `@Version`, returned to clients. Send it
back on `PUT` and a stale value is rejected with 409 rather than silently
overwriting a colleague's edit; omit it and last-write-wins. Two writers who race
past that check still hit the database-level check, which surfaces as the same
409.

**Validation and errors.** Bean Validation on the request records, with a single
`@RestControllerAdvice` translating everything to `problem+json`. It extends
`ResponseEntityExceptionHandler` deliberately: a bare catch-all swallows Spring
MVC's own exceptions and turns unknown paths into 500s. That exact bug appeared
during development and is now pinned by `unknownPathIsNotFound` and
`badRequestsKeepTheirStatus`.

**Tags** are trimmed, lower-cased and de-duplicated on write, so `Ops`, `ops `
and `ops` are one tag and filtering needs no case juggling.

**Database.** In-memory H2 with Hibernate generating the schema, so the service
runs with nothing installed. A `postgres` profile
([`application-postgres.yml`](src/main/resources/application-postgres.yml))
points at a real database:

```bash
SPRING_PROFILES_ACTIVE=postgres POSTGRES_URL=jdbc:postgresql://localhost:5432/notes ./mvnw spring-boot:run
```

Note content is a plain `varchar(20000)` rather than a `@Lob`, which keeps
`LIKE` search working identically on H2 and PostgreSQL.

---

## Tests

```bash
./mvnw test
```

28 tests: 27 drive the API through the real HTTP stack with security enabled
(`MockMvc` + the full application context), plus one context-load check. They
are integration tests rather than unit tests because the behaviour worth
protecting here *is* the interaction between authentication, authorisation and
persistence — a mocked service layer would not catch a wrong status code or a
share that fails to apply.

- [`NoteApiTest`](src/test/java/com/example/notes/NoteApiTest.java) — the
  access matrix (unshared/READ/WRITE/team/owner × read/write/delete/share),
  404-vs-403, strongest-share-wins, optimistic locking, validation, search,
  filtering, paging, framework error codes.
- [`TeamApiTest`](src/test/java/com/example/notes/TeamApiTest.java) — team
  visibility, admin-only membership changes, last-admin protection, and a
  new member gaining access to the team's notes.

Each test starts from an empty schema and rebuilds a known fixture, deleting in
foreign-key order so the real constraints get exercised.

---

## Key design choices

1. **AI-assisted**: Given that the instructions explicitly state that all available tools including AI tools are encouraged, and given the time-constraint for the task, I elected to use Claude Code (Opus 5 High) for the initial code generation.  As AI-generated code can be risky or a contentious subject, I wanted to provide additional reasoning:
    1. While code quality and security are legitimate concerns for code intended to enter into production, there are inherent tradeoffs
for smaller internal-only tasks developed under tight time constraints.
    2. Initial code quality concerns are mitigated by carefully crafting AI tool prompting prior to code generation to provide high levels of
context and detailed purpose to the tool.  Subsequent quality improvements are made by developer review and edits prior to the initial commit.
    3. Security concerns, while minimal in projects intended only for internal or personal use when compared to production systems, are
nonetheless critical items for any development work, particularly those involving AI tool assistance.
    4. Providing example users with basic provided passwords was determined to be acceptable for this task as an example, but all
provided contents were reviewed to confirm that no real-world security issues were present (ex: exposed GitHub credentials).

2. **User/Team security**: While not specified in the task instructions, user and team access management was an important design choice for the following reasons:
    1. While this is just an example task for a simple REST service, the role being tested for is for an organization where application security
and access management is critical.  It would be unwise to not demonstrate some acknowledgement of access management even in a simple implementation.
    2. In almost every case, simple security is better than no security, so long as it's done well.  Incorrectly implemented security features 
can hinder rather than help if they block legitimate access and/or don't stop bad actors.

3. **Test coverage**: Based on my experience in my current and previous roles, taking the time now to implement tests prevents breakdowns
and headaches in the future.  While unit and integration tests are generally critical (and implemented here) for validating happy/sad paths for internal code and external request/response handling, it is also important for more robust systems to incorporate performance and destructive testing to provide team members and leadership with confidence in the system's ability to withstand less-than-ideal conditions.  While performance and destructive tests were not included in this project due to time constraints, they would be on my short list of next steps before or shortly after production deployment, depending on user onboarding strategy.

---

## What I would do next

Roughly in the order I would pick it up:

1. **Implement team-based restrictions** such that a user in team A cannot share a
   note with users in team B without authorization between teams.
2. **Replace Basic auth** with JWT or an OAuth2 resource server, and add user
   registration — right now users only arrive via the seeder.
3. **Restrict `sort`** to an allow-list of fields. Today any note property is
   sortable, which leaks a little of the schema shape.
4. **Note history**, so an unwanted edit by a teammate with WRITE access can be
   recovered.
5. **Rate limiting and audit logging** on the sharing endpoints.

### Known limits

- Nothing is paginated except notes. Team membership lists are returned whole,
  which is fine for small teams and would not be for large ones.
- Deleting a user is not supported, so their notes cannot be orphaned.
