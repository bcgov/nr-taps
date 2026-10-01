# Intentional legacy divergences

TAPS should preserve ECAS/GAS business behavior unless a deliberate change is documented and approved by the appropriate owner. This register separates implemented technical choices from proposed business differences. It is not a blanket parity signoff or a record of feature retirements.

## Implemented technical choices

| ID                           | TAPS behavior                                                                                 | Legacy difference and boundary                                                                                                                                                              |
| ---------------------------- | --------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `PUBLIC_CLIENT_OIDC`         | Public browser client uses code + PKCE; the stateless API validates signed user access tokens | Replaces WebADE/application-server authentication and per-action proxy identity. Live SSO acceptance remains pending.                                                                       |
| `BUSINESS_BCEID_ONLY`        | IDIR and Business BCeID are supported; Basic BCeID is rejected                                | Confirmed project direction and published ECAS industry requirement. The archive's generic `BCEID` spelling is not evidence that Basic is required.                                         |
| `COMBINED_ROLE_CAPABILITIES` | One proposed TAPS role catalogue supplies ECAS/GAS capabilities                               | The legacy design already bundles GAS roles into ECAS profiles. Exact role/capability policy is still proposed.                                                                             |
| `GRANT_SCOPE_ASSOCIATION`    | Multiple grants retain their own capability/scope association                                 | Replaces selected-organization/role-flag interpretation with explicit signed grants. Only synthetic record helpers are implemented; actual data enforcement belongs to the future endpoint. |
| `EXACT_GRANT_NAMES`          | Exact role/provider names and required scope formats; invalid grants confer nothing           | No legacy aliases, case/padding normalization or bare scoped-code fallback grants authority.                                                                                                |
| `DEFAULT_DENY_API`           | `/api/me` needs valid sign-in; health probes are public; other app routes default to denied   | New endpoints are enabled with explicit route, record and action policies rather than generic authenticated access.                                                                         |
| `BACKEND_CAPABILITY_SESSION` | Frontend obtains accepted grants/capabilities from `/api/me`                                  | Browser route/button visibility is presentation only; FAM role-management UI is not rebuilt in TAPS.                                                                                        |

## Proposed policy differences requiring confirmation

| ID                                  | Initial proposal                                                                                     | Confirmation required                                                                                                                     |
| ----------------------------------- | ---------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `PROVINCIAL_ADMIN_HQ_SCOPE`         | Admin/HQ roles have unscoped provincial coverage                                                     | Legacy ECAS record checks require organization coverage even for these roles. Approve coverage or introduce the necessary scope.          |
| `DISTRICT_SCOPED_VIEWER`            | Ministry viewer is district scoped; approved regions can be represented through district assignments | Confirm intended viewer coverage and report behavior.                                                                                     |
| `CONSERVATIVE_HQ_CAPABILITIES`      | HQ has ECAS read and GAS client/HQ-report capabilities, not worksheet or risk-assessment editing     | Legacy action, Oracle and JSP layers disagree about HQ rights.                                                                            |
| `CLIENT_SCOPED_BCTS_RATE_OVERRIDE`  | Only the BCTS submitter receives the proposed override capability, scoped by forest client           | GAS design gives both BCTS profiles an unscoped override. Confirm eligible persona/files/business area before implementing the operation. |
| `CLERK_BULK_RUNS_DEFERRED`          | Region clerk has no coarse bulk-run capability                                                       | Legacy clerical execution differs by run/profile; split and confirm permissions before enabling runs.                                     |
| `REGIONAL_STATUS_OVERRIDE_DEFERRED` | No replacement for additive `ECAS_STATUS_UPDATE` is provisioned                                      | Verify deployed use and permitted transitions before designing the replacement.                                                           |

Business BCeID support for BCTS consultants is preservation of a published access path, not permission to obtain ministry roles. Client and professional eligibility remain required separately.

## Porting gaps are not intentional retirements

The five current pages are shells. Searches, record forms, profile changes, attachments, reports, calculations, bulk processes, notifications and legacy integration endpoints are not implemented yet. Their absence does not mean they have been retired or accepted as a functional difference.

Coast/interior variants, ECAS submission states, GAS worksheet states and RPF/RFT requirements remain migration requirements until reviewed. Do not combine similarly named screens or drop controls solely because the application has a shared session and role catalogue.

## Stored-procedure replacement policy

Prefer direct parameterized SQL for verified, low-risk reads. Check projection, joins, effective/expiry filters, ordering, null/date semantics, scope and caller behavior against the approved schema/fixtures. Keep calculation, write, workflow and bulk-run procedures as calls until transaction boundaries, side effects, audit and concurrency are understood.

No Oracle procedure replacement is implemented in this foundation. Add a procedure inventory with parity evidence when each read is ported; procedure removal from the shared database is a separate ownership decision.

## Recording a future difference

Add a stable ID, the observable before/after behavior, reason, implementation scope and approval/validation status. Distinguish technical implementation evidence from business approval and deployed acceptance. Update this register when the decision changes; do not treat a proposed row as permission to enable a transaction.

Related: [architecture](architecture.md), [access and identity](access-and-identity.md), [deployment configuration](deployment-configuration.md).
