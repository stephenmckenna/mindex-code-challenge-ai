# Coding Challenge
## What's Provided
A simple [Spring Boot](https://projects.spring.io/spring-boot/) web application has been created and bootstrapped with data. The application contains 
information about all employees at a company. On application start-up, an in-memory Mongo database is bootstrapped with 
a serialized snapshot of the database. While the application runs, the data may be accessed and mutated in the database 
without impacting the snapshot.

### How to Run
The application may be executed by running `gradlew bootRun`.

*Spring Boot 3 requires Java 17 or higher. This project targets Java 17. If you want to change the targeted Java 
version, you can modify the `sourceCompatibility` variable in the `build.gradle` file.*

### How to Use
The following endpoints are available to use:
```
* CREATE
    * HTTP Method: POST 
    * URL: localhost:8080/employee
    * PAYLOAD: Employee
    * RESPONSE: Employee
* READ
    * HTTP Method: GET 
    * URL: localhost:8080/employee/{id}
    * RESPONSE: Employee
* UPDATE
    * HTTP Method: PUT 
    * URL: localhost:8080/employee/{id}
    * PAYLOAD: Employee
    * RESPONSE: Employee
* READ REPORTING STRUCTURE
    * HTTP Method: GET
    * URL: localhost:8080/employee/{id}/reportingStructure
    * RESPONSE: ReportingStructure
* CREATE COMPENSATION
    * HTTP Method: POST
    * URL: localhost:8080/employee/{id}/compensation
    * PAYLOAD: Compensation (salary and effectiveDate)
    * RESPONSE: Compensation
* READ COMPENSATION HISTORY
    * HTTP Method: GET
    * URL: localhost:8080/employee/{id}/compensation
    * RESPONSE: Compensation[] (newest effectiveDate first)
```

The Employee has a JSON schema of:
```json
{
  "title": "Employee",
  "type": "object",
  "properties": {
    "employeeId": {
      "type": "string"
    },
    "firstName": {
      "type": "string"
    },
    "lastName": {
      "type": "string"
    },
    "position": {
      "type": "string"
    },
    "department": {
      "type": "string"
    },
    "directReports": {
      "type": "array",
      "items": {
        "anyOf": [
          {
            "type": "string"
          },
          {
            "type": "object"
          }
        ]
      }
    }
  }
}
```
For all endpoints that require an `id` in the URL, this is the `employeeId` field.

The ReportingStructure has a JSON schema of:
```json
{
  "title": "ReportingStructure",
  "type": "object",
  "properties": {
    "employee": {
      "$ref": "Employee"
    },
    "numberOfReports": {
      "type": "integer"
    }
  }
}
```

The Compensation has a JSON schema of:
```json
{
  "title": "Compensation",
  "type": "object",
  "properties": {
    "compensationId": {
      "type": "string"
    },
    "employeeId": {
      "type": "string"
    },
    "salary": {
      "type": "number",
      "exclusiveMinimum": 0
    },
    "effectiveDate": {
      "type": "string",
      "format": "date"
    }
  },
  "required": ["salary", "effectiveDate"]
}
```
`compensationId` and `employeeId` are assigned by the server; any values sent in a create request are ignored.

Example:
```
curl -X POST localhost:8080/employee/16a596ae-edd3-4847-99fe-c4518e82c86f/compensation \
     -H 'Content-Type: application/json' \
     -d '{"salary": 85000.00, "effectiveDate": "2025-01-01"}'
```

Errors are returned as Spring Boot's standard error body, with a `message` describing the problem:

| Status | When |
|---|---|
| 400 Bad Request | Compensation body is missing `salary` or `effectiveDate`, `salary` is not positive, or a value cannot be parsed. The `errors` array names each invalid field. |
| 404 Not Found | No employee exists with the given `id` (all endpoints) |
| 409 Conflict | The employee already has a compensation with the same `effectiveDate`; the message names the existing `compensationId` |

### How to Test
Tests run with `gradlew test` (JUnit 5).

## What to Implement
This coding challenge was designed to allow for flexibility in the approaches you take. While the requirements are 
minimal, we encourage you to explore various design and implementation strategies to create functional features. Keep in
mind that there are multiple valid ways to solve these tasks. What's important is your ability to justify and articulate
the reasoning behind your design choices. We value your thought process and decision-making skills. Also, If you 
identify any areas in the existing codebase that you believe can be enhanced, feel free to make those improvements.

### Task 1
Create a new type called `ReportingStructure` that has two fields: `employee` and `numberOfReports`.

The field `numberOfReports` should equal the total number of reports under a given employee. The number of reports is 
determined by the number of `directReports` for an employee, all of their distinct reports, and so on. For example,
given the following employee structure:
```
                   John Lennon
                 /             \
         Paul McCartney     Ringo Starr
                            /         \
                       Pete Best    George Harrison
```
The `numberOfReports` for employee John Lennon (`employeeId`: 16a596ae-edd3-4847-99fe-c4518e82c86f) would be equal to 4.

This new type should have a new REST endpoint created for it. This new endpoint should accept an `employeeId` and return
the fully filled out `ReportingStructure` for the specified `employeeId`. The values should be computed on the fly and 
will not be persisted.

### Task 2
Create a new type called `Compensation` to represent an employee's compensation details. A `Compensation` should have at 
minimum these two fields: `salary` and `effectiveDate`. Each `Compensation` should be associated with a specific 
`Employee`. How that association is implemented is up to you.

Create two new REST endpoints to create and read `Compensation` information from the database. These endpoints should 
persist and fetch `Compensation` data for a specific `Employee` using the persistence layer.

## Implementation Notes

### Task 1: ReportingStructure
`GET /employee/{id}/reportingStructure` returns the employee as stored (with `directReports` as id-only references) and
the total number of distinct reports beneath them. Nothing is persisted; the count is computed on each request.

- **`ReportingStructure` is a Java record.** It is computed, never persisted and never mutated, which is exactly what a
  record models.
- **Direct reports are stored as id-only references**, so each level of the tree must be looked up in the database
  rather than traversed in memory.
- **The tree is walked level by level, not recursively.** Each level is fetched with a single
  `findByEmployeeIdIn` query, so the number of queries equals the depth of the org chart instead of the number of
  employees, and very deep hierarchies cannot overflow the stack.
- **A visited set guarantees each person is counted once.** Because employees can be edited through
  `PUT /employee/{id}`, the data can contain cycles (A reports to B, B reports to A) or someone listed under two
  managers. Both are counted once and logged as warnings. References to employees that do not exist are skipped and
  logged. None of these fail the request.

### Task 2: Compensation
`POST /employee/{id}/compensation` creates a record and `GET /employee/{id}/compensation` returns the employee's full
compensation history, newest `effectiveDate` first.

- **Compensation lives in its own collection, linked by `employeeId`.** Alternatives considered:
    - *Embedding it in `Employee`* would expose salary in every employee and reporting structure response, and
      `PUT /employee/{id}` replaces the whole document, so any update that omitted compensation would erase it.
    - *Embedding a copy of the `Employee`* in each compensation would go stale as employees are updated.
    - *`@DBRef`* adds lazy-loading complexity that a lookup by id does not need.
- **Records form a history.** A raise is a new record with a new `effectiveDate`; earlier records are kept. Two
  records for the same employee and date would be ambiguous, so the second is rejected with 409.
- **`salary` is a `BigDecimal` and `effectiveDate` is a `LocalDate`.** Floating point cannot represent many decimal
  amounts exactly, and an effective date is a calendar day with no time or time zone.
- **The request body is the `Compensation` type itself**, matching how `POST /employee` takes an `Employee`. Ids are
  always assigned by the server and records are written with `insert()` rather than `save()`, so a client cannot
  overwrite an existing record by supplying its id.
- **The create response matches `POST /employee`** (the created object with a 200), and each successful create is
  logged at INFO.

### Improvements to the Existing Code
- **Fixed `PUT /employee/{id}` creating duplicate employees.** `Employee` had no `@Id`, so every `save()` inserted a
  new document, and any later read of that employee failed with a 500. `employeeId` is now the document id.
- **Unknown employee ids return 404 instead of 500**, via `EmployeeNotFoundException`.
- **Lombok** replaces hand-written getters, setters and constructors, and `@Slf4j` replaces manual logger fields.
- **Constructor injection** (`@RequiredArgsConstructor` with `final` fields) replaces `@Autowired` field injection,
  which makes classes easier to construct in unit tests.
- **Tests migrated from JUnit 4 to JUnit 5**, which Spring Boot 3's test tooling is built on.
- **Null fields are omitted from JSON responses**, so id-only `directReports` no longer serialize as a list of nulls.

### Testing
| Layer | Tooling | Covers |
|---|---|---|
| Service unit tests | Mockito | Report counting edge cases (cycles, duplicates, dangling ids, batching), compensation id assignment, conflicts, not-found handling |
| Controller tests | `@WebMvcTest` | Routing, JSON shape, status codes, request validation |
| JSON/model tests | `@JsonTest` | Serialization of dates, decimals and id-only references; validation constraints |
| Integration tests | `@SpringBootTest` | End-to-end requests against the in-memory Mongo, including the real repository queries and seed data |

Integration tests create their own employees so they never modify the seed data other tests rely on.

### Known Limitations
- **Duplicate-date check race.** The 409 check reads before it inserts, so two identical requests arriving at the same
  moment could both succeed. A unique index on (`employeeId`, `effectiveDate`) would close this at the database
  level; it was not added because enforcement by the in-memory `mongo-java-server` is unverified.
- **No authorization.** Compensation data is sensitive and would normally be restricted to authorized users.

## Delivery
Please upload your results to a publicly accessible Git repo. Free ones are provided by GitHub and Bitbucket.
