# Disposable Oracle test fixtures

The seven `.sql` files in this directory create and populate a disposable Oracle test database so the read adapters can be exercised without a proxy account. **They are test fixtures, not deployment migrations or setup scripts for the existing ECAS/GAS database.** Never execute them against a shared database.

| Files | Purpose |
| --- | --- |
| [schema.sql](schema.sql) / [seed.sql](seed.sql) | Minimal tables and synthetic submissions, clients, regions, licences, timber marks and worksheets for the read tests. |
| [attachments-schema.sql](attachments-schema.sql) / [attachments-seed.sql](attachments-seed.sql) | Attachment metadata tables and synthetic records for document-type visibility, filename handling and cross-submission isolation. No actual documents or binary content. |
| [audit-schema.sql](audit-schema.sql) / [audit-seed.sql](audit-seed.sql) | Audit tables and synthetic events, comments and changes for history filtering and cross-submission isolation. |
| [client-name-function.sql](client-name-function.sql) | A test substitute for `SIL_GET_CLIENT_NAME` that looks up synthetic client names; it does not replace the existing database function. |

The fixtures use existing table and column names, but their definitions are deliberately incomplete and are not authoritative DDL. The seeded records are synthetic, not production exports, and contain no real credentials or attachments. Keeping the files in Git makes the integration tests and local rehearsal reproducible from a checkout.

## Where they run

**The application, deployment pipeline and supplied fixture loaders have no execution path that loads these files into the live ECAS/GAS database.** This isolation is enforced by how the files are packaged and how the loaders connect:

- Maven excludes these test resources and the test classes from the production application package. The [backend Dockerfile](../../../../../backend/Dockerfile) copies only the packaged application into the final runtime image, so neither the SQL files nor their test loaders are present in a deployed backend.
- [OracleReadIT](../../java/ca/bc/gov/nrs/taps/integration/OracleReadIT.java) and [OracleReadResilienceIT](../../java/ca/bc/gov/nrs/taps/integration/OracleReadResilienceIT.java) create the fixture connection directly from their Testcontainers Oracle instance's JDBC URL, username and generated password. They do not use the application's configured datasource to load fixtures.
- The [local rehearsal script](../../../../../scripts/local-read-stack.sh) creates a new Oracle container and runs SQLPlus inside that container, connecting to `127.0.0.1:1521/FREEPDB1` with generated fixture credentials. That address is the container's own loopback, and the loader has no external database target option.

Setting `DATABASE_*` or `SPRING_DATASOURCE_*` environment variables cannot redirect these fixture loaders to a live database. Enabling the application's `oracle` profile does not load the fixtures. TAPS continues to use the existing ECAS/GAS schema without schema changes or data migration.

This guarantee applies to the supplied code and deployment configuration. The files are plain SQL: someone could manually copy and execute them in another database session with sufficient privileges. They cannot prevent that separate action and must never be run manually against a shared database.

## Running the integration tests

From `backend`, run `mvn -B -ntp -Poracle-it verify` with Docker running. Normal `mvn test` does not start Oracle. Selecting `oracle-it` fails if Docker or the image is unavailable; integration acceptance is never silently skipped.

`OracleReadIT` starts `gvenzl/oracle-free:23.26.3-slim-faststart`, which supports Apple ARM and x86 hosts, with a randomly generated fixture-only password. Its listener binds only to loopback on a random port. Testcontainers removes the container at completion. No existing database, persistent Docker volume, cloud service, proxy credential, or OpenShift namespace is used.

The schema is deliberately minimal and entirely synthetic. It exercises the Oracle SQL actually used by every implemented read adapter, including scalar subqueries, CTEs, unions, `LISTAGG`, `ROW_NUMBER`, date bounds, nulls, exact decimals, all worksheet families, regional/client visibility, multi-mark/permit rows, out-of-range page totals, and ambiguous relationships. Each adapter test rolls back its fixture changes. The HTTP scenario starts the real Spring app with Hikari, the production security/controller configuration, and those same Oracle readers. Only token decoding is substituted by a decoder defined on the test classpath.

`SIL_GET_CLIENT_NAME` is a **test-only lookup helper**, not a reconstruction or acceptance test of the production function. `V_CLIENT_PUBLIC` is represented by a fixture table. These tests do not establish the production schema, object grants, synonyms, TLS/truststore behavior, actual FAM tokens, production query plans, data distributions, or ECAS/GAS business-policy signoff. Those require the proxy account and deployed TEST acceptance.

Primary references: [Testcontainers Oracle Free](https://java.testcontainers.org/modules/databases/oraclefree/) and [gvenzl image tags and ARM support](https://github.com/gvenzl/oci-oracle-free).
