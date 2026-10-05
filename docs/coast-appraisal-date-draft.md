# Coast appraisal date draft

Users with `ECAS_SUBMISSION_EDIT` see an appraisal-dates section on the Coast reference.
**Check dates** runs the rules below and **Reset draft** restores the stored values. Nothing is
saved, and leaving or reloading the record discards the draft.

Category and revision are read-only. The controls follow the LEXIS form pattern: invalid input is
kept, the first invalid field gets focus, and the inputs stay inside the mobile drawer's focus trap.

## Field rules

| Field | Rule |
| --- | --- |
| Effective and expiry | Valid `yyyy-MM-dd` dates, no UTC conversion. Blank stays blank. |
| Effective | On or after 2002-04-01. Required for reappraisal category `R`. |
| Expiry | Optional. Needs an effective date and must be on or after it. |

Code: [validator](../frontend/src/contracts/coast-reference-draft.ts) and
[component](../frontend/src/components/appraisal/CoastAppraisalDatesDraft.tsx).

## Why it doesn't save

Legacy Save Dates does more than update two fields. It checks the dates against FTA and other
appraisals for the mark, warns about overlaps, refreshes FTA defaults, can update cutting-authority
rows, sets audit context, checks the revision and depends on workflow state.

Writes like this stay in the legacy stored procedure, which TAPS doesn't call yet. A save path will
need the user's identity, an edit capability and state check, record scope, revision conflict
handling and audit context.

Interior dates also depend on appraisal-manual data and policy dates, so don't reuse these rules
for Interior.

See [activation acceptance](activation-acceptance.md) for the shared-environment checks.
