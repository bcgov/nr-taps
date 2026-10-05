# DEV/TEST activation acceptance

Run this checklist once the OpenShift namespace, SSO client and Oracle proxy account exist. None of it has been run yet. PROD stays disabled.

For each check, record the commit, image tag, environment, result and any follow-up. Keep credentials, tokens, account details and query results out of Git, screenshots and logs.

## 1. Identity and infrastructure

- [ ] The namespace and deploy token match the environment. Check the service account's real permissions, not just the namespace suffix.
- [ ] Neither deploy identity can change PROD. A timeout doesn't count as a denial.
- [ ] Frontend and backend use the same issuer and client. Callbacks, logout and providers are registered for the exact host.
- [ ] The database team approves the proxy account, service, TLS trust, owners/synonyms and SELECT/EXECUTE grants. The read screens run no DML or DDL, even though the account will later get write grants.
- [ ] Set `database_host`, `database_service_name`, `database_user`, `database_password` and `keystore_secret` (see [deployment configuration](deployment-configuration.md)). Don't print them.

First deploy with `TAPS_ORACLE_ENABLED=false`. Check that anonymous `/api/me` returns 401, a signed-in user gets `readApiEnabled: false`, and the UI says reads are unavailable.

## 2. Check the schema

- [ ] Connect as the proxy account. Log in interactively or with a wallet, never with credentials on the command line.
- [ ] The tables and columns listed in [Oracle read runtime](oracle-read-runtime.md) exist with the types the read adapters expect, and resolve to the expected owners.
- [ ] The database team reviews `SIL_GET_CLIENT_NAME`.
- [ ] The database team confirms the grants match the approved list, with no DDL or `ANY` privileges.

Never load the local fixture DDL into a shared database.

## 3. Turn on DEV reads and compare with legacy

Set `TAPS_ORACLE_ENABLED=true` and deploy. The backend must connect before it starts. Then compare TAPS with the legacy apps on real data:

| Area | Check |
| --- | --- |
| ECAS inbox | Filters, direct-ID lookup, labels, dates, ordering, page counts, separate mark/permit rows. |
| ECAS visibility | Draft/scenario rules use the same FAM grant. |
| Coast/Interior references | Revision counts, marks, FTAS defaults, location labels, nulls, ambiguous-context errors. |
| GAS search | All three families, status exclusions, client/organization paths, all marks. |
| Appraised summary | ADS ownership, ADS/ADSC differences, rate order and precision, linked ECAS. |
| Historic summary | Stored ASR/NASR rows, species inputs, nullable fields, Coast species/grade; no new rates derived. |
| Non-appraised summary | NASR components, add-ons, expired selections; no recalculation. |
| Licence/FTA | Chooser, permit contexts, multi-permit totals, FTA shown even with no worksheets. |
| Lookups | ECAS and GAS code lists, active flags, date limits; organization choices stay within FAM grants. |
| Audit history | Scope, labels, event order, hidden Import comments, text truncation; `DBMS_LOB.GETLENGTH` is executable. |
| Attachments | Non-ZIP records, Coast vs Interior rules, empty lists, cross-parent denial; no download endpoint. |

Use read-only methods. Don't call legacy code that might create, delete or recalculate rates. Include null, ambiguous and multi-row cases. Review query plans, pool size times replicas, and timeouts.

## 4. Authorization and browser

- [ ] Anonymous, expired, wrong-issuer/client/provider and malformed tokens are rejected. No-role users get no read access.
- [ ] Each district, region and forest-client grant sees the same records in search and detail. Out-of-scope and missing records both return 404.
- [ ] With mixed roles, capability and scope come from the same grant. Filters never widen scope. Report-only GAS access can't read worksheets. Real FAM region codes match the Oracle rollup.
- [ ] ECAS and GAS access stay separate, and direct API calls get the same denials as the UI.
- [ ] Walk ECAS search to reference to GAS summary, GAS search, the chooser and FTA. Check empty results, errors and retry, keyboard focus and mobile drawers.
- [ ] Sign-in, renewal, logout, re-login and Business BCeID logout work with the real providers.

## 5. TEST and runtime

- [ ] The merged PR was updated from `main` and rebuilt before merge. TEST runs that PR's images.
- [ ] DEV and TEST smoke tests pass on both image headers before the `test` tag moves.
- [ ] SCC, UID/GID, read-only filesystems, volumes, network policies, TLS, resources and probes work in the cluster.
- [ ] In an isolated TEST exercise, a database outage or full pool gives safe 503s while liveness/readiness stay up, and reads recover. Never cause an outage on a shared database.
- [ ] Rolling updates and SIGTERM shutdown finish within the termination budget.

Read-only status checks:

```sh
: "${taps_namespace:?Set the approved DEV or TEST namespace}"
: "${taps_resource:?Set the deployment prefix, such as nr-taps-test}"
oc -n "$taps_namespace" get deployment "$taps_resource-backend" "$taps_resource-frontend"
oc -n "$taps_namespace" get deployment "$taps_resource-backend" -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
oc -n "$taps_namespace" get deployment "$taps_resource-frontend" -o jsonpath='{.spec.template.spec.initContainers[0].image}{"\n"}{.spec.template.spec.containers[0].image}{"\n"}'
oc -n "$taps_namespace" rollout status "deployment/$taps_resource-backend" --timeout=120s
oc -n "$taps_namespace" rollout status "deployment/$taps_resource-frontend" --timeout=120s
```

Keep ECAS and GAS2 running until each unported feature has a migration and rollback plan.

## Saving forms

The [Coast date draft](coast-appraisal-date-draft.md) validates fields but doesn't save. A save path needs its own review of FAM and state rules, audit fields, revision conflicts, FTA checks and child-row updates. The legacy Save Dates path also updates cutting-authority child rows, so a plain date update isn't enough. This release writes nothing to the shared schema.
