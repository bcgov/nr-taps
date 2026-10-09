# TAPS - Timber Appraisal and Pricing System

TAPS combines ECAS and GAS2 using the existing Oracle schema and data through an application proxy account. There is no data migration or shared-schema change.

## Current status

- Implemented: FAM/BC Gov SSO sign-in, scoped ECAS inbox (All Submissions and My To Do)/references/audit/attachment metadata, GAS search across all three worksheet families, licence-to-mark chooser, FTA information and stored summaries.
- Oracle reads remain off by default and require the deployment configuration and acceptance checks before activation. PROD deployment is disabled.
- Not implemented: assignment changes, editing/saving, pricing calculations, workflow, reports, file upload/download, notices, notifications, service-client APIs, file scanning, scheduled batch work and the inactivity warning. The Coast date form validates a local draft only.

Current validation uses source, synthetic data and disposable local containers. It does not establish production readiness or complete ECAS/GAS2 replacement.

| Component | Technology |
| --- | --- |
| Frontend | React 19, TypeScript, Vite, TanStack Router, Carbon / IBM Products, BC Sans |
| Backend | Spring Boot 3.5.16, Java 21, Undertow, Spring Security, Actuator |
| Database | Existing Oracle schema through Hikari/JDBC; reads only |
| Hosting | Gold OpenShift, Caddy/Coraza, GitHub Actions, GHCR |

## Environments

| Environment | Runtime | Database and identity |
| --- | --- | --- |
| LOCAL | Developer machine or local Docker | Optional local synthetic UI preview, or disposable synthetic Oracle data and a local test issuer. |
| DEV | OpenShift DEV | Its own DEV Oracle database and configured SSO. |
| TEST | OpenShift TEST | TEST database and configured SSO. |
| PROD | OpenShift PROD | PROD database and configured SSO; PROD deployment automation is currently disabled. |

Synthetic database fixtures, the test issuer and the local synthetic UI preview are local validation tools. They
are not deployed to DEV, TEST or PROD and never seed those databases. All OpenShift environments
use the release application images with environment-specific configuration; an unavailable database
does not enable synthetic data as a fallback. CI unit tests use isolated fixtures without creating
a synthetic OpenShift environment.

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

Read screens remain unavailable until Oracle is enabled; see [backend configuration](backend/README.md#configuration).
For UI checks with sample data and no backend, use the [local synthetic UI preview](frontend/README.md#local-synthetic-ui-preview).
For local checks with a test issuer and disposable Oracle, use the [local synthetic read rehearsal](scripts/README.md#local-synthetic-read-rehearsal).

## Component docs

| Document | Purpose |
| --- | --- |
| [Architecture](docs/architecture.md) | Runtime boundaries, sign-in and FAM authorization |
| [Intentional legacy divergences](docs/intentional-legacy-divergences.md) | Stable IDs for observable ECAS/GAS differences, their reasons and validation status |
| [Backend](backend/README.md) | Spring profiles, environment variables, Oracle grants, API reference and tests |
| [Frontend](frontend/README.md) | Vite configuration, testing, Carbon components and the Coast date draft |
| [Local validation scripts](scripts/README.md) | Local image checks and authenticated read rehearsal |
| [Deployment and DEV/TEST acceptance](docs/deployment-configuration.md) | SSO/GitHub/OpenShift setup, delivery and activation checks |
| [Read API and Oracle behavior](docs/oracle-reads.md) | Implemented request/response contracts, query mappings and document visibility |

The [Oracle SQL fixture README](backend/src/test/resources/oracle/README.md) stays beside the seven synthetic SQL files and explains their isolation from live databases.
