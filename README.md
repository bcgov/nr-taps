# TAPS - Timber Appraisal and Pricing System

TAPS combines ECAS and GAS2 using the existing Oracle schema and data through an application proxy account. There is no data migration or shared-schema change.

## Current status

- Implemented: FAM/BC Gov SSO sign-in, scoped ECAS inbox/references/audit/attachment metadata, GAS search across all three worksheet families, licence-to-mark chooser, FTA information and stored summaries.
- The DEV proxy database account is available. Connection setup and verification of grants, real-data behavior and deployed access are still pending. Oracle reads remain off by default.
- OpenShift namespaces and SSO client setup remain pending. PROD deployment is disabled.
- Not implemented: My To Do assignments, editing/saving, calculations, workflow, reports, file upload/download, notices, notifications, service-client APIs, file scanning, scheduled batch work and the inactivity warning. The Coast date form validates a local draft only.

Current validation uses source, synthetic data and disposable local containers. It does not establish production readiness or complete ECAS/GAS2 replacement.

| Component | Technology |
| --- | --- |
| Frontend | React 19, TypeScript, Vite, TanStack Router, Carbon / IBM Products, BC Sans |
| Backend | Spring Boot 3.5.16, Java 21, Undertow, Spring Security, Actuator |
| Database | Existing Oracle schema through Hikari/JDBC; reads only |
| Hosting | Gold OpenShift, Caddy/Coraza, GitHub Actions, GHCR |

## Guides

| Guide | Use it for |
| --- | --- |
| [Architecture and access](docs/architecture.md) | Runtime layout, sign-in, token validation and FAM grant scopes |
| [Local development and testing](docs/development.md) | Run commands, test suites, container checks and authenticated local rehearsal |
| [Deployment and DEV/TEST acceptance](docs/deployment-configuration.md) | SSO/GitHub/OpenShift settings, Oracle connection/grants, delivery and activation checks |
| [Read API and Oracle behavior](docs/oracle-reads.md) | Endpoints, contracts, legacy query mappings, attachment visibility and provisional ownership |
| [UI development](docs/ui-foundation.md) | Synthetic preview, Carbon components, focus behavior and the Coast date draft |

The [Oracle SQL fixture README](backend/src/test/resources/oracle/README.md) is the reference for the seven synthetic SQL files. It explains why the application and supplied loaders have no execution path into the live database, and the boundary around manually copied SQL.
