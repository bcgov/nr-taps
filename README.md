# TAPS - Timber Appraisal and Pricing System

TAPS replaces two legacy apps, ECAS and GAS2. It keeps the existing Oracle schema and data and connects through an application proxy account. There is no data migration.

Current state:

- Spring Boot API (Java 21, Undertow) and a React frontend. Caddy serves the frontend and proxies `/api/*` to the backend.
- Sign-in through FAM / BC Gov SSO with IDIR MFA or Business BCeID (no Basic BCeID). Roles map to capabilities and to district, region or forest-client scopes.
- Read-only ECAS and GAS screens: ECAS inbox, references and GAS worksheet summaries.
- Oracle reads run only with the backend's `oracle` Spring profile, which stays off until the proxy account is provisioned. OpenShift namespaces and SSO clients are also pending.
- Not built yet: editing, calculations, workflow and other writes.

| Component | Technology                                                                  |
| --------- | --------------------------------------------------------------------------- |
| Frontend  | React 19, TypeScript, Vite, TanStack Router, Carbon / IBM Products, BC Sans |
| Backend   | Spring Boot 3.5.16, Java 21, Undertow, Spring Security, Actuator            |
| Database  | Existing Oracle schema through Hikari/JDBC; read-only, off by default       |
| Hosting   | Gold OpenShift, Caddy/Coraza, GitHub Actions, GHCR                          |

## Docs

| Doc                                                                    | What's in it                                             |
| ---------------------------------------------------------------------- | -------------------------------------------------------- |
| [Architecture](docs/architecture.md)                                   | Runtime, auth and delivery diagrams; built vs planned    |
| [Authentication and authorization](docs/access-and-identity.md)        | Token checks, grant formats, record scopes               |
| [SSO and deployment configuration](docs/deployment-configuration.md)   | SSO request, callbacks, GitHub settings, OpenShift names |
| [Oracle read runtime](docs/oracle-read-runtime.md)                     | Oracle settings, API routes, required grants             |
| [Oracle read foundation](docs/oracle-read-foundation.md)               | Legacy query rules and ownership mappings                |
| [Local connected read rehearsal](docs/local-read-rehearsal.md)         | Full local stack with a test issuer and Oracle           |
| [DEV/TEST activation acceptance](docs/activation-acceptance.md)        | Checklist for turning on Oracle reads                    |
| [Technical legacy divergences](docs/intentional-legacy-divergences.md) | Where TAPS differs from ECAS/GAS2                        |
| [Attachment inventory](docs/attachment-inventory.md)                   | Attachment metadata and visibility rules                 |
| [Coast appraisal date draft](docs/coast-appraisal-date-draft.md)       | Date validation and why saving is still off              |
| [Legacy read contracts](docs/legacy-read-contracts.md)                 | Read API request rules, formats and paging               |
| [UI foundation](docs/ui-foundation.md)                                 | Carbon shell, shared components and UI checks            |
| [Container rehearsal](docs/container-rehearsal.md)                     | Local check of the production images                     |

## Local development

Install Java 21, Maven and Node 24. Set `TAPS_OIDC_ISSUER_URI` and `TAPS_OIDC_CLIENT_ID` for the backend; it won't start without them. Copy [frontend/.env.example](frontend/.env.example) to `frontend/.env` and set the matching values. Then run each in its own terminal from the repository root:

```sh
cd backend && mvn spring-boot:run
```

```sh
cd frontend && npm ci && npm run dev
```

The frontend runs on `http://localhost:3000` and proxies `/api` to `http://localhost:8080`. Change these with `VITE_DEV_HOST`, `VITE_DEV_PORT` and `VITE_DEV_BACKEND_TARGET`. To sign in, add `http://localhost:3000/authCallback` and its origin to a development FAM client.

Or run both in Docker with `docker compose up`: the backend through Maven on 8080 and the Vite dev server on 3000. Set the two OIDC values in the shell or a root `.env` first. `docker compose --profile caddy up` also serves the production frontend image on `http://localhost:3005`. Oracle stays off unless `SPRING_PROFILES_ACTIVE=oracle` and the database settings are set; [backend/.env.example](backend/.env.example) lists them.

`GET /api/me` returns the user's identity, grants, capabilities, forest clients and `readApiEnabled`. A user with no TAPS role gets an empty access list. With Oracle off, business routes are denied; see [Oracle read runtime](docs/oracle-read-runtime.md) to turn it on. To run everything locally with a test issuer and Oracle, use the [local read rehearsal](docs/local-read-rehearsal.md).

## Tests

From `backend` with Java 21:

```sh
mvn -B -DskipITs verify     # no database needed; JaCoCo report in target/site/jacoco
mvn -B -Poracle-it verify   # needs Docker; disposable Oracle with synthetic data
```

The SQL files under [`backend/src/test/resources/oracle`](backend/src/test/resources/oracle/README.md) are synthetic test fixtures for the Oracle tests and local read rehearsal. **The application, deployment pipeline and supplied fixture loaders have no execution path that loads them into the live ECAS/GAS database.** They are excluded from the production application package and runtime image; fixture loaders connect only to the disposable Oracle containers they create, independently of live database environment variables. See the [execution boundaries](backend/src/test/resources/oracle/README.md#where-they-run), including the separate risk of manually executing copied SQL. No changes to the shared ECAS/GAS schema are required.

From `frontend`:

```sh
npm run typecheck
npm run lint
npm run format:check
npm run test:cov            # fails below 80% statements, 75% branches, 80% functions and lines
npm run build
```

`npm run test:security-config` checks the Caddy/Coraza config with synthetic requests. It needs a Coraza-enabled Caddy, such as the one `frontend/Dockerfile` builds; set `CADDY_BIN` if it isn't on the path.

The PR workflow runs the backend tests and frontend checks before the DEV deploy. The Analysis workflow runs on non-draft PRs, pushes to `main`, weekly and on demand: backend tests with JaCoCo, frontend checks with dependency and supply-chain scans, the rehearsal issuer tests, and an advisory Trivy scan that reports to GitHub Security. SonarCloud runs on pushes to `main` once the `sonar_token_backend` and `sonar_token_frontend` secrets exist. CI doesn't run the Oracle integration tests or `test:security-config`.

## Deployment

| Event                | Target                                                      | URL                                               |
| -------------------- | ----------------------------------------------------------- | ------------------------------------------------- |
| PR opened or updated | DEV preview `nr-taps-backend-<PR>`, `nr-taps-frontend-<PR>` | `https://nr-taps-<PR modulo 50>.<OC_APPS_DOMAIN>` |
| PR merged to `main`  | TEST, using the PR's images                                 | `https://nr-taps-test.<OC_APPS_DOMAIN>`           |
| PR closed            | DEV preview removed, plus any left by other closed PRs      |                                                   |
| Weekly (Saturday)    | DEV previews older than a week removed; ZAP scan of TEST    |                                                   |

- PR builds tag images with the PR number and head SHA. DEV runs the SHA images. TEST runs the PR-numbered images, which get the `test` tag once the smoke test passes. Update a PR from `main` and let it rebuild before merging.
- Every deploy forces a rollout, so pods restart even when an image tag is reused.
- The smoke test checks that the shell loads, anonymous `/api/me` gets 401, and both pods report the expected image tag. The tag check catches two PRs sharing a route slot. There are 50 slots to match the SSO redirect list.
- PR Validate posts the preview links on the PR. The Scheduled workflow also marks PRs and issues stale after 14 days and closes them 7 days later.
- Only the frontend has a public Route.
- Keep the OpenShift deployer action at v4.2.2 or later so rendered templates, which contain secrets, stay out of logs.

**PROD deployment is disabled.** There is no PROD job, Route or image promotion. PROD will need its own hostname and certificate and a separate reviewed promotion path.

See [SSO and deployment configuration](docs/deployment-configuration.md) for the SSO request, callbacks, GitHub variables and secrets, and OpenShift object names.
