# Read API and Oracle behavior

The ECAS/GAS read API uses the existing schema. Low-risk legacy reads are rewritten as parameterized SELECTs; writes and calculations remain in legacy procedures that TAPS does not call. The implementation has been exercised with mocked JDBC and disposable Oracle fixtures. Provisional mappings still need comparison with the shared database.

Use [architecture and access](architecture.md) for token/grant rules, the [backend README](../backend/README.md#configuration) for connection settings and privileges, and [activation acceptance](deployment-configuration.md#activation-acceptance) for real-data validation. This guide owns the API contracts and SQL mappings; [intentional legacy divergences](intentional-legacy-divergences.md) separately records deliberate differences and provisional choices for parity review.

## HTTP routes

Every route needs a TAPS access token. The capability is checked first, then the reader applies
record scope from the same user's grants. Licence, client, district or region in a request only
filters.

| Route | Capability | Notes |
| --- | --- | --- |
| `POST /api/ecas/inbox?page=0` | `ECAS_SUBMISSION_VIEW` | `EcasInbox.Search` body with `mode: "ALL_SUBMISSIONS"`. 100 per page. |
| `GET /api/ecas/lookups` | `ECAS_SUBMISSION_VIEW` | Methods, codes with active hints, scoped organizations. |
| `GET /api/gas/lookups` | `GAS_APPRAISAL_VIEW` | Effective method, status and rate-adjustment lists. |
| `GET /api/ecas/references/{method}/{ecasId}` | `ECAS_SUBMISSION_VIEW` | `method` is `C` or `I`. |
| `GET /api/ecas/audit/{ecasId}?page=0` | `ECAS_SUBMISSION_VIEW` | Audit history, 100 per page. |
| `GET /api/ecas/audit/{ecasId}/events/{eventId}?page=0` | `ECAS_SUBMISSION_VIEW` | Field changes and comments for one event. |
| `GET /api/ecas/{ecasId}/attachments?page=0` | `ECAS_SUBMISSION_VIEW` | Document metadata, 50 per page. See [attachment visibility](#attachments). |
| `GET /api/gas/worksheets?licence=&timberMark=&page=0` | `GAS_APPRAISAL_VIEW` | All three families, 10 per page. |
| `GET /api/gas/worksheets/{type}/{worksheetId}` | `GAS_APPRAISAL_VIEW` | `APPRAISED`, `HISTORIC` or `NON_APPRAISED`. |
| `GET /api/gas/appraised/by-ecas/{ecasId}` | `GAS_APPRAISAL_VIEW` | Appraised summary for an ECAS ID. |
| `GET /api/gas/licences/{licence}/marks` | `GAS_APPRAISAL_VIEW` | HA marks. Empty is valid. |
| `GET /api/gas/licence-information?licence=&timberMark=` | `GAS_APPRAISAL_VIEW` | FTA licence panel. Mark required. |

Pages are zero-based. The rules below define identifiers, values and row identity.

Errors are `application/problem+json` with `title`, `detail`, `status` and a `code` that clients
match on: `INVALID_REQUEST` (400), `AUTHENTICATION_REQUIRED` (401), `ACCESS_DENIED` (403),
`NOT_FOUND` (404) and `READ_UNAVAILABLE` (503). Missing and inaccessible records both return 404.
No SQL, grants or stack traces are exposed. Other business paths are denied.

## Contract types and values

Java records below and matching types in `frontend/src/contracts` define the field lists.

| Java type | Purpose |
| --- | --- |
| [EcasInbox](../backend/src/main/java/ca/bc/gov/nrs/taps/read/EcasInbox.java) `.Search` / `.Item` / `.Page` | Inbox filters, result row, 100-row page |
| [EcasReference](../backend/src/main/java/ca/bc/gov/nrs/taps/read/EcasReference.java) `.Header` / `.Coast` / `.Interior` | Reference views |
| [GasAppraisal](../backend/src/main/java/ca/bc/gov/nrs/taps/read/GasAppraisal.java) `.Search` / `.Key` / `.Item` / `.Page` | Worksheet search, 10-row page |
| `GasAppraisal.LicenceMarks`, `.FtaLicenceInformation`, `.SearchResult` | Mark chooser and FTA licence panel |
| `GasAppraisal.AppraisedSummary`, `.StoredRate` | Appraised summary |
| `GasAppraisal.HistoricSummary`, `.HistoricSpecies`, `.HistoricCoastSpeciesGrade` | Historic summary |
| `GasAppraisal.NonAppraisedSummary`, `.StoredNonAppraisedRate`, `.SelectedRateAddon` | Non-appraised summary |
| [EcasAudit](../backend/src/main/java/ca/bc/gov/nrs/taps/read/EcasAudit.java) `.HistoryPage` / `.DetailPage` | Audit history and event details |
| [EcasAttachments](../backend/src/main/java/ca/bc/gov/nrs/taps/read/EcasAttachments.java) `.Page` | Attachment metadata |
| [CodeOption](../backend/src/main/java/ca/bc/gov/nrs/taps/read/CodeOption.java), `EffectiveCode` | Lookup values |

- IDs are JSON strings. ECAS, worksheet and rate IDs are positive integers of up to 12 digits, and
  leading zeroes are dropped when parsing.
- Forest client numbers are 8-character strings and keep their leading zeroes.
- Calendar dates are ISO `yyyy-MM-dd`, or `null`. Timestamp fields and `EffectiveCode` keep
  local date-times without timezone conversion.
- Display values are nullable unless noted. Lists keep their order and are immutable.
- Stored amounts are exact two-decimal strings such as `"12.30"`: `NUMBER(6,2)` for `StoredRate`
  and ASR, `NUMBER(5,2)` for NASR components. Overflow or rounding is rejected. Reference
  quantities are `BigDecimal` JSON numbers.
- No totals, breakdowns, policy costs or action flags are calculated.

## Record scope

[ReadScopePredicate](../backend/src/main/java/ca/bc/gov/nrs/taps/read/oracle/ReadScopePredicate.java)
turns the user's grants for one capability into a single OR expression plus bind values. No
qualifying grant gives `(1 = 0)` and a provincial grant gives `(1 = 1)`. A scoped grant missing its
suffix is rejected rather than treated as provincial.

Each reader supplies a `record_scope` projection with `ADMIN_DISTRICT_CODE`, `ROLLUP_REGION_CODE`
and `CLIENT_NUMBER`, loaded from the record and its parents, never from request values. Null
ownership never matches a scoped grant. Direct reads authorize the loaded parent and children, and
ECAS visibility doesn't grant GAS access.

## Code lists

[OracleCodeLists](../backend/src/main/java/ca/bc/gov/nrs/taps/read/oracle/OracleCodeLists.java):

| Method | Legacy procedure | Table |
| --- | --- | --- |
| `appraisalMethods()` | `GAS2_COMMON.FIND_ALL_APP_METHOD_CODES` | `APPRAISAL_METHOD_CODE` |
| `appraisalStatuses()` | `GAS2_COMMON.FIND_ALL_APP_STATUS_CODES` | `APPRAISAL_STATUS_CODE` |
| `rateAdjustmentTypes()` | `GAS2_COMMON.FIND_ALL_RTE_ADJ_TYP_CODES` | `RATE_ADJUSTMENT_TYPE_CODE` |
| `ecasAppraisalStatuses()` | `PKG_ECAS_CODE_LISTS.GET_APPRAISAL_STATUS` | `APPRAISAL_STATUS_CODE` |

GAS lists select `CODE`, `DESCRIPTION`, `EFFECTIVE_DATE`, `EXPIRY_DATE` and `UPDATE_TIMESTAMP`
with the legacy `SYSDATE BETWEEN C.EFFECTIVE_DATE AND C.EXPIRY_DATE ORDER BY CODE`. Java doesn't
filter, sort, dedupe or cache, and errors propagate with no fallback list.

ECAS lookups return fixed Coast/Interior methods and every status, category, reappraisal reason and
file type, including inactive codes. The `active` flag is a display hint using Oracle day boundaries
instead of the legacy JVM clock:
`SYSDATE > TRUNC(EFFECTIVE_DATE) AND SYSDATE < TRUNC(EXPIRY_DATE)`. Organization choices are
unexpired units from the user's ministry FAM grants: primary regions and districts for a provincial
grant, subordinate units for a regional grant, none from client grants. They are filters only.

[EffectiveCode](../backend/src/main/java/ca/bc/gov/nrs/taps/read/EffectiveCode.java) maps `DATE`
with `getTimestamp().toLocalDateTime()`, keeping the time of day with no timezone.

## ECAS search

`EcasInbox.Search` accepts the fields below. `MY_TO_DO` listing fails before SQL runs because the
assignment mapping is not implemented.

| Field | Rule |
| --- | --- |
| `mode` | `MY_TO_DO` (default, listing not built) or `ALL_SUBMISSIONS` |
| `appraisalMethod` | `C`, `I` or `null` for both |
| `licence`, `cuttingPermit` | Letters/digits, uppercased, max 10 / 3 |
| `timberMark` | Uppercased, max 6 |
| `clientNumber`, `clientLocationCode` | Padded to 8 / 2 characters |
| `managementUnitType`, `managementUnitId` | Letters/digits, uppercased, max 1 / 4 |
| `workedOnByUserId` | Max 30 |
| `bctsFunded`, `certified` | `null` means no filter |
| `orgUnitNumbers`, `statusCodes` | Empty means no filter |
| `sortBy`, `sortDirection` | Enums, default `ECAS_ID` `DESC` |

- Text is trimmed and blanks become `null`.
- Cutting permit needs a licence, management-unit ID needs a type and client location needs a
  client number. Client acronyms need a separate lookup.
- Date bounds need a matching date type or status. `EE` can't be combined with status-date bounds.
- [DateRange](../backend/src/main/java/ca/bc/gov/nrs/taps/domain/DateRange.java) allows open or
  equal bounds and rejects reversed ranges, impossible dates, date-times, numbers, arrays and years
  outside `0001`-`9999`.
- Date types: `EFFCTV` (effective), `EXPRY` (expiry), `NTRY` (entry), `LTMD` (last modified),
  `STTS` (status change), `FCED` (FTA cutting-permit expiry).
- Sort fields: `ECAS_ID`, `TIMBER_MARK`, `LICENCE`, `CLIENT_NAME`, `STATUS`, `STATUS_CHANGE_DATE`,
  `APPRAISAL_TYPE`, `EFFECTIVE_DATE`, `EXPIRY_DATE`, `SUBMITTED_DATE`, `DISTRICT_RECEIVED_DATE`,
  `SENT_TO_REGION_DATE`, `UPDATE_DATE`.
- Constructors check format, not whether a code exists.

Several `Item` rows can share an ECAS ID when a submission has several marks or permits. Don't
group by ECAS ID, or by ECAS ID and mark.

`OracleEcasInbox.search` supports `ALL_SUBMISSIONS` with 100-row pages. One scoped SELECT replaces
the legacy ECAS05 temporary-table DELETE/INSERT.

- Sorting is by day with fixed tie-breakers.
- Every matching mark/permit row is returned. Without a mark filter, primary marks are listed; an
  explicit mark can match a non-primary one.
- Organization selections only narrow results.
- A direct ECAS ID skips other criteria but keeps authorization, status visibility, organizations,
  client/location and certification.
- Certified `false` includes null; BCTS `false` requires `N`. Worked-on requires an audit event.
  FTA cutting-permit expiry applies only when both bounds are set.
- Client names come from `SIL_GET_CLIENT_NAME`. Native midnight bounds and day truncation stand in
  for the legacy SIL date-conversion helper (provisional).

## ECAS references

`Header` requires only ECAS ID and method. `Coast` adds the primary mark, all submitted marks (each
with its own revision count), cruise volume, areas and major centre. `Interior` adds its single
mark, mark revision count, point of appraisal, selling price zone, comparative cruise and salvage.
These are read views, not the full editing forms.

`OracleEcasReference.coast` and `.interior` use ADS ownership and `EcasReadPredicate`, which ties
draft/scenario visibility to each FAM grant.

- The requested method must match the stored one.
- Coast needs one primary mark and returns all marks and revisions. Interior needs its single mark.
- Includes FTAS defaults, cutting-permit management overrides, TSA/TSB fallback, client labels,
  the Coast species-volume sum and the Interior selling-zone default for dates before 2018-11-01.
- Authorization stays ADS-based even when FTAS shows a different district.
- Conflicting FTAS contexts or Coast major centres fail instead of choosing an unordered first row.

## Audit history

- `HistoryPage`: 100 events per page, each with a 60-character comment preview and
  suppressed/more-comment flags.
- `DetailPage`: tied to the same parent. Up to 4000 comment characters and 100 field changes per
  page. Old and new values are capped at 4000 characters with truncation flags. Import comments are
  hidden.

Readers re-check the parent and event and preserve stored labels and actors.

## Attachments

- Pages of 50, in legacy display order then document ID descending.
- Missing and inaccessible parents both return 404. No visible documents returns an empty page.
- Paper and not-applicable document records are listed like any other.
- Rows have document ID and type, transmission code, file basename, description, revision and
  created/updated local times.
- Only the basename is returned, so legacy client paths aren't exposed. No content, storage path,
  internal file ID, URL or download link. Nothing reads `ECAS_FILE` or calls an attachment package.

### Visibility rules

These follow the legacy Coast (ECAS43) and Interior (ECAS70) attachment pages. Every ECAS role
could open both pages, but Coast filtered each document type by role.

| Rule | Coast | Interior |
| --- | --- | --- |
| Parent | One grant supplies the capability, record scope and status visibility. | Same |
| Method | `APPRAISAL_ATTACHMENT_XREF.APPRAISAL_METHOD_CODE = 'C'` | `'I'` |
| Type dates | `effective_date <= NVL(appraisal_effective_date, SYSDATE) < expiry_date` | Effective date only, as in legacy |
| Per-document role | The grant that authorized the parent must have its XREF flag set to `Y`. Admins bypass. Null denies. | None |
| ZIP | Excluded | Excluded |

Coast role mapping (provisional):

| TAPS grant | XREF flag |
| --- | --- |
| Administrator | Bypass |
| Headquarters | `HEADQUARTERS_ACCESS_IND` |
| District appraiser | `DISTRICT_ACCESS_IND` |
| Regional appraiser or clerk | `REGION_ACCESS_IND` |
| BCTS or BCTS submitter | `BCTS_ACCESS_IND` |
| Licensee or licensee viewer | `LICENSEE_ACCESS_IND` |
| Licensee submitter | `RPF_ACCESS_IND` |
| Ministry viewer | None. Legacy view-only users saw no Coast documents. |

Each grant is checked on its own, so a district viewer can't borrow another region's appraiser
flag. A forest-client filter never grants access.

The file join matches both the file ID and the ECAS ID, so a bad link can't expose another
submission's filename. Duplicate document or parent rows return 503.

### Not included

ZIP files (the UI says so), upload-form placeholders, and opening, downloading, uploading or
deleting files. An empty list doesn't reveal whether hidden or ZIP documents exist.

Attachment column names come from legacy package queries. Their sizes and nullability still need verification against the real schema; the fixture definitions are not authoritative. The [database privilege list](../backend/README.md#database-privileges) identifies the four metadata tables.

## GAS search

- `Search` filters are trimmed and uppercased (root locale), max 10 / 6, punctuation kept. Negative
  pages are rejected.
- `Key` is `type` plus `worksheetId`. Legacy discriminators 0 / 1 / 2 map to `APPRAISED`,
  `NON_APPRAISED` and `HISTORIC`. A worksheet number alone isn't unique.
- `Page` rejects more than 10 items or a total smaller than the item count.
- Several rows can share a worksheet key with different marks, so table row keys must include the
  mark. Use the summary's `timberMarks` for the full list.

[GasSearchPlan](../backend/src/main/java/ca/bc/gov/nrs/taps/read/oracle/GasSearchPlan.java) builds
the GAS search WHERE clause for `GAS_APPRAISAL_VIEW`:

- Scope, status exclusions and optional exact licence/mark filters, all bound. The projection must
  expose `STATUS_CODE`, `LICENSE`, `TIMBER_MARK` and the `record_scope` alias.
- The same SQL and parameters serve count and page. Don't filter fetched rows in Java.
- Excluded statuses, from legacy GAS search:
  `ACC APP CLR CPC DCL DFT EE FWD LFS GAS NAP NBS RCD RGN RTN SCN SEC SWI SUB`. The `NOT IN` also
  drops null status, like the legacy chained `<>` checks.
- `firstRow()`/`lastRow()` are inclusive, one-based `long` values for 10-row pages, bound after the
  filter values.

`OracleGasSearch.search` keeps the legacy ten-column UNION of APPRAISED, NON_APPRAISED and HISTORIC, including hidden method/client columns. Scope and filters apply inside each branch. Historic rows need `ACTIVE_IND='Y'`.

## GAS summaries

`SummaryVariant.resolve` matches the legacy dispatcher: `CVP` gives `CVP`, Interior `MPS` gives
`INTERIOR_MPS`, and Coast `MPS` needs a TOA indicator to give `COAST_MPS_TOA_Y` or `_N`. Anything
else has no variant, and a mismatched variant is rejected.

**Appraised ownership (provisional).** `AppraisedScopeSql` joins `APPRAISED_WORKSHEET.ECAS_ID` to
the current ADS submission and uses its client number and administrative district, with the region
from `ORG_UNIT.ROLLUP_REGION_NO`. ADSC only supplies the appraisal method. Legacy GAS search used
the ADSC client instead, and some districts came from the mark. We use ADS because ECAS uses it for
client changes and authorization.

**`OracleAppraisedSummary`** (`byTypedKey` for APPRAISED keys, `byEcasId` via the stored parent):

- Needs `GAS_APPRAISAL_VIEW` with matching scope. Missing or unauthorized is empty; more than one
  parent fails.
- One statement: a scoped parent CTE plus tagged UNION ALL rows, so marks and rates don't
  multiply. Marks in mark order, rates by effective date then rate ID.
- Null TOA becomes `'N'` (legacy `NVL`); other non-Y/N values fail.
- Status is a LEFT JOIN, so a missing or expired description doesn't hide the record. Search status
  exclusions don't apply.

**`OtherWorksheetScopeSql`** (provisional) handles historic and non-appraised ownership. Client
comes from the legacy HVA/HVX licence and A-link client paths. District is the harvesting district,
then road or private-mark district for non-appraised. Region is the `ORG_UNIT` rollup. Ambiguous
relationships fail.

**`OracleOtherWorksheetSummary`** (`historic`, `nonAppraised`) needs the exact family key. ASR/NASR
children must match the family foreign key. It returns stored headers, rates, historic species,
Coast grades and selected add-ons. Add-ons keep the legacy `code - description` label and expired
selections. Available add-ons, costs and eligibility aren't included; nothing is calculated.

## Licence marks and FTA information

- `LicenceMarks` can list a mark with no worksheets. Empty means none found.
- `SearchResult` has the worksheet `Page` and a nullable `FtaLicenceInformation`. Show each even
  when the other is empty.
- `FtaLicenceInformation.cuttingPermit` can hold several comma-separated permits. Don't split or
  truncate it.

Both readers use `FtaScopeSql` with `GAS_APPRAISAL_VIEW`. Scope is the mark's district and its
`ORG_UNIT` rollup (provisional).

`OracleLicenceMarks.forLicence` is the legacy hauling-authority (HA) chooser: exact licence, each
file/mark pair authorized, ordered by mark, no road-only marks. An EXISTS check stops duplicate
ownership rows from repeating marks.

`OracleFtaLicenceInformation.find` returns the eleven-field licence panel:

- Mark required, licence optional. Mark-only reads find files through HA and blanket-road records.
- Handles permit, private and road contexts. No worksheet needed.
- Status is licence status; client name is `FOREST_CLIENT.CLIENT_NAME`.
- Displayed region is `PROV_FOREST_USE.FOREST_REGION`, which can differ from the rollup used for
  authorization.
- Client is the distinct non-null S-link client, else the A-link client. Legacy took an unordered
  first row; we fail when there is more than one client or permitted context.
- Cutting permits are joined with `, ` after scope filtering, so permits the user can't see are left
  out. Legacy used a 500-character buffer, so text over 498 characters fails. The query keeps the
  legacy split between the HA licence and the HVA file; don't add a file-equality check.

No client-scoped role has `GAS_APPRAISAL_VIEW` today. Revisit client authorization before reusing
the permit query for a client-facing feature.

## Contract fixture

The [synthetic workflow fixture](../backend/src/test/resources/contracts/synthetic-workflow.json)
has 13 examples, including an FTA-only mark, two GAS rows for one Coast worksheet and ECAS rows
sharing a submission.
[ReadContractsTest](../backend/src/test/java/ca/bc/gov/nrs/taps/read/ReadContractsTest.java) checks
it against these records.

The [Coast date draft](../frontend/README.md#coast-appraisal-date-draft) is frontend-only and has no backend write contract.
