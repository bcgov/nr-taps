# Oracle read foundation

How the Oracle readers in
[`read/oracle`](../backend/src/main/java/ca/bc/gov/nrs/taps/read/oracle) map the legacy ECAS and
GAS queries. Settings, routes and grants are in [Oracle read runtime](oracle-read-runtime.md).

Low-risk legacy reads are rewritten as parameterized SELECTs. Writes and calculations stay in the
legacy stored procedures. The SQL has only run against mocked JDBC and a disposable Oracle Free
schema. Anything marked provisional is our reading of the legacy code, to be checked against the
real schema once the proxy account exists.

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
file type, including inactive codes. The `active` flag is a display hint only:
`SYSDATE > TRUNC(EFFECTIVE_DATE) AND SYSDATE < TRUNC(EXPIRY_DATE)`. Organization choices are
unexpired units from the user's ministry FAM grants: primary regions and districts for a provincial
grant, subordinate units for a regional grant, none from client grants. They are filters only.

[EffectiveCode](../backend/src/main/java/ca/bc/gov/nrs/taps/read/EffectiveCode.java) maps `DATE`
with `getTimestamp().toLocalDateTime()`, keeping the time of day with no timezone.

## Record scope

[ReadScopePredicate](../backend/src/main/java/ca/bc/gov/nrs/taps/read/oracle/ReadScopePredicate.java)
turns the user's grants for one capability into a single OR expression plus bind values. No
qualifying grant gives `(1 = 0)` and a provincial grant gives `(1 = 1)`. A scoped grant missing its
suffix is rejected rather than treated as provincial.

Each reader supplies a `record_scope` projection with `ADMIN_DISTRICT_CODE`, `ROLLUP_REGION_CODE`
and `CLIENT_NUMBER`, loaded from the record and its parents, never from request values. Null
ownership never matches a scoped grant. Direct reads authorize the loaded parent and children, and
ECAS visibility doesn't grant GAS access.

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

## GAS worksheets

**Appraised ownership (provisional).** `AppraisedScopeSql` joins `APPRAISED_WORKSHEET.ECAS_ID` to
the current ADS submission and uses its client number and administrative district, with the region
from `ORG_UNIT.ROLLUP_REGION_NO`. ADSC only supplies the appraisal method. Legacy GAS search used
the ADSC client instead, and some districts came from the mark. We use ADS because ECAS uses it for
client changes and authorization. See `PROVISIONAL_APPRAISED_OWNER` in
[legacy divergences](intentional-legacy-divergences.md).

**`OracleAppraisedSummary`** (`byTypedKey` for APPRAISED keys, `byEcasId` via the stored parent):

- Needs `GAS_APPRAISAL_VIEW` with matching scope. Missing or unauthorized is empty; more than one
  parent fails.
- One statement: a scoped parent CTE plus tagged UNION ALL rows, so marks and rates don't
  multiply. Marks in mark order, rates by effective date then rate ID.
- Null TOA becomes `'N'` (legacy `NVL`); other non-Y/N values fail.
- Status is a LEFT JOIN, so a missing or expired description doesn't hide the record. Search status
  exclusions don't apply.

**`OracleGasSearch.search`** keeps the legacy ten-column UNION of APPRAISED, NON_APPRAISED and
HISTORIC, including hidden method/client columns. Scope and filters apply inside each branch.
Historic rows need `ACTIVE_IND='Y'`.

**`OtherWorksheetScopeSql`** (provisional) handles historic and non-appraised ownership. Client
comes from the legacy HVA/HVX licence and A-link client paths. District is the harvesting district,
then road or private-mark district for non-appraised. Region is the `ORG_UNIT` rollup. Ambiguous
relationships fail.

**`OracleOtherWorksheetSummary`** (`historic`, `nonAppraised`) needs the exact family key. ASR/NASR
children must match the family foreign key. It also returns historic species, Coast species
grades and selected add-ons, keeping expired codes. Values are as stored; nothing is calculated.

## Licence marks and FTA information

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

## ECAS inbox

`OracleEcasInbox.search` supports `ALL_SUBMISSIONS` with 100-row pages. One scoped SELECT replaces
the legacy ECAS05 temporary-table DELETE/INSERT.

- All filters and 13 sort fields. Sorting is by day with fixed tie-breakers.
- Every matching mark/permit row is returned. Without a mark filter, primary marks are listed; an
  explicit mark can match a non-primary one.
- Organization selections only narrow results.
- A direct ECAS ID skips other criteria but keeps authorization, status visibility, organizations,
  client/location and certification.
- Certified `false` includes null; BCTS `false` requires `N`. Worked-on requires an audit event.
  FTA cutting-permit expiry applies only when both bounds are set.
- `MY_TO_DO` listing fails before SQL runs. It needs an assignment mapping we don't have yet.
- Client names come from `SIL_GET_CLIENT_NAME`. Native midnight bounds and day truncation stand in
  for the legacy SIL date-conversion helper (provisional).

## ECAS references

`OracleEcasReference.coast` and `.interior` use ADS ownership and `EcasReadPredicate`, which ties
draft/scenario visibility to each FAM grant.

- The requested method must match the stored one.
- Coast needs one primary mark and returns all marks and revisions. Interior needs its single mark.
- Includes FTAS defaults, cutting-permit management overrides, TSA/TSB fallback, client labels,
  the Coast species-volume sum and the Interior selling-zone default for dates before 2018-11-01.
- Authorization stays ADS-based even when FTAS shows a different district.
- Conflicting contexts fail.

## Open questions

To check once the proxy account exists (see [activation acceptance](activation-acceptance.md)):

- Object owners, grants and column types under the proxy account.
- Each query compared with its legacy procedure: date boundaries, non-midnight times, nulls,
  sentinels and ordering.
- Driver and session date handling, and Oracle versus JVM clock for the ECAS `active` hint.
- ADS versus ADSC owner mismatches, and district paths for FTA and other families.
- Whether family, worksheet ID and mark make a unique paging key.
- `SIL_GET_CLIENT_NAME` behaviour and the date-helper substitute.
- Execution plans and FAM role results with real users.
