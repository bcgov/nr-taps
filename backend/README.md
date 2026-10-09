# TAPS Backend

Spring Boot/Java API for the scoped ECAS and GAS reads. See [architecture](../docs/architecture.md) for component boundaries and token/grant enforcement.

## Running locally

Use the [root local development steps](../README.md#local-development). Both direct Maven and Docker Compose runs require the configured SSO issuer and public client ID, even with Oracle disabled.

## Spring profiles

| Profile | Behavior |
| --- | --- |
| Default | No datasource; sign-in and `/api/me` work, while business read routes stay disabled. |
| `oracle` | Registers the datasource, read adapters and routes; a database connection is required before startup completes. |

Enabling `oracle` does not create accounts or run migrations. In OpenShift it connects to that
environment's database: DEV, TEST or PROD, as defined in the [environment map](../README.md#environments).
Disabled or unavailable reads never fall back to synthetic data. Test fixtures are excluded from the
application package; see their [execution boundaries](src/test/resources/oracle/README.md#where-they-run).

## Configuration

The setup is Spring Boot with Undertow, JDBC/Hikari, startup pool validation,
secrets from the environment and state-based health probes. TAPS stays on Spring Boot 3.5.16 and
Java 21, with Jackson pinned to
[2.21.7](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21.7) for security fixes and
the Oracle driver pinned to `ojdbc11` 21.3.0.0. That driver rejects passwords longer than 30
characters, so keep the proxy account password within that.

| General variable | Default | Purpose |
| --- | --- | --- |
| `TAPS_OIDC_ISSUER_URI` | None | Required BC Gov SSO issuer. |
| `TAPS_OIDC_CLIENT_ID` | None | Required public client ID; must match the frontend. |
| `TAPS_IMAGE_REFERENCE` | `local` | Image identity reported by the backend. |

The profile's settings are in
[application-oracle.yml](src/main/resources/application-oracle.yml). The [deployment guide](../docs/deployment-configuration.md#github-variables-and-secrets) describes the OpenShift secret mapping; [backend/.env.example](.env.example) lists local settings.
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

The startup [OracleTlsCompatibility](src/main/java/ca/bc/gov/nrs/taps/configuration/OracleTlsCompatibility.java)
hook re-enables RSA key-exchange suites (`TLS_RSA_*`) without changing other Java restrictions.
Review this compatibility exception with the database administrators before deployment, and remove
it when no longer required. Keep environment-specific service and TLS details in private operational records.

The Hikari pool holds at most 10 connections with 1 idle, and a request waits up to 30 seconds for
one. Idle connections close after 10 minutes and connections live at most 30 minutes. Size the pool
for the replica count (the backend scales to 3) and the proxy account's connection limit.

`/actuator/health` includes datasource health. Liveness and readiness use app state,
so a database outage doesn't restart every pod. Read failures return 503. Turning Oracle on doesn't
create accounts or run migrations.

To diagnose 503s, set `TAPS_FAILURE_DIAGNOSTICS_LOG_LEVEL=DEBUG`. The
`ca.bc.gov.nrs.taps.audit.failure` logger then records the method, route with IDs masked, failure
types, SQLState and vendor error code. It never logs exception messages, which can carry SQL or
connection details.

## Database privileges

From the SQL in [`read/oracle`](src/main/java/ca/bc/gov/nrs/taps/read/oracle). Names are
unqualified, so the database team needs to confirm the owner and synonym of each object for the
proxy account. Don't assume one owner because the test fixture has one.

`SELECT` on these 54 objects:

| Objects | Used for |
| --- | --- |
| `APPRAISAL_DATA_SUBMISSION`, `APPRAISAL_DATA_SUBMISSION_CTRL`, `ADS_SUBMITTED_TIMBER_MARK` | ECAS records, marks and appraised parents |
| `ADS_ASSIGNED_TO_USER` | Existing ECAS My To Do assignments |
| `APPRAISAL_CATEGORY_CODE`, `APPRAISAL_STATUS_CODE`, `NON_APPRAISED_STATUS_CODE`, `REAPPRAISAL_REASON_CODE`, `STAND_RATE_ELIGIBILITY_CODE` | Code labels and choices |
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
| `WORKSHEET_REFERENCE_TYPE_CODE`, `APPRAISAL_FOREST_ZONE_CODE`, `NON_APPRAISED_RATE_TYPE_CODE` | Non-appraised worksheet labels |
| `SCALE_SPECIES_CODE`, `SCALE_PRODUCT_CODE`, `SCALE_GRADE_CODE` | Stored rate labels |
| `NON_APPRAISED_WS_RATE_ADDON`, `NON_APPRAISED_RATE_ADDON_CODE` | Selected add-ons |
| `HISTORIC_SPECIES`, `HISTORIC_COAST_SPECIES_GRADE` | Historic species and Coast grades |

Also `EXECUTE` on `SIL_GET_CLIENT_NAME` (inbox client names), the only function called. The
database team should check its body, rights and dependencies; the fixture version returns
synthetic names. `DUAL`, built-in functions and `DBMS_LOB.GETLENGTH` use normal public access.

No `GAS2_*` or `PKG_ECAS*` package is called. The account needs no `INSERT`, `UPDATE`, `DELETE`,
DDL, schema ownership or `ANY` privilege.

## API reference

`/api/me` exposes identity, grants, capabilities and read availability, including `ecasMyToDoAvailable`. Assigned queues require a signed provider username; the GUID audit fallback cannot identify legacy assignments. Health probes are public; business routes require both the `oracle` profile and the matching capability. The [read API guide](../docs/oracle-reads.md#http-routes) lists all routes, page sizes, errors and contracts. It also records the SQL ownership mappings and family-specific behavior.

Review [intentional legacy divergences](../docs/intentional-legacy-divergences.md) separately when assessing parity.

## Testing

From `backend` with Java 21:

```sh
mvn -B -DskipITs verify     # no database needed; JaCoCo report in target/site/jacoco
```

### Oracle integration tests

From `backend` with Docker running:

```sh
mvn -B -Poracle-it verify
# Run only the HTTP/runtime failure cases:
mvn -B -Poracle-it -Dit.test=OracleReadResilienceIT verify
```

The `oracle-it` Maven profile starts disposable Oracle containers for local validation. It does not
use the OpenShift TEST environment or TEST database, and is separate from the application's `oracle`
Spring profile. Normal `mvn test` does not start Oracle; selecting `oracle-it` fails if Docker or the
image is unavailable. See the [fixture inventory and database isolation](src/test/resources/oracle/README.md)
before running it.

[OracleReadIT](src/test/java/ca/bc/gov/nrs/taps/integration/OracleReadIT.java) exercises the read adapters, Oracle SQL, paging, dates, nulls, exact amounts, worksheet families and scope rules. Its HTTP scenario substitutes only token decoding. [OracleReadResilienceIT](src/test/java/ca/bc/gov/nrs/taps/integration/OracleReadResilienceIT.java) uses real HTTP, the production JWT decoder and synthetic signed tokens to check regional denials, invalid tokens, pool exhaustion, statement timeouts, database outages and recovery. Neither suite validates the shared schema, actual grants, TLS, real FAM tokens, query plans or business-policy acceptance.

Shared-environment checks are in [DEV/TEST activation acceptance](../docs/deployment-configuration.md#activation-acceptance).
