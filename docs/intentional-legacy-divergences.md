# Intentional legacy divergences

TAPS normally preserves ECAS/GAS2 behavior. This register keeps deliberate changes visible during
parity review, with stable IDs, reasons and links to the owning contracts. Implementation detail,
preserved behavior and incomplete replacement coverage are separated below.

These entries describe implemented technical choices, not completed business acceptance. Provisional
ownership and visibility mappings still need real-data and role validation through the
[DEV/TEST acceptance checks](deployment-configuration.md#activation-acceptance). Infrastructure
provisioning alone does not establish that validation.

## Authentication and authorization

| ID | TAPS behavior | Reason and validation boundary |
| --- | --- | --- |
| `PUBLIC_CLIENT_OIDC` | Public browser client with authorization code + PKCE and a signed bearer-token API replaces app-server login. | Modern sign-in integration; check real IDIR MFA and Business BCeID login, renewal and logout. See [authentication](architecture.md#authentication). |
| `EXPLICIT_GRANT_SCOPES` | Each FAM grant retains its own capability and record scope at search, detail and child-resource boundaries. | Least privilege; mixed grants must not combine a narrow capability with a wider scope from another grant. See [authorization](architecture.md#authorization). |
| `EXACT_GRANT_NAMES` | Role/provider names and scope formats match exactly; unknown or malformed grants give no access. | Reject invalid FAM claims rather than broadening access. Real token assignments remain to be checked. |
| `DEFAULT_DENY_API` | Health probes are public; `/api/me` requires sign-in and read routes also require Oracle enabled and the matching capability. Other paths are denied. | New API boundaries require explicit authorization. See [enforcement](architecture.md#enforcement). |
| `BACKEND_CAPABILITY_SESSION` | The frontend obtains identity and capabilities from `/api/me`; the backend remains the authorization boundary. | Replaces app-server page/session integration. Hiding UI controls does not authorize API access. |

## Read behavior

| ID | TAPS behavior | Reason and validation boundary |
| --- | --- | --- |
| `PROVISIONAL_APPRAISED_OWNER` | Appraised GAS reads use the current ADS client and administrative district; ADSC supplies the method. Legacy GAS search used the ADSC client and some mark-based district paths. | Aligns with ECAS ownership, provisionally. ADS and ADSC can legitimately differ; this remains an assumption to validate, not an approved ownership-policy change. See [GAS summaries](oracle-reads.md#gas-summaries). |
| `SCOPED_FTA_PERMIT_DISPLAY` | Scope is applied to each permit before aggregation, omitting permits outside the user's grant. | Avoids exposing hidden permit context. Check marks spanning districts/files; see [FTA information](oracle-reads.md#licence-marks-and-fta-information). |
| `FTA_CONTEXT_AMBIGUITY` | Multiple preferred clients or permitted contexts fail rather than selecting an unordered first row. | Avoids presenting an arbitrary owner/context. Confirm real cardinality and error handling. |
| `LICENCE_MARK_ORDER` | The HA-only licence-to-mark chooser orders results by timber mark. | Deterministic ordering; verify against legacy user expectations. Road-only marks remain outside this chooser. |
| `ECAS_GRANT_VISIBILITY` | A single grant supplies both record scope and draft/scenario visibility. Client viewers provisionally follow the other industry roles' scenario exclusion. | Prevents combining unrelated grants. The client-viewer mapping still needs role acceptance; see [scope format](architecture.md#scope-format). |
| `ECAS_STATUS_DISPLAY_CLOCK` | Status choices retain inactive codes; the `active` display hint uses Oracle day boundaries instead of the legacy JVM clock. | Keeps display evaluation with the database query. Compare day boundaries and null/sentinel dates; see [code lists](oracle-reads.md#code-lists). |
| `SCOPED_AUDIT_READS` | Audit reads re-check the submission and event and cap returned text with truncation flags. Import comments remain hidden. | Bounds response size while retaining parent scope. Verify event order, actors, labels and truncation; see [audit history](oracle-reads.md#audit-history). |
| `ECAS_REFERENCE_AMBIGUITY` | Conflicting FTAS contexts or Coast major centres fail rather than selecting an unordered first row. | Avoids arbitrary reference defaults. Check parent/mark relationships and lookup cardinality; see [ECAS references](oracle-reads.md#ecas-references). |

## Read-path replacements

These are implementation changes intended to preserve legacy results, not permission to remove
shared procedures or change the schema. TAPS currently performs no Oracle writes or calculations.

| ID or path | Legacy path | TAPS implementation |
| --- | --- | --- |
| GAS code lists | `GAS2_COMMON.FIND_ALL_APP_METHOD_CODES`, `FIND_ALL_APP_STATUS_CODES`, `FIND_ALL_RTE_ADJ_TYP_CODES` | Direct reads in [OracleCodeLists](../backend/src/main/java/ca/bc/gov/nrs/taps/read/oracle/OracleCodeLists.java), preserving projections, validity, ordering and error behavior. |
| ECAS status list | `PKG_ECAS_CODE_LISTS.GET_APPRAISAL_STATUS` | Direct status reads; the clock difference is recorded above. |
| `ECAS_SELECT_PAGING` | ECAS05 temporary-table DELETE/INSERT and in-memory paging | One scoped SELECT with 100-row server pages. Compare rows, counts, dates and query plans; see [ECAS search](oracle-reads.md#ecas-search). |
| `ECAS_DATE_HELPER_MAPPING` | Legacy SIL date-conversion helper | Native midnight comparisons and day truncation, provisionally equivalent. `SIL_GET_CLIENT_NAME` is still called. Verify intraday, null and sentinel dates. |

## Preserved behavior

| ID | Compatibility boundary |
| --- | --- |
| `EXISTING_ORACLE_SCHEMA` | Existing tables, data and shared procedures stay in place; no schema migration. Disposable SQL fixtures are [test-only](../backend/src/test/resources/oracle/README.md). |
| `SELECTED_ADDON_HISTORY` | Stored add-ons retain expired selections and legacy labels; see [GAS summaries](oracle-reads.md#gas-summaries). |
| `HISTORIC_STORED_SPECIES` | Historic species and Coast grades are returned as stored, without deriving new rates. |

## Incomplete replacement coverage

These are current limitations, not approved retirements of legacy functionality. The
[current-status list](../README.md#current-status) tracks the broader unimplemented workflows.

| ID | Current boundary |
| --- | --- |
| `ATTACHMENT_METADATA_ONLY` | Non-ZIP document metadata with Coast/Interior visibility and basenames only. No opening, downloading, uploading or deleting files; see [attachments](oracle-reads.md#attachments). |
| `COAST_DATE_DRAFT_ONLY` | Coast dates can be checked in memory but not saved. The legacy Save Dates side effects must be retained before a save endpoint exists; see [the draft contract](../frontend/README.md#coast-appraisal-date-draft). |

## Recording a change

Keep IDs stable. For a new observable difference, record the legacy and TAPS behavior, reason,
affected scope, implementation/test links and actual validation status. Mark the implementing code
with a searchable `INTENTIONAL_LEGACY_DIVERGENCE(<ID>)` comment.
Do not label a refactor, unported feature or provisional mapping as business-approved parity.
