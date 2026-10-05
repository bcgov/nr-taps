# Authentication and authorization

## SSO configuration

TAPS uses a public OpenID Connect browser client with authorization code and PKCE. Supported providers are **IDIR MFA and Business BCeID**, with provider hints `azureidir` and `bceidbusiness`. Basic BCeID is not supported. See [deployment configuration](deployment-configuration.md) for issuer, client and redirect settings.

Tokens and OIDC state live in browser session storage. Logout clears them and chains SiteMinder logoff to Keycloak end-session. The backend accepts bearer tokens only, not cookies.

## Token and grant validation

The API checks the token signature, issuer, lifetime, expiry, client (`azp`), type (`typ`) and identity provider. Roles come from CSS `client_roles`, or from the client's Keycloak `resource_access` entry when `client_roles` is missing. Role-to-capability mappings live in [TapsRole.java](../backend/src/main/java/ca/bc/gov/nrs/taps/security/TapsRole.java).

Signing keys come from the issuer's JWKS endpoint with 10-second connect and 15-second read timeouts, one retry and a cache that refreshes ahead of expiry, so a slow key fetch doesn't fail a request.

These grant nothing: malformed claims, unknown roles, wrong providers, FAM management roles and `FAM:` metadata roles. Role names, provider names and scope values are case sensitive.

## Scope format

| Scope         | FAM marker          | Example (synthetic)                              |
| ------------- | ------------------- | ------------------------------------------------ |
| District      | `HAS_DISTRICT_ROLE` | `TAPS_VIEWER_DISTRICT-DZZ`                       |
| Region        | `HAS_REGION_ROLE`   | `TAPS_REGION_APPRAISER_REGION-KOOTENAY_BOUNDARY` |
| Forest client | `HAS_FOREST_CLIENT` | `TAPS_LICENSEE_VIEWER_FOREST_CLIENT-99990001`    |

- District codes match `D[A-Z]{2}`. Forest-client numbers are eight digits, leading zeroes kept.
- A scoped grant needs exactly one matching suffix with a valid value. Missing or extra suffixes grant nothing.
- Region grants use FAM's `HAS_REGION_ROLE` convention and region codes, including ones with underscores like `KOOTENAY_BOUNDARY`. TAPS has no region model of its own.
- `FamRegion` maps FAM region codes to the Oracle organization rollup. That mapping still needs checking against real data.

ECAS draft and scenario visibility is a separate status rule, applied per grant by `EcasReadPredicate` for inbox and reference reads. Ministry viewers don't see drafts. Client-scoped roles don't see scenarios; for the client viewer this is provisional, to match the other industry roles. TAPS doesn't change any FAM role definitions or assignments.

## Enforcement

`GET /api/me` returns the user's identity, grants, capabilities, forest clients and `readApiEnabled`. A user with no TAPS role gets an empty access list. Health probes are public. The read routes below need the backend's `oracle` profile and the listed capability. All other paths are denied.

| Routes                                     | Capability             |
| ------------------------------------------ | ---------------------- |
| ECAS inbox and references                  | `ECAS_SUBMISSION_VIEW` |
| GAS worksheets, licence marks and FTA info | `GAS_APPRAISAL_VIEW`   |

A report-only capability doesn't allow GAS worksheet reads. The security filter and the controller both check the capability, then the reader applies the grant's scope in SQL. The capability and scope must come from the same grant, so a user can't pair one grant's capability with another's wider scope. Request filters only narrow results. Missing and unauthorized records both return 404.

ECAS and appraised GAS records use ADS client/admin-district ownership for now. Other GAS families and FTA use their own paths. Check these mappings and the region rollup against real data and real FAM roles before turning Oracle on in a shared environment. See [Oracle read foundation](oracle-read-foundation.md) for the SQL rules and [Oracle read runtime](oracle-read-runtime.md) for the full endpoint list.

Future writes must also check linked-resource scope and add state, transaction and concurrency controls.
