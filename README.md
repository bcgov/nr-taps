# TAPS

This repository is the initial TAPS application foundation. It has a Java 21 Spring Boot API and a React frontend. The frontend serves the app through Caddy and proxies `/api/*` to the backend. Browser sign in uses the modern FAM Keycloak client with authorization code and PKCE. The backend accepts signed user access tokens for the configured FAM client. Roles, business endpoints, database integration, and NEXCOL-style machine APIs are outside this first slice.

## Deployment flow

| Event | OpenShift target | Public route |
| --- | --- | --- |
| PR opened or updated | One preview in DEV, named `nr-taps-<PR>` | `https://nr-taps-<PR>.<OC_APPS_DOMAIN>` |
| PR merged to `main` | TEST, using the PR image | `https://nr-taps-test.<OC_APPS_DOMAIN>` |

The PR workflow builds both images under the PR number and head SHA, deploys the SHA images to DEV, and checks that the public shell responds and `/api/me` rejects anonymous requests. On merge, TEST deploys the PR-numbered images, runs the same smoke check, then tags those images `test`. Closing a PR cancels any in-progress preview deployment, then removes its DEV resources. The backend has no public Route; Caddy proxies the API through the frontend Route.

The OpenShift deployer (v4.2.2 or later) keeps rendered templates, which will include Secret values once the app has them, out of workflow logs; don't pin an older version or print rendered templates from other steps.

**PROD deployment is disabled.** There is no PROD job, Route, or image promotion. When PROD is planned, give it a dedicated hostname and certificate rather than an OpenShift generated hostname, and add a separate reviewed promotion path.

## GitHub and FAM setup

The following configuration must exist before the deployment workflows can succeed. Use separate FAM clients and OpenShift credentials for DEV and TEST. The browser client ID is the CSS installation JSON `resource` value; it is public configuration, so no browser client secret is needed.

| Scope | Name | Purpose |
| --- | --- | --- |
| Repository variable | `oc_server` | OpenShift API URL |
| Repository variable | `OC_APPS_DOMAIN` | Cluster apps domain, for example `apps.gold.devops.gov.bc.ca` |
| DEV repository secret | `oc_namespace`, `oc_token` | DEV namespace and deployment service token |
| DEV environment variable | `TAPS_OIDC_ISSUER_URI`, `TAPS_OIDC_CLIENT_ID` | DEV FAM issuer and browser client |
| TEST environment secret | `oc_namespace`, `oc_token` | TEST namespace and deployment service token |
| TEST environment variable | `TAPS_OIDC_ISSUER_URI`, `TAPS_OIDC_CLIENT_ID` | TEST FAM issuer and browser client |
| Optional environment variable | `TAPS_OIDC_SITEMINDER_LOGOUT_URL` | SiteMinder logoff URL; defaults to `https://logontest7.gov.bc.ca/clp-cgi/logoff.cgi` for DEV and TEST |

Create GitHub Environments named `dev` and `test`; restrict TEST to `main`. The caller passes the DEV repository secrets, and the called deployment jobs select TEST environment secrets for the TEST run. An environment secret replaces the caller's secret only when it exists, so the deployment check requires `oc_namespace` to end in `-dev` or `-test` to match the environment. A missing TEST secret therefore stops the run instead of deploying into DEV. Give the service tokens only the permissions needed to manage this app in their respective namespace. GHCR images must be pullable by those namespaces.

FAM must allow each active DEV preview redirect URI `https://nr-taps-<PR>.<OC_APPS_DOMAIN>/authCallback` and the TEST redirect URI `https://nr-taps-test.<OC_APPS_DOMAIN>/authCallback`. Allow the matching origins as post-logout redirects. IDIR and Business BCeID provider hints follow the Lexis FAM setup. Keycloak logout does not end the SiteMinder session behind Business BCeID, so sign out goes to SiteMinder `logoff.cgi` first and returns through Keycloak logout. PROD must use `https://logon7.gov.bc.ca/clp-cgi/logoff.cgi`. No TAPS roles are created or checked in this slice. Sign-in and logout still require live credentialed acceptance after the clients and redirect URIs are configured.

## Local development

Install Java 21, Maven, and Node 24. Set `TAPS_OIDC_ISSUER_URI` and `TAPS_OIDC_CLIENT_ID` in the backend environment. Copy `frontend/.env.example` to `frontend/.env.local` and set its matching Vite values. Then run:

```sh
cd backend && mvn spring-boot:run
cd frontend && npm ci && npm run dev
```

The frontend runs at `http://localhost:3000`, proxies `/api` to `http://localhost:8080`, and returns from FAM at `http://localhost:3000/authCallback`. Add that local redirect and origin to a development FAM client before attempting sign in. The backend exits at startup when its issuer or client ID is missing.

The only app endpoint today is `GET /api/me`, which returns the authenticated user's subject and name. Spring Boot Actuator exposes `/actuator/health/liveness` and `/actuator/health/readiness` for pod probes. The frontend serves `/config.js` from environment values at container startup, so the same image can run in DEV and TEST.

## Validation boundary

Local checks run Java tests, frontend lint and tests, a frontend build, and OpenShift template processing. The deploy smoke check verifies routing and anonymous API denial. It does not prove real FAM sign-in, IDIR or Business BCeID behavior, or deployment health until the namespace, clients, GitHub configuration, and images are available.
