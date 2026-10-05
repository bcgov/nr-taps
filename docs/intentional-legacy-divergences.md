# Technical legacy divergences

Where TAPS deliberately behaves differently from ECAS/GAS2. Each workflow still needs its own
parity check before the legacy version is retired.

## Framework

| ID | TAPS behaviour | Notes |
| --- | --- | --- |
| `PUBLIC_CLIENT_OIDC` | Public client with code + PKCE. The API validates signed bearer tokens. | Replaces app-server login. Check sign-in and logout against the SSO client. |
| `EXPLICIT_GRANT_SCOPES` | Each grant keeps its own capability and scope. | Ownership comes from database fields and relationships. |
| `EXACT_GRANT_NAMES` | Exact role/provider names and scope formats. | Unknown or malformed grants give no access. |
| `DEFAULT_DENY_API` | `/api/me` needs sign-in. Read routes also need Oracle enabled and a capability. Everything else is denied. | New endpoints need explicit authorization and tests. |
| `BACKEND_CAPABILITY_SESSION` | The frontend gets identity and capabilities from `/api/me`. | Hiding something in the browser doesn't block the API. |
| `EXISTING_ORACLE_SCHEMA` | Opt-in JDBC readers use the proxy account on the existing schema. | No schema changes or data migration. |

## What's built

ECAS search, Coast/Interior references, audit history, attachment metadata and lookups. GAS search
across all three families, the mark chooser, the FTA licence panel, stored summaries and lookups.
A Coast appraisal-date draft that doesn't save. All of it has only run against synthetic data.

Not built: My To Do assignments, reference editing, attachment upload/download, reports,
calculations, notices, notifications and writes.

## Oracle reads

Low-risk reads are rewritten as parameterized SQL that keeps the legacy columns, filters, ordering
and null/date behaviour. Writes and calculations stay as legacy stored procedures. See
[Oracle read foundation](oracle-read-foundation.md).

| ID | TAPS behaviour | Check against real data |
| --- | --- | --- |
| `PROVISIONAL_APPRAISED_OWNER` | Appraised reads use the ADS client and district, not the ADSC client legacy GAS search used. | Records where they differ. |
| `SCOPED_FTA_PERMIT_DISPLAY` | Scope is applied per permit before aggregation, so hidden permits are left out. | Marks across several districts or files. |
| `FTA_CONTEXT_AMBIGUITY` | More than one preferred client or context fails instead of taking an unordered first row. | How often, and how the UI should show it. |
| `LICENCE_MARK_ORDER` | The chooser keeps HA-only marks, sorted by mark. | Sort order with real data. |
| `ECAS_SELECT_PAGING` | One scoped SELECT returns 100-row server pages instead of temporary-table writes and in-memory paging. | Rows, counts, dates and query plans. |
| `ECAS_GRANT_VISIBILITY` | Scope and draft/scenario visibility come from the same grant. Client viewers get the industry scenario rules (provisional). | Results per role. |
| `ECAS_DATE_HELPER_MAPPING` | Native midnight comparisons and day truncation replace the SIL date-conversion helper. `SIL_GET_CLIENT_NAME` is still called. | Intraday, null and sentinel dates. |
| `ECAS_STATUS_DISPLAY_CLOCK` | All statuses stay selectable. The `active` hint uses Oracle day boundaries, not the legacy Java clock. | Database vs JVM clock at boundaries. |
| `SELECTED_ADDON_HISTORY` | Selected add-ons keep legacy labels and expired codes. | Stored selections and old labels. |
| `HISTORIC_STORED_SPECIES` | Historic species and Coast grades are returned as stored. | Order, duplicates and nulls. |
| `SCOPED_AUDIT_READS` | Audit reads re-check the parent and event, hide Import comments and cap text with truncation flags. | Event order, labels and actors by role. |
| `ATTACHMENT_METADATA_ONLY` | Non-ZIP document metadata with legacy Coast/Interior visibility. Basenames only, no file access. | Coast flags, Interior roles, document sets. |
| `COAST_DATE_DRAFT_ONLY` | Coast dates can be checked in memory but not saved. | See [Coast date draft](coast-appraisal-date-draft.md). |
| `ECAS_REFERENCE_AMBIGUITY` | Conflicting FTAS contexts or major centres fail instead of taking an unordered first row. | Parent/mark and lookup cardinality. |

Before removing any legacy path, run the [preflight pack](../scripts/oracle-preflight.sql) and the
[DEV/TEST acceptance checklist](activation-acceptance.md).

## Recording a technical difference

Add a stable ID, the before and after behaviour, the reason, the scope and how it was checked.

Related: [architecture](architecture.md), [authentication and authorization](access-and-identity.md), [deployment configuration](deployment-configuration.md).
