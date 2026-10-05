# Disposable Oracle read acceptance

From `backend`, run `mvn -B -ntp -Poracle-it verify` with Docker running. Normal `mvn test` does not start Oracle. Selecting `oracle-it` fails if Docker or the image is unavailable; integration acceptance is never silently skipped.

`OracleReadIT` starts `gvenzl/oracle-free:23.26.3-slim-faststart`, which supports Apple ARM and x86 hosts, with a randomly generated fixture-only password. Its listener binds only to loopback on a random port. Testcontainers removes the container at completion. No existing database, persistent Docker volume, cloud service, proxy credential, or OpenShift namespace is used.

The schema is deliberately minimal and entirely synthetic. It exercises the Oracle SQL actually used by every implemented read adapter, including scalar subqueries, CTEs, unions, `LISTAGG`, `ROW_NUMBER`, date bounds, nulls, exact decimals, all worksheet families, regional/client visibility, multi-mark/permit rows, out-of-range page totals, and ambiguous relationships. Each adapter test rolls back its fixture changes. The HTTP scenario starts the real Spring app with Hikari, the production security/controller configuration, and those same Oracle readers. Only token decoding is substituted by a decoder defined on the test classpath.

`SIL_GET_CLIENT_NAME` is a **test-only lookup helper**, not a reconstruction or acceptance test of the production function. `V_CLIENT_PUBLIC` is represented by a fixture table. These tests do not establish the production schema, object grants, synonyms, TLS/truststore behavior, actual FAM tokens, production query plans, data distributions, or ECAS/GAS business-policy signoff. Those require the proxy account and deployed TEST acceptance.

Primary references: [Testcontainers Oracle Free](https://java.testcontainers.org/modules/databases/oraclefree/) and [gvenzl image tags and ARM support](https://github.com/gvenzl/oci-oracle-free).
