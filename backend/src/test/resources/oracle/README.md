# Disposable Oracle test fixtures

The seven `.sql` files in this directory create and populate a disposable Oracle container for
**local validation only**. They are not setup scripts or seed data for the DEV, TEST or PROD
databases. Those databases belong to their corresponding OpenShift environments; see the
[environment map](../../../../../README.md#environments). Never execute these fixtures against them.

| Files | Purpose |
| --- | --- |
| [schema.sql](schema.sql) / [seed.sql](seed.sql) | Minimal tables and synthetic submissions, clients, regions, licences, timber marks and worksheets for the read tests. |
| [attachments-schema.sql](attachments-schema.sql) / [attachments-seed.sql](attachments-seed.sql) | Attachment metadata tables and synthetic records for document-type visibility, filename handling and cross-submission isolation. No actual documents or binary content. |
| [audit-schema.sql](audit-schema.sql) / [audit-seed.sql](audit-seed.sql) | Audit tables and synthetic events, comments and changes for history filtering and cross-submission isolation. |
| [client-name-function.sql](client-name-function.sql) | A test substitute for `SIL_GET_CLIENT_NAME` that looks up synthetic client names; it does not replace the existing database function. |

The fixtures use existing table and column names, but their definitions are deliberately incomplete and are not authoritative DDL. The seeded records are synthetic, not production exports, and contain no real credentials or attachments. Keeping the files in Git makes the integration tests and local rehearsal reproducible from a checkout.

Test isolation is separate from approval to publish inherited schema and rule details; see the [public repository content guidance](../../../../../SECURITY.md#public-repository-content).

## Where they run

**The application, deployment pipeline and supplied fixture loaders have no execution path that loads these files into the DEV, TEST or PROD databases.** This isolation is enforced by how the files are packaged and how the loaders connect:

- Maven excludes these test resources and the test classes from the production application package. The [backend Dockerfile](../../../../../backend/Dockerfile) copies only the packaged application into the final runtime image, so neither the SQL files nor their test loaders are present in a deployed backend.
- [OracleReadIT](../../java/ca/bc/gov/nrs/taps/integration/OracleReadIT.java) and [OracleReadResilienceIT](../../java/ca/bc/gov/nrs/taps/integration/OracleReadResilienceIT.java) create the fixture connection directly from their Testcontainers Oracle instance's JDBC URL, username and generated password. They do not use the application's configured datasource to load fixtures.
- The [local rehearsal script](../../../../../scripts/local-read-stack.sh) creates a new Oracle container and runs SQLPlus inside that container, connecting to `127.0.0.1:1521/FREEPDB1` with generated fixture credentials. That address is the container's own loopback, and the loader has no external database target option.

Setting `DATABASE_*` or `SPRING_DATASOURCE_*` environment variables cannot redirect these fixture loaders to a live database. Enabling the application's `oracle` profile does not load the fixtures. TAPS continues to use the existing ECAS/GAS schema without schema changes or data migration.

This guarantee applies to the supplied code and deployment configuration. The files are plain SQL: someone could manually copy and execute them in another database session with sufficient privileges. They cannot prevent that separate action and must never be run manually against a shared database.

## Validation scope

Run commands and suite coverage are in [Oracle integration tests](../../../../README.md#oracle-integration-tests). Testcontainers uses generated fixture credentials, a random loopback listener and disposable databases; no existing database, cloud service or proxy credentials are used.

`SIL_GET_CLIENT_NAME` is a test substitute and `V_CLIENT_PUBLIC` is a fixture table. Neither establishes the production function/view behavior. Shared-schema, grants, TLS, real FAM roles, query plans and business-policy acceptance require the [DEV/TEST checks](../../../../../docs/deployment-configuration.md#activation-acceptance).
