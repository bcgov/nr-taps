# Oracle read runtime and access inventory

Oracle reads are off by default (`TAPS_ORACLE_ENABLED=false`): no datasource, business routes
denied, and `/api/me` returns `readApiEnabled: false`. When enabled, the app registers the readers
and must open a connection before startup completes. Sign-in is required either way.

The setup follows nr-lexis: Spring Boot with Undertow, JDBC/Hikari, startup pool validation,
secrets from the environment and state-based health probes. TAPS stays on Spring Boot 3.5.16 and
Java 21, with Jackson pinned to
[2.21.7](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21.7) for security fixes.

Query rules are in [Oracle read foundation](oracle-read-foundation.md) and response shapes in
[legacy read contracts](legacy-read-contracts.md).

## Configuration

OpenShift sets these from the database secrets (see
[deployment configuration](deployment-configuration.md)); locally the rehearsal scripts do. Keep
credentials out of Git, images and frontend config. The JDBC URL must start with
`jdbc:oracle:thin:@` and must not contain credentials.

| Variable | Default | Notes |
| --- | --- | --- |
| `TAPS_ORACLE_ENABLED` | `false` | Turns on the datasource, readers and routes. |
| `TAPS_ORACLE_JDBC_URL` | Empty | Required when enabled. |
| `TAPS_ORACLE_USERNAME` | Empty | Required when enabled. Proxy account. |
| `TAPS_ORACLE_PASSWORD` | Empty | Required when enabled. |
| `TAPS_ORACLE_MAXIMUM_POOL_SIZE` | `10` | 1-30 per replica. |
| `TAPS_ORACLE_MINIMUM_IDLE` | `1` | 0 to the pool maximum. |
| `TAPS_ORACLE_CONNECTION_TIMEOUT_MS` | `10000` | Pool wait, 1000-60000. |
| `TAPS_ORACLE_CONNECT_TIMEOUT_MS` | `10000` | Network connect, 1000-60000. |
| `TAPS_ORACLE_READ_TIMEOUT_MS` | `30000` | Socket read, 1000-120000. |
| `TAPS_ORACLE_QUERY_TIMEOUT_SECONDS` | `20` | Statement timeout, 1-60. |
| `TAPS_ORACLE_TRUSTSTORE_PATH` | Empty | OpenShift sets `/cert/jssecacerts`. |
| `TAPS_ORACLE_TRUSTSTORE_TYPE` | `JKS` | Required with a truststore path. |
| `TAPS_ORACLE_TRUSTSTORE_PASSWORD` | Empty | Required with a truststore path. OpenShift uses `keystore_secret`. |

The JDBC URL picks TCP or TCPS. As in nr-lexis, TCPS trusts the certificate the init container
imports for the host, with no extra DN check and no way to skip verification. Only the local test
database uses TCP. Idle connections close after 10 minutes and connections live at most 30
minutes. Size the pool for the replica count and the proxy account's connection limit.

`/actuator/health` includes datasource health. Liveness and readiness use app state, as in LEXIS,
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

Errors return only `code` and `message`: `INVALID_REQUEST` (400), `AUTHENTICATION_REQUIRED` (401),
`ACCESS_DENIED` (403), `NOT_FOUND` (404) and `READ_UNAVAILABLE` (503). Missing and inaccessible
records both return 404. No SQL, grants or stack traces are exposed. Other business paths are denied.

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

## Preflight check

Run the [SQL*Plus pack](../scripts/oracle-preflight.sql) from the repository root in a fresh
session as the proxy account. Log in interactively or with a wallet, never with credentials in the
script or command line.

```sql
@scripts/oracle-preflight.sql
```

It prints session, schema, clock and NLS details; resolves each object through synonyms and
rejects database links; and parses zero-row SELECTs for 253 columns to check their types. It
describes the `SIL_GET_CLIENT_NAME` call without running it. Any failure exits non-zero, and it
always rolls back, so use a fresh session. A clean run confirms access and types, not result
parity.

The [inventory](../scripts/oracle-preflight-inventory.json) drives the pack. 110 column families
are checked against legacy DDL; the rest come from the legacy queries. The pack uses `DBMS_SQL`,
`DBMS_OUTPUT` and metadata views, which the app doesn't need. If those are restricted, agree an
alternative with the database team instead of adding grants to the app account.

The script doesn't spool. Don't commit target metadata, account names or output; just record
which checks passed.

`python3 scripts/oracle-preflight.py` checks the generated SQL without a database. After editing
the inventory, run it with `--write` and rerun the integration tests.

## Local integration tests

With Java 21 and Docker:

```sh
mvn -Poracle-it verify
```

This runs the readers and HTTP layer against a disposable Oracle Free container with a synthetic
schema. The normal test suite needs no database. See
[OracleReadIT](../backend/src/test/java/ca/bc/gov/nrs/taps/integration/OracleReadIT.java) and the
[fixture schema](../backend/src/test/resources/oracle/schema.sql). Never load the fixture into a
shared database.

## Enabling DEV or TEST

Work through [activation acceptance](activation-acceptance.md) and the
[open questions](oracle-read-foundation.md#open-questions). PROD activation and retiring ECAS/GAS2
are separate decisions.

Nothing here writes to Oracle. The [Coast date draft](coast-appraisal-date-draft.md) is in-memory
only.
