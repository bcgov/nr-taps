# Authentication and authorization

## SSO configuration

TAPS uses a public OpenID Connect browser client with authorization code and PKCE. Supported providers are **IDIR MFA and Business BCeID**; Basic BCeID is unsupported. Provider hints are `azureidir` and `bceidbusiness`. See [deployment configuration](deployment-configuration.md) for issuer, client and redirect settings.

Tokens and OIDC state use browser session storage. Logout clears local credentials and chains SiteMinder logoff to Keycloak end-session. The backend accepts bearer tokens rather than browser cookies.

## Token and grant validation

The API validates the token signature, issuer, lifetime, expiry, client (`azp`), type (`typ`) and identity provider. Roles come from CSS `client_roles`, or the configured client's Keycloak `resource_access` entry when `client_roles` is absent. Malformed claims grant no access.

Role-to-capability mappings are defined in [TapsRole.java](../backend/src/main/java/ca/bc/gov/nrs/taps/security/TapsRole.java). These mappings are part of the current access scaffold. New application endpoints require validated authorization rules and acceptance tests before they are enabled.

FAM management roles and `FAM:` metadata roles confer no application permissions. Role names, provider names and scope values are case sensitive.

## Scope format

| Scope         | FAM marker          | Synthetic example                                |
| ------------- | ------------------- | ------------------------------------------------ |
| District      | `HAS_DISTRICT_ROLE` | `TAPS_VIEWER_DISTRICT-DZZ`                       |
| Region        | `HAS_REGION_ROLE`   | `TAPS_REGION_APPRAISER_REGION-KOOTENAY_BOUNDARY` |
| Forest client | `HAS_FOREST_CLIENT` | `TAPS_LICENSEE_VIEWER_FOREST_CLIENT-99990001`    |

Examples illustrate syntax, not deployed assignments. District codes use `D[A-Z]{2}`; forest-client numbers use eight digits, preserving leading zeroes. Scoped grants require exactly one matching suffix with a valid value. Unknown roles, wrong providers, missing scopes and extra scope dimensions grant no access.

## Enforcement

`GET /api/me` returns the authenticated identity, accepted grants, capabilities and forest clients. A valid sign-in without application roles receives an empty access list. Health probes are public; other application paths default to denied.

The frontend uses capabilities for navigation only. API endpoints must authorize independently, loading record ownership from the database. The same grant must supply both the capability and the record scope; multiple grants cannot combine a capability from one with a wider scope from another.

Record-scope helpers currently have synthetic test coverage and no database-backed consumers. Verify list, report, direct-record and linked-resource access when each endpoint is implemented. State-changing operations also need transaction and concurrency controls.
