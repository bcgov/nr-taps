# SSO and deployment configuration

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

## Oracle connection

Oracle reads are off unless `TAPS_ORACLE_ENABLED` is `true`. The deploy workflow rejects any other value. When it's `true`, the workflow requires all five database secrets and deploys the backend with `SPRING_PROFILES_ACTIVE=oracle`. With that profile, the backend won't start if credentials are missing or the database is unreachable.

How it's wired:

- The backend template creates Secret `nr-taps-backend-secret-<zone>` with keys `DATABASE_USER`, `DATABASE_PASSWORD` and `KEYSTORE_SECRET`. PR cleanup deletes it.
- The `oracle` profile builds a TCPS descriptor from `DATABASE_HOST`, port 1543 and `DATABASE_SERVICE_NAME`.
- An init container (`ghcr.io/bcgov/nr-forest-client/common:prod`) reads the database certificate and writes a JKS truststore to `/cert/jssecacerts`, protected by `keystore_secret`. It only runs when the `oracle` profile is on. The truststore is rebuilt on every pod start, so a rotated certificate is picked up. The backend mounts it read-only.

Readiness uses Spring's `readinessState`, not the database, so a shared Oracle outage doesn't pull every replica. `/actuator/health` reports database status separately. See [Oracle read runtime](oracle-read-runtime.md) for grants, timeouts and pool settings.

## Rollout order

1. Run the [production-container rehearsal](container-rehearsal.md) locally.
2. Get the SSO clients and callbacks registered.
3. Set the GitHub variables, secrets and TEST branch restriction.
4. Deploy DEV and check the smoke test passes.
5. Test real login, renewal, logout, re-login and the no-role view for both providers.
6. Deploy TEST. PROD stays disabled.

To turn on Oracle reads, follow the [activation checklist](activation-acceptance.md).
