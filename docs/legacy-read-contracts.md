# Legacy read contracts

The Java records returned by the ECAS and GAS read API. Field lists are in the records below and
the matching frontend types in `frontend/src/contracts`. This page covers the rules that aren't
obvious from the code. SQL behaviour is in
[Oracle read foundation](oracle-read-foundation.md) and routes in
[Oracle read runtime](oracle-read-runtime.md).

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

## General rules

- IDs are JSON strings. ECAS, worksheet and rate IDs are positive integers of up to 12 digits, and
  leading zeroes are dropped when parsing.
- Forest client numbers are 8-character strings and keep their leading zeroes.
- Dates are ISO `yyyy-MM-dd`, or `null`. `EffectiveCode` keeps the time of day as a local
  date-time, with no timezone conversion.
- Display values are nullable unless noted. Lists keep their order and are immutable.
- Stored amounts are exact two-decimal strings such as `"12.30"`: `NUMBER(6,2)` for `StoredRate`
  and ASR, `NUMBER(5,2)` for NASR components. Overflow or rounding is rejected. Reference
  quantities are `BigDecimal` JSON numbers.
- No totals, breakdowns, policy costs or action flags are calculated.

## ECAS search

`EcasInbox.Search`:

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

## ECAS references

`Header` requires only ECAS ID and method. `Coast` adds the primary mark, all submitted marks (each
with its own revision count), cruise volume, areas and major centre. `Interior` adds its single
mark, mark revision count, point of appraisal, selling price zone, comparative cruise and salvage.
These are read views, not the full editing forms.

## GAS search

- `Search` filters are trimmed and uppercased (root locale), max 10 / 6, punctuation kept. Negative
  pages are rejected.
- `Key` is `type` plus `worksheetId`. Legacy discriminators 0 / 1 / 2 map to `APPRAISED`,
  `NON_APPRAISED` and `HISTORIC`. A worksheet number alone isn't unique.
- `Page` rejects more than 10 items or a total smaller than the item count.
- Several rows can share a worksheet key with different marks, so table row keys must include the
  mark. Use the summary's `timberMarks` for the full list.
- `LicenceMarks` can list a mark with no worksheets. Empty means none found.
- `SearchResult` has the worksheet `Page` and a nullable `FtaLicenceInformation`. Show each even
  when the other is empty.
- `FtaLicenceInformation.cuttingPermit` can hold several comma-separated permits. Don't split or
  truncate it.

## GAS summaries

`SummaryVariant.resolve` matches the legacy dispatcher: `CVP` gives `CVP`, Interior `MPS` gives
`INTERIOR_MPS`, and Coast `MPS` needs a TOA indicator to give `COAST_MPS_TOA_Y` or `_N`. Anything
else has no variant, and a mismatched variant is rejected.

`HistoricSummary` and `NonAppraisedSummary` return stored header fields, rates, historic species
and Coast grade rows, and selected add-ons. Add-ons keep the legacy `code - description` label and
expired selections. Available add-ons, costs and eligibility aren't included.

## Audit and attachments

- `HistoryPage`: 100 events per page, each with a 60-character comment preview and
  suppressed/more-comment flags.
- `DetailPage`: tied to the same parent. Up to 4000 comment characters and 100 field changes per
  page. Old and new values are capped at 4000 characters with truncation flags. Import comments are
  hidden.
- `EcasAttachments.Page`: 50 rows per page, basenames only, no content, paths, URLs or internal
  file IDs. See [attachment inventory](attachment-inventory.md).

## Fixture and access rules

The [synthetic workflow fixture](../backend/src/test/resources/contracts/synthetic-workflow.json)
has 13 examples, including an FTA-only mark, two GAS rows for one Coast worksheet and ECAS rows
sharing a submission.
[ReadContractsTest](../backend/src/test/java/ca/bc/gov/nrs/taps/read/ReadContractsTest.java) checks
it against these records.

Readers follow the [record-scope rules](access-and-identity.md): ownership comes from the record and
its parents, and capability and scope come from the same grant. Request filters and IDs grant
nothing. Future reports and writes must do the same.

The [Coast date draft](coast-appraisal-date-draft.md) is frontend-only, with no backend contract.
