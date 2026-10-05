# ECAS attachment inventory

`GET /api/ecas/{ecasId}/attachments?page=0` lists document metadata for a submission. It needs
`ECAS_SUBMISSION_VIEW`, then the reader checks the parent record and each document's visibility.

- Pages of 50, in legacy display order then document ID descending.
- Missing and inaccessible parents both return 404. No visible documents returns an empty page.
- Paper and not-applicable document records are listed like any other.
- Rows have document ID and type, transmission code, file basename, description, revision and
  created/updated local times.
- Only the basename is returned, so legacy client paths aren't exposed. No content, storage path,
  URL or download link. Nothing reads `ECAS_FILE` or calls an attachment package.

## Visibility rules

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

## Not included

ZIP files (the UI says so), upload-form placeholders, and opening, downloading, uploading or
deleting files. An empty list doesn't reveal whether hidden or ZIP documents exist.

## Tables

`ADS_SUPPORT_DOCUMENT`, `APPRAISAL_DOCUMENT_TYPE_CODE`, `APPRAISAL_ATTACHMENT_XREF` and
`ECAS_SUBMITTED_FILE`. Column names come from the legacy package queries. We don't have DDL for
these tables, so the test fixture's sizes and nullability are guesses until we can query the real
schema. See the [privilege list](oracle-read-runtime.md) and
[activation checklist](activation-acceptance.md).
