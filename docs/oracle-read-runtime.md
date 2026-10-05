# Oracle read runtime and access inventory

Oracle reads run only with the `oracle` Spring profile, which is off by default. Without it there's
no datasource, business routes are denied, and `/api/me` returns `readApiEnabled: false`. With it,
the app registers the readers and must open a connection before startup completes. Sign-in is
required either way.

The setup is Spring Boot with Undertow, JDBC/Hikari, startup pool validation,
secrets from the environment and state-based health probes. TAPS stays on Spring Boot 3.5.16 and
Java 21, with Jackson pinned to
[2.21.7](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21.7) for security fixes and
the Oracle driver pinned to `ojdbc11` 21.3.0.0. That driver rejects passwords longer than 30
characters, so keep the proxy account password within that.

Query rules are in [Oracle read foundation](oracle-read-foundation.md) and response shapes in
[legacy read contracts](legacy-read-contracts.md).

## Configuration

The profile's settings are in
[application-oracle.yml](../backend/src/main/resources/application-oracle.yml). OpenShift sets the
variables from the database secrets (see [deployment configuration](deployment-configuration.md));
locally the rehearsal scripts do, and [backend/.env.example](../backend/.env.example) lists them.
Keep credentials out of Git, images, frontend config and the JDBC URL.

| Variable | Default | Notes |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Empty | Include `oracle` to turn on the datasource, readers and routes. |
| `DATABASE_HOST` | None | Required for TCPS. |
| `DATABASE_PORT` | `1543` | TCPS port. |
| `DATABASE_SERVICE_NAME` | None | Required for TCPS. |
| `DATABASE_USER` | None | Required with `oracle`. Proxy account. |
| `DATABASE_PASSWORD` | None | Required with `oracle`. |
| `KEYSTORE_SECRET` | None | Required with `oracle`, even over TCP. Truststore password; OpenShift uses `keystore_secret`. |
| `TRUSTSTORE_PATH` | `/cert/jssecacerts` | JKS truststore written by the init container. |
| `DATABASE_CONNECT_TIMEOUT_MS` | `10000` | Network connect. |
| `DATABASE_READ_TIMEOUT_MS` | `30000` | Socket read. |
| `DATABASE_QUERY_TIMEOUT_SECONDS` | `20` | Statement timeout. |
| `SPRING_DATASOURCE_URL` | TCPS descriptor | Local only. Replaces the descriptor for a plain-TCP database. |
| `TAPS_HTTP_WORKER_THREADS` | `64` | Undertow workers. Reads hold one while Oracle answers. |
| `APP_LOG_LEVEL` | `INFO` | TAPS log level. |
| `TAPS_FAILURE_DIAGNOSTICS_LOG_LEVEL` | `INFO` | `DEBUG` logs database codes for read failures (see below). |

OpenShift always uses TCPS. It trusts the certificate the init container imports for the host,
with no extra DN check. Only local test databases use TCP. The driver's default NIO transport
ignores the read timeout, so the profile sets `oracle.jdbc.javaNetNio=false`.

The shared database only accepts `TLS_RSA_WITH_AES_256_CBC_SHA` after the listener hands off a
TCPS connection, and Java 21.0.12 disables `TLS_RSA_*` suites. At startup TAPS removes
`TLS_RSA_*` from the JVM's disabled list and leaves the other Java defaults
([OracleTlsCompatibility](../backend/src/main/java/ca/bc/gov/nrs/taps/configuration/OracleTlsCompatibility.java)).
Drop this once the database supports ECDHE.

The Hikari pool holds at most 10 connections with 1 idle, and a request waits up to 30 seconds for
one. Idle connections close after 10 minutes and connections live at most 30 minutes. Size the pool
for the replica count (the backend scales to 3) and the proxy account's connection limit.

`/actuator/health` includes datasource health. Liveness and readiness use app state,
so a database outage doesn't restart every pod. Read failures return 503. Turning Oracle on doesn't
create accounts or run migrations.

## HTTP routes

Every route needs a TAPS access token. The capability is checked first, then the reader applies
record scope from the same user's grants. Licence, client, district or region in a request only
filters.

| Route | Capability | Notes |
| --- | --- | --- |
| `POST /api/ecas/inbox?page=0` | `ECAS_SUBMISSION_VIEW` | `EcasInbox.Search` body with `mode: "ALL_SUBMISSIONS"`. 100 per page. |
| `GET /api/ecas/lookups` | `ECAS_SUBMISSION_VIEW` | Methods, codes with active hints, scoped organizations. |
| `GET /api/gas/lookups` | `GAS_APPRAISAL_VIEW` | Effective method, status and rate-adjustment lists. |
| `GET /api/ecas/references/{method}/{ecasId}` | `ECAS_SUBMISSION_VIEW` | `method` is `C` or `I`. |
| `GET /api/ecas/audit/{ecasId}?page=0` | `ECAS_SUBMISSION_VIEW` | Audit history, 100 per page. |
| `GET /api/ecas/audit/{ecasId}/events/{eventId}?page=0` | `ECAS_SUBMISSION_VIEW` | Field changes and comments for one event. |
| `GET /api/ecas/{ecasId}/attachments?page=0` | `ECAS_SUBMISSION_VIEW` | Document metadata, 50 per page. See [attachment inventory](attachment-inventory.md). |
| `GET /api/gas/worksheets?licence=&timberMark=&page=0` | `GAS_APPRAISAL_VIEW` | All three families, 10 per page. |
| `GET /api/gas/worksheets/{type}/{worksheetId}` | `GAS_APPRAISAL_VIEW` | `APPRAISED`, `HISTORIC` or `NON_APPRAISED`. |
| `GET /api/gas/appraised/by-ecas/{ecasId}` | `GAS_APPRAISAL_VIEW` | Appraised summary for an ECAS ID. |
| `GET /api/gas/licences/{licence}/marks` | `GAS_APPRAISAL_VIEW` | HA marks. Empty is valid. |
| `GET /api/gas/licence-information?licence=&timberMark=` | `GAS_APPRAISAL_VIEW` | FTA licence panel. Mark required. |

Pages are zero-based. IDs and rates are strings, dates are ISO strings and missing values are null.
Search returns every mark/permit row, so don't dedupe by ID. `MY_TO_DO` listing isn't built.

Errors are `application/problem+json` with `title`, `detail`, `status` and a `code` that clients
match on: `INVALID_REQUEST` (400), `AUTHENTICATION_REQUIRED` (401), `ACCESS_DENIED` (403),
`NOT_FOUND` (404) and `READ_UNAVAILABLE` (503). Missing and inaccessible records both return 404.
No SQL, grants or stack traces are exposed. Other business paths are denied.

To diagnose 503s, set `TAPS_FAILURE_DIAGNOSTICS_LOG_LEVEL=DEBUG`. The
`ca.bc.gov.nrs.taps.audit.failure` logger then records the method, route with IDs masked, failure
types, SQLState and vendor error code. It never logs exception messages, which can carry SQL or
connection details.

## Database privileges

From the SQL in [`read/oracle`](../backend/src/main/java/ca/bc/gov/nrs/taps/read/oracle). Names are
unqualified, so the database team needs to confirm the owner and synonym of each object for the
proxy account. Don't assume one owner because the test fixture has one.

`SELECT` on these 46 objects:

| Objects | Used for |
| --- | --- |
| `APPRAISAL_DATA_SUBMISSION`, `APPRAISAL_DATA_SUBMISSION_CTRL`, `ADS_SUBMITTED_TIMBER_MARK` | ECAS records, marks and appraised parents |
| `APPRAISAL_CATEGORY_CODE`, `APPRAISAL_STATUS_CODE`, `NON_APPRAISED_STATUS_CODE`, `REAPPRAISAL_REASON_CODE` | Code labels and choices |
| `ORG_UNIT` | District/region rollup and labels |
| `ECAS_AUDIT_EVENT`, `ECAS_ACTION_CODE`, `ECAS_AUDIT_COMMENT`, `ECAS_AUDIT_DETAIL` | Audit history and inbox audit filters |
| `ECAS_SUBMITTED_FILE` | File metadata (no binary column) |
| `ADS_SUPPORT_DOCUMENT`, `APPRAISAL_DOCUMENT_TYPE_CODE`, `APPRAISAL_ATTACHMENT_XREF` | Attachment inventory |
| `APPRAISED_WORKSHEET`, `HISTORIC_APPRAISED_WORKSHEET`, `NON_APPRAISED_WORKSHEET` | Worksheet parents |
| `APPRAISED_STUMPAGE_RATE`, `NON_APPRAISED_STUMPAGE_RATE` | Stored rates |
| `HAULING_AUTHORITY`, `HARVESTING_AUTHORITY`, `HARVESTING_HAULING_XREF` | Licence/mark/permit links and ownership |
| `BLANKET_ROAD_MARK`, `PRIVATE_MARK_CERTIFICATE` | Road/private FTA context |
| `FOREST_FILE_CLIENT`, `FOREST_CLIENT`, `V_CLIENT_PUBLIC` | Client links and names (`V_CLIENT_PUBLIC` is a view in the real schema, a table in the fixture) |
| `PROV_FOREST_USE`, `FILE_TYPE_CODE` | Licence data and filters |
| `TENURE_FILE_STATUS_CODE`, `HARVEST_AUTH_STATUS_CODE`, `PRIVATE_MARK_STATUS_CODE` | FTA status labels |
| `ADS_SPECIES_VOLUME`, `ADS_CUTTING_AUTHORITY_DETAIL` | Coast volume and major centre |
| `INT_POINT_OF_APPRAISAL_CODE`, `POINT_OF_APPRAISAL` | Interior appraisal point and selling zone |
| `TSA_NUMBER_CODE`, `TSB_NUMBER_CODE` | Management-unit labels |
| `APPRAISAL_METHOD_CODE`, `RATE_ADJUSTMENT_TYPE_CODE` | GAS lookups |
| `NON_APPRAISED_WS_RATE_ADDON`, `NON_APPRAISED_RATE_ADDON_CODE` | Selected add-ons |
| `HISTORIC_SPECIES`, `HISTORIC_COAST_SPECIES_GRADE` | Historic species and Coast grades |

Also `EXECUTE` on `SIL_GET_CLIENT_NAME` (inbox client names), the only function called. The
database team should check its body, rights and dependencies; the fixture version returns
synthetic names. `DUAL`, built-in functions and `DBMS_LOB.GETLENGTH` use normal public access.

No `GAS2_*` or `PKG_ECAS*` package is called. The account needs no `INSERT`, `UPDATE`, `DELETE`,
DDL, schema ownership or `ANY` privilege.

## Local integration tests

With Java 21 and Docker:

```sh
mvn -B -Poracle-it verify
```

This runs the readers and HTTP layer against a disposable Oracle Free container with a synthetic
schema. CI doesn't run it. The normal test suite needs no database. See
[OracleReadIT](../backend/src/test/java/ca/bc/gov/nrs/taps/integration/OracleReadIT.java) and the
[fixture schema](../backend/src/test/resources/oracle/schema.sql). Never load the fixture into a
shared database.

## Enabling DEV or TEST

Work through [activation acceptance](activation-acceptance.md) and the
[open questions](oracle-read-foundation.md#open-questions). PROD activation and retiring ECAS/GAS2
are separate decisions.

Nothing here writes to Oracle. The [Coast date draft](coast-appraisal-date-draft.md) is in-memory
only.
