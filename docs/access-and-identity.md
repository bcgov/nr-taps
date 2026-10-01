# TAPS access and identity

## Account types

Confirmed project direction is **IDIR and Business BCeID only**. Do not enable Basic BCeID. The published [ECAS access form](https://forms.gov.bc.ca/industry/electronic-commerce-appraisal-system-ecas-requesting-access-or-removal/) requires Business BCeID for industry users and offers IDIR or BCeID for BCTS consultants in its government-access section.

| Persona                                                    | Sign-in type                | Access boundary                                                                                |
| ---------------------------------------------------------- | --------------------------- | ---------------------------------------------------------------------------------------------- |
| Ministry district, region, headquarters and administrators | IDIR MFA                    | Approved staff role plus its district/region scope, or an explicitly approved provincial grant |
| BCTS staff                                                 | IDIR MFA                    | BCTS role scoped to an approved forest client                                                  |
| BCTS consultants                                           | Business BCeID              | BCTS role scoped to an approved forest client; this does not grant ministry administration     |
| Licensee staff and external RPF/RFT representatives        | Business BCeID              | Approved licensee role scoped to one or more forest clients                                    |
| Basic BCeID                                                | Unsupported                 | No TAPS access                                                                                 |
| Batch/report/agent identities                              | Separate future integration | No interactive user role is inferred from a legacy service identity                            |

[Business BCeID](https://www.bceid.ca/aboutbceid/) is intended for people representing a business/legal entity; Basic BCeID is for personal-capacity access. A Business BCeID account does not by itself establish an active RPF/RFT qualification or permission to represent a forest client. Those are separate authorization and business checks.

The legacy Java/Oracle archives identify `IDIR` and generic `BCEID` directories and client/organization relationships. They do not record the account tier. The TAPS provider choice is now supported by published ECAS requirements and confirmed project direction, rather than inferred from a `BCEID\USER` audit string. Historical identity and audit mapping still require verification when Oracle integration begins.

## Proposed application roles

The account-type decision is confirmed; the following role catalogue and capability policy still require business review before provisioning. Exact codes and capabilities are defined in [TapsRole.java](../backend/src/main/java/ca/bc/gov/nrs/taps/security/TapsRole.java).

| Code                      | Supported provider     | Required scope            | Intended persona/access                                                                                        |
| ------------------------- | ---------------------- | ------------------------- | -------------------------------------------------------------------------------------------------------------- |
| `TAPS_ADMIN`              | IDIR                   | None, proposed provincial | ECAS review/status/reference and GAS administration; ordinary client submit and GAS BCTS override are excluded |
| `TAPS_HEADQUARTERS`       | IDIR                   | None, proposed provincial | Conservative ECAS read and GAS client/HQ reporting subset                                                      |
| `TAPS_VIEWER`             | IDIR                   | District                  | ECAS read and GAS client reporting                                                                             |
| `TAPS_REGION_APPRAISER`   | IDIR                   | Region                    | ECAS regional review/risk and GAS appraisal/non-appraised work                                                 |
| `TAPS_REGION_CLERK`       | IDIR                   | Region                    | ECAS read, GAS worksheet/non-appraised work; bulk runs excluded initially                                      |
| `TAPS_DISTRICT_APPRAISER` | IDIR                   | District                  | ECAS district review/risk and GAS worksheet/report read                                                        |
| `TAPS_BCTS`               | IDIR or Business BCeID | Forest client             | BCTS data entry, client reports                                                                                |
| `TAPS_BCTS_SUBMITTER`     | IDIR or Business BCeID | Forest client             | BCTS entry/submit and proposed scoped GAS BCTS rate update                                                     |
| `TAPS_LICENSEE`           | Business BCeID         | Forest client             | Licensee data entry, client reports                                                                            |
| `TAPS_LICENSEE_SUBMITTER` | Business BCeID         | Forest client             | Licensee entry/submit; active professional representation checks required with the endpoint                    |
| `TAPS_LICENSEE_VIEWER`    | Business BCeID         | Forest client             | Licensee read, client reports                                                                                  |

Keep administrator/role-management permissions in FAM. `FAM_ADMIN`, delegated administration and `FAM:` bookkeeping/label/description roles are not TAPS business roles. A Business BCeID consultant can hold a scoped BCTS role but cannot use an IDIR-only ministry role.

## Grant format and enforcement

| Scope         | FAM marker          | Synthetic example                                |
| ------------- | ------------------- | ------------------------------------------------ |
| District      | `HAS_DISTRICT_ROLE` | `TAPS_DISTRICT_APPRAISER_DISTRICT-DCC`           |
| Region        | `HAS_REGION_ROLE`   | `TAPS_REGION_APPRAISER_REGION-KOOTENAY_BOUNDARY` |
| Forest client | `HAS_FOREST_CLIENT` | `TAPS_BCTS_SUBMITTER_FOREST_CLIENT-00001018`     |

Codes and scope values are case sensitive. Scoped roles require exactly one appropriate suffix; bare scoped codes, additional scope dimensions, invalid/retired regions and malformed values grant no access. District syntax is `D[A-Z]{2}` and forest clients are eight digits with leading zeroes preserved. Syntax is not proof that an organization exists or belongs to BCTS: provisioning and future endpoints must use authoritative data.

Multiple assignments compose actions while retaining each assignment's scope. The backend helper authorizes a record only when the same grant supplies both capability and scope. Region-name/code mappings and retired-region handling must be verified against authoritative district rollups before using real records.

## Decisions still open

- Provincial admin/HQ scope and the final HQ read/write/risk-assessment subset.
- Professional RPF/RFT eligibility and active client representation, including BCTS submitting requirements.
- Appraiser versus statutory-decision-maker separation.
- BCTS-funded-file/area checks, rate-update eligibility and the conservative submitter-only override proposal.
- Clerical bulk-run rights and whether the additive legacy regional status-override flag remains required.
- Record/parent relationships, attachment visibility, workflow states, locking and historic audit identity mapping.

These decisions are not approved by successful token/helper tests. See [architecture](architecture.md) and [intentional legacy divergences](intentional-legacy-divergences.md).
