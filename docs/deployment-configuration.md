# Deployment and DEV/TEST acceptance

Each environment needs its own SSO client, OpenShift namespace and deploy credentials. Keep namespace details and request tracking out of the repository.

## SSO request

| Request field         | TAPS choice                                                         |
| --------------------- | ------------------------------------------------------------------- |
| Application           | Timber Appraisal and Pricing System (TAPS), modernizing ECAS + GAS2 |
| Protocol              | OpenID Connect                                                      |
| Client                | Public browser/SPA client                                           |
| Flow                  | Authorization code with PKCE                                        |
| Identity providers    | IDIR MFA and Business BCeID                                         |
| Basic BCeID           | Disabled                                                            |
| Environments          | DEV and TEST for now; PROD later                                    |
| Browser client secret | None                                                                |

The client ID is the `resource` value in each environment's installation JSON, not the request number. Frontend and backend must use the same issuer and client. Manage role assignments in FAM. Provider hints are `azureidir` and `bceidbusiness`.

Keycloak logout doesn't end the SiteMinder session behind Business BCeID, so sign-out goes to SiteMinder `logoff.cgi` first and then to Keycloak logout. DEV and TEST use `https://logontest7.gov.bc.ca/clp-cgi/logoff.cgi`. PROD must use `https://logon7.gov.bc.ca/clp-cgi/logoff.cgi`.

## Redirects and origins

| Environment                   | Callback URI                                                     | Origin / post-logout redirect                       |
| ----------------------------- | ---------------------------------------------------------------- | --------------------------------------------------- |
| Local development, DEV client | `http://localhost:3000/authCallback`                             | `http://localhost:3000`                             |
| DEV slots 0-49                | `https://nr-taps-<slot>.apps.gold.devops.gov.bc.ca/authCallback` | `https://nr-taps-<slot>.apps.gold.devops.gov.bc.ca` |
| TEST                          | `https://nr-taps-test.apps.gold.devops.gov.bc.ca/authCallback`   | `https://nr-taps-test.apps.gold.devops.gov.bc.ca`   |

The DEV slot is `PR number modulo 50`. List every host from 0 to 49 in the request:

```sh
for slot in $(seq 0 49); do
  printf 'https://nr-taps-%s.apps.gold.devops.gov.bc.ca\n' "$slot"
  printf 'https://nr-taps-%s.apps.gold.devops.gov.bc.ca/authCallback\n' "$slot"
done
```

The frontend uses `window.location.origin`, so sign-in returns to the host it started on. There is no PROD hostname or redirect yet.

## GitHub variables and secrets

Create `dev` and `test` GitHub Environments and restrict `test` to `main`.

| Scope                         | Name                                          | Value                                                                                  |
| ----------------------------- | --------------------------------------------- | -------------------------------------------------------------------------------------- |
| Repository variable           | `OC_SERVER`                                   | `https://api.gold.devops.gov.bc.ca:6443`                                               |
| Repository variable           | `OC_APPS_DOMAIN`                              | `apps.gold.devops.gov.bc.ca`                                                           |
| DEV environment variable      | `TAPS_OIDC_ISSUER_URI`                        | `https://dev.loginproxy.gov.bc.ca/auth/realms/standard`                                |
| TEST environment variable     | `TAPS_OIDC_ISSUER_URI`                        | `https://test.loginproxy.gov.bc.ca/auth/realms/standard`                               |
| DEV/TEST environment variable | `TAPS_OIDC_CLIENT_ID`                         | TAPS `resource` value for that environment                                             |
| DEV/TEST environment variable | `TAPS_OIDC_SITEMINDER_LOGOUT_URL`             | Optional; defaults to `https://logontest7.gov.bc.ca/clp-cgi/logoff.cgi`                |
| DEV/TEST environment variable | `TAPS_OIDC_IDIR_HINT`, `TAPS_OIDC_BCEID_HINT` | Optional; default to `azureidir` and `bceidbusiness`                                   |
| DEV/TEST environment variable | `TAPS_ORACLE_ENABLED`                         | `true` or `false`; unset means `false`. `true` turns on the backend's `oracle` profile |
| Repository secret (DEV)       | `oc_namespace`, `oc_token`                    | DEV namespace (ends in `-dev`) and deploy token                                        |
| TEST environment secret       | `oc_namespace`, `oc_token`                    | TEST namespace (ends in `-test`) and a separate deploy token                           |
| DEV/TEST environment secret   | `database_host`                               | Oracle TCPS host                                                                       |
| DEV/TEST environment secret   | `database_service_name`                       | Oracle service name                                                                    |
| DEV/TEST environment secret   | `database_user`                               | Proxy account                                                                          |
| DEV/TEST environment secret   | `database_password`                           | Proxy account password                                                                 |
| DEV/TEST environment secret   | `keystore_secret`                             | Passphrase for the generated truststore                                                |
| Repository secret             | `sonar_token_backend`, `sonar_token_frontend` | Optional; SonarCloud runs on pushes to `main` once set                                 |

- The database secrets are only required when `TAPS_ORACLE_ENABLED=true`. They're read from the `dev` or `test` environment only; callers don't pass them.
- TEST jobs use the `test` environment secrets. The deploy check requires the namespace suffix to match the environment, so a missing TEST secret fails instead of falling back to the DEV repository secret.
- Keep OIDC values in their environment, with no repository-level fallback.
- Limit each deploy token to this app in its own namespace. Both namespaces must be able to pull the GHCR images.
- There is no `TAPS_OIDC_CLIENT_SECRET`.

Set the public variables with `gh`:

```sh
gh variable set OC_SERVER --repo bcgov/nr-taps --body 'https://api.gold.devops.gov.bc.ca:6443'
gh variable set OC_APPS_DOMAIN --repo bcgov/nr-taps --body 'apps.gold.devops.gov.bc.ca'
gh variable set TAPS_OIDC_ISSUER_URI --repo bcgov/nr-taps --env dev --body 'https://dev.loginproxy.gov.bc.ca/auth/realms/standard'
gh variable set TAPS_OIDC_ISSUER_URI --repo bcgov/nr-taps --env test --body 'https://test.loginproxy.gov.bc.ca/auth/realms/standard'
```

Enter secrets through the GitHub UI or `gh secret set` from a prompt or stdin, so they stay out of shell history.

## OpenShift objects

`<zone>` is the PR number in DEV and `test` in TEST. `<slot>` is `PR number modulo 50` in DEV and `test` in TEST.

| Object                                       | Name                                                                |
| -------------------------------------------- | ------------------------------------------------------------------- |
| Backend Deployment and Service               | `nr-taps-backend-<zone>`                                            |
| Backend HPA (1-3 replicas on CPU)            | `nr-taps-backend-<zone>-cpu`                                        |
| Backend Secret                               | `nr-taps-backend-secret-<zone>`                                     |
| Frontend Deployment and Service (2 replicas) | `nr-taps-frontend-<zone>`                                           |
| Network policies                             | `nr-taps-backend-<zone>-ingress`, `nr-taps-frontend-<zone>-ingress` |
| Route                                        | `nr-taps-<slot>-frontend`                                           |

Every object is labelled `app` (`nr-taps-backend-<zone>` or `nr-taps-frontend-<zone>`), `app.kubernetes.io/name` (`nr-taps-backend` or `nr-taps-frontend`) and `app.kubernetes.io/instance` (`<zone>`). The Route carries the frontend labels of the PR that last deployed to the slot, so cleanup by label skips a Route a newer PR has taken over. The Route timeout is 300 seconds.

Parameters and defaults are in [backend/openshift.deploy.yml](../backend/openshift.deploy.yml), [frontend/openshift.deploy.yml](../frontend/openshift.deploy.yml) and [frontend/openshift.route.yml](../frontend/openshift.route.yml). Each deploy sets `ROLLOUT_TRIGGER` to the workflow run, so pods roll even when an image tag is reused.

PRs sharing a DEV route slot share a hostname; the latest deploy owns that Route. Deployment and Service names still use the full PR number.

Pods don't mount service-account tokens, drop all capabilities, block privilege escalation and use a read-only root filesystem with writable temp volumes. Network policies allow router-to-frontend and same-preview frontend-to-backend traffic. The frontend runs 2 replicas and the backend scales from 1 to 3 on CPU. The backend image runs an exploded jar with a 60% max heap and exits on out-of-memory so the pod restarts.

Spring's 60-second graceful shutdown plus a 10-second preStop fits in the 90-second termination budget. Caddy turns off upstream connection pooling so requests don't stick to a draining pod.

## Delivery lifecycle

| Event                | Target                                                      | URL                                               |
| -------------------- | ----------------------------------------------------------- | ------------------------------------------------- |
| PR opened or updated | DEV preview `nr-taps-backend-<PR>`, `nr-taps-frontend-<PR>` | `https://nr-taps-<PR modulo 50>.<OC_APPS_DOMAIN>` |
| PR merged to `main`  | TEST, using the PR's images                                 | `https://nr-taps-test.<OC_APPS_DOMAIN>`           |
| PR closed            | DEV preview removed, plus any left by other closed PRs      |                                                   |
| Weekly (Saturday)    | DEV previews older than a week removed; ZAP scan of TEST    |                                                   |

- PR builds tag images with the PR number and head SHA. DEV runs the SHA images. TEST runs the PR-numbered images, which get the `test` tag once the smoke test passes. Update a PR from `main` and let it rebuild before merging.
- The smoke test checks that the shell loads, anonymous `/api/me` gets 401, and both pods report the expected image tag. The tag check catches two PRs sharing a route slot. There are 50 slots to match the SSO redirect list.
- PR Validate posts the preview links on the PR. The Scheduled workflow also marks PRs and issues stale after 14 days and closes them 7 days later.
- Keep the OpenShift deployer action at v4.2.2 or later so rendered templates, which contain secrets, stay out of logs.

Configure branch protection in the repository settings.

**PROD deployment is disabled.** There is no PROD job, Route or image promotion. PROD will need its own hostname and certificate and a separate reviewed promotion path.

The PR workflow runs the backend tests and frontend checks before the DEV deploy. The Analysis workflow runs on non-draft PRs, pushes to `main`, weekly and on demand: backend tests with JaCoCo, frontend checks with dependency and supply-chain scans, the rehearsal issuer tests, and an advisory Trivy scan that reports to GitHub Security. SonarCloud runs on pushes to `main` once the `sonar_token_backend` and `sonar_token_frontend` secrets exist. CI doesn't run the Oracle integration tests or `test:security-config`.

## Oracle connection

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

`TAPS_ORACLE_ENABLED` accepts `true` or `false`; unset means `false`. When it's `true`, the workflow requires all five database secrets and deploys the backend with `SPRING_PROFILES_ACTIVE=oracle`. With that profile, the backend won't start if credentials are missing or the database is unreachable.

How it's wired:

- The backend template creates Secret `nr-taps-backend-secret-<zone>` with keys `DATABASE_USER`, `DATABASE_PASSWORD` and `KEYSTORE_SECRET`. PR cleanup deletes it.
- The `oracle` profile builds a TCPS descriptor from `DATABASE_HOST`, port 1543 and `DATABASE_SERVICE_NAME`.
- An init container (`ghcr.io/bcgov/nr-forest-client/common:prod`) reads the database certificate and writes a JKS truststore to `/cert/jssecacerts`, protected by `keystore_secret`. It only runs when the `oracle` profile is on. The truststore is rebuilt on every pod start, so a rotated certificate is picked up. The backend mounts it read-only.

### Runtime settings

The profile's settings are in
[application-oracle.yml](../backend/src/main/resources/application-oracle.yml). The secrets above supply the OpenShift variables; [backend/.env.example](../backend/.env.example) lists local settings.
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

## Activation acceptance

Complete these checks before activating reads in a shared environment. Having a proxy account does not establish connectivity, grants or application acceptance. See the [current status](../README.md#current-status); PROD remains disabled.

For each check, record the commit, image tag, environment, result and any follow-up. Keep credentials, tokens, account details and query results out of Git, screenshots and logs.

### 1. Identity and infrastructure

- [ ] Run the [local container checks](development.md#production-container-checks), register the SSO clients and callbacks, and configure the GitHub environments and TEST branch restriction.
- [ ] The namespace and deploy token match the environment. Check the service account's real permissions, not just the namespace suffix.
- [ ] Neither deploy identity can change PROD. A timeout doesn't count as a denial.
- [ ] Frontend and backend use the same issuer and client. Callbacks, logout and providers are registered for the exact host.
- [ ] The database team approves the proxy account, service, TLS trust, owners/synonyms and SELECT/EXECUTE grants. The read screens need no DML or DDL privileges.
- [ ] Set `database_host`, `database_service_name`, `database_user`, `database_password` and `keystore_secret` (see [GitHub variables and secrets](#github-variables-and-secrets)). Don't print them.

First deploy with `TAPS_ORACLE_ENABLED=false`, so the backend runs without the `oracle` profile. Check that anonymous `/api/me` returns 401, a signed-in user gets `readApiEnabled: false`, and the UI says reads are unavailable.

### 2. Check the schema

- [ ] Connect as the proxy account. Log in interactively or with a wallet, never with credentials on the command line.
- [ ] The objects in the [database privilege list](#database-privileges) and columns used by the [read adapters](../backend/src/main/java/ca/bc/gov/nrs/taps/read/oracle) exist with the expected types and resolve to the expected owners.
- [ ] The database team reviews `SIL_GET_CLIENT_NAME` and its dependencies.
- [ ] Verify attachment column sizes/nullability, driver/session date handling, and Oracle versus JVM day boundaries. Synthetic fixture definitions are not authoritative DDL.
- [ ] The database team confirms the grants match the approved list, with no DDL or `ANY` privileges.

Never load the local fixture DDL into a shared database.

### 3. Turn on DEV reads and compare with legacy

Set `TAPS_ORACLE_ENABLED=true` and deploy. The workflow starts the backend with the `oracle` Spring profile, and the backend must connect before it starts. Then compare TAPS with the legacy apps on real data:

| Area | Check |
| --- | --- |
| ECAS inbox | Filters, direct-ID lookup, labels, dates, ordering, page counts, separate mark/permit rows. |
| ECAS visibility | Draft/scenario rules use the same FAM grant. |
| Coast/Interior references | Revision counts, marks, FTAS defaults, location labels, nulls, ambiguous-context errors. |
| GAS search | All three families, status exclusions, client/organization paths, all marks; verify whether family, worksheet ID and mark form a unique paging key. |
| Appraised summary | ADS ownership, ADS/ADSC differences, rate order and precision, linked ECAS. |
| Historic summary | Stored ASR/NASR rows, species inputs, nullable fields, Coast species/grade; no new rates derived. |
| Non-appraised summary | NASR components, add-ons, expired selections; no recalculation. |
| Licence/FTA | Chooser, permit contexts, multi-permit totals, FTA shown even with no worksheets. |
| Lookups | ECAS and GAS code lists, active flags, date limits; organization choices stay within FAM grants. |
| Audit history | Scope, labels, event order, hidden Import comments, text truncation; `DBMS_LOB.GETLENGTH` is executable. |
| Attachments | Non-ZIP records, Coast vs Interior rules, empty lists, cross-parent denial; no download endpoint. |

Include intraday and sentinel dates and the native substitute for the SIL date helper. Use read-only methods. Don't call legacy code that might create, delete or recalculate rates. Include null, ambiguous and multi-row cases. Review query plans, pool size times replicas, and timeouts.

### 4. Authorization and browser

- [ ] Anonymous, expired, wrong-issuer/client/provider and malformed tokens are rejected. No-role users get no read access.
- [ ] Each district, region and forest-client grant sees the same records in search and detail. Out-of-scope and missing records both return 404.
- [ ] With mixed roles, capability and scope come from the same grant. Filters never widen scope. Report-only GAS access can't read worksheets. Real FAM region codes match the Oracle rollup.
- [ ] ECAS and GAS access stay separate, and direct API calls get the same denials as the UI.
- [ ] Walk ECAS search to reference to GAS summary, GAS search, the chooser and FTA. Check empty results, errors and retry, keyboard focus and mobile drawers.
- [ ] Sign-in, renewal, logout, re-login and Business BCeID logout work with the real providers.

### 5. TEST and runtime

- [ ] The merged PR was updated from `main` and rebuilt before merge. TEST runs that PR's images.
- [ ] DEV and TEST smoke tests pass on both image headers before the `test` tag moves.
- [ ] SCC, UID/GID, read-only filesystems, volumes, network policies, TLS, resources and probes work in the cluster.
- [ ] In an isolated TEST exercise, a database outage or full pool gives safe 503s while liveness/readiness stay up, and reads recover. Never cause an outage on a shared database.
- [ ] Rolling updates and SIGTERM shutdown finish within the termination budget.

Read-only status checks:

```sh
: "${taps_namespace:?Set the approved DEV or TEST namespace}"
: "${taps_zone:?Set the zone, a PR number or test}"
oc -n "$taps_namespace" get deployment "nr-taps-backend-$taps_zone" "nr-taps-frontend-$taps_zone"
oc -n "$taps_namespace" get deployment "nr-taps-backend-$taps_zone" -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
oc -n "$taps_namespace" get deployment "nr-taps-frontend-$taps_zone" -o jsonpath='{.spec.template.spec.initContainers[0].image}{"\n"}{.spec.template.spec.containers[0].image}{"\n"}'
oc -n "$taps_namespace" rollout status "deployment/nr-taps-backend-$taps_zone" --timeout=120s
oc -n "$taps_namespace" rollout status "deployment/nr-taps-frontend-$taps_zone" --timeout=120s
```

Keep ECAS and GAS2 running until each unported feature has a migration and rollback plan.

Writes remain disabled. The [Coast date draft](ui-foundation.md#coast-appraisal-date-draft) describes the checks needed before its save path can be implemented.
