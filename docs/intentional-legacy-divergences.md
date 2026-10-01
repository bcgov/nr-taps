# Technical legacy divergences

This register records implemented framework and integration changes from ECAS/GAS2. Functional parity is verified per implemented workflow.

## Implemented technical changes

| ID                           | TAPS behavior                                                              | Compatibility boundary                                                                                   |
| ---------------------------- | -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `PUBLIC_CLIENT_OIDC`         | Public client uses code + PKCE; the API validates signed bearer tokens     | Replaces application-server authentication. Verify sign-in and logout against the configured SSO client. |
| `EXPLICIT_GRANT_SCOPES`      | Each accepted grant retains its capability/scope association               | Record ownership comes from authoritative database fields and relationships.                             |
| `EXACT_GRANT_NAMES`          | Exact role/provider names and required scope formats                       | Unknown or malformed grants confer no authority.                                                         |
| `DEFAULT_DENY_API`           | `/api/me` requires sign-in; health is public; other app routes are denied  | Enable each new endpoint with explicit authorization and tests.                                          |
| `BACKEND_CAPABILITY_SESSION` | Frontend gets its identity and capabilities from `/api/me`                 | Browser visibility does not grant API access.                                                            |
| `EXISTING_ORACLE_SCHEMA`     | JDBC access will use an application proxy account for existing Oracle data | Existing schema, tables and identifiers remain in place; no data migration is planned.                   |

## Functional coverage

The five current pages are shells. Forms, searches, attachments, reports, calculations, notifications and legacy integration endpoints remain unimplemented. Missing functionality is not an accepted retirement or a parity signoff.

Verify each ported workflow against its acceptance requirements. Similar screen names alone do not establish equivalent behavior.

## Oracle integration

Use parameterized SQL for verified reads. Preserve projection, filters, ordering and null/date semantics. Retain procedure calls until their transaction boundaries, side effects and compatibility have been verified.

No Oracle procedure replacement is implemented in this foundation. Record parity evidence for application queries and procedure calls as they are ported. Changes to shared database objects require separate ownership and approval.

## Recording a technical difference

Record a stable ID, observable before/after behavior, reason, implementation scope and validation evidence. Update this register as functionality is implemented and verified.

Related: [architecture](architecture.md), [authentication and authorization](access-and-identity.md), [deployment configuration](deployment-configuration.md).
