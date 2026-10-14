import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@carbon/react'
import type {
  CoastReference,
  InteriorReference,
  ReferenceHeader,
  GasAppraisedSummary,
} from '@/contracts/appraisal'
import TableFrame from '../TableFrame'
import { displayCode, worksheetTypeLabels } from './AppraisalResults'
import type { ReadApi } from '@/service/read-service'
import GasFtaContext from './GasFtaContext'
import GasAuditDetails from './GasAuditDetails'
import type { GasAuditApi } from '@/service/gas-audit-service'
import { formatDisplayDate } from '../iso-date'

const yesNo = (value: boolean | null) => (value === null ? '—' : value ? 'Yes' : 'No')

function Fields({ rows }: { rows: (readonly [string, string | number | null])[] }) {
  return (
    <dl className="taps-field-grid">
      {rows.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value ?? '—'}</dd>
        </div>
      ))}
    </dl>
  )
}

function HeaderDetails({ header }: { header: ReferenceHeader }) {
  return (
    <>
      <h3>Submission information</h3>
      <Fields
        rows={[
          ['ECAS ID', header.ecasId],
          ['Submission revision', header.revisionCount],
          ['Status', displayCode(header.status)],
        ]}
      />
      <h3>Client and tenure</h3>
      <Fields
        rows={[
          ['Licence', header.licence],
          ['Cutting permit', header.cuttingPermit],
          ['File type', displayCode(header.fileType)],
        ]}
      />
      <Fields
        rows={[
          ['Client number', header.clientNumber],
          ['Client location', header.clientLocationCode],
        ]}
      />
      <Fields rows={[['Licensee', header.licenseeName]]} />
      <h3>Appraisal information</h3>
      <Fields
        rows={[
          ['Appraisal category', header.appraisalCategoryCode],
          ['Reappraisal reason', header.reappraisalReasonCode],
        ]}
      />
      <Fields rows={[['Rate calculation method', header.rateCalculationMethodCode]]} />
      <Fields
        rows={[
          ['Coniferous stand rate eligibility', displayCode(header.coniferousStandRateEligibility)],
          ['Deciduous stand rate eligibility', displayCode(header.deciduousStandRateEligibility)],
        ]}
      />
      <Fields
        rows={[
          ['Effective date', formatDisplayDate(header.effectiveDate)],
          ['Expiry date', formatDisplayDate(header.expiryDate)],
        ]}
      />
      <h3>Geographic context</h3>
      <Fields
        rows={[
          ['Administrative district', displayCode(header.administrativeDistrict)],
          ['Geographic district', displayCode(header.geographicDistrict)],
          ['Timber supply area', displayCode(header.timberSupplyArea)],
          ['Timber supply block', displayCode(header.timberSupplyBlock)],
        ]}
      />
    </>
  )
}

export function CoastReferenceDetails({ reference }: { reference: CoastReference }) {
  return (
    <>
      <HeaderDetails header={reference.header} />
      <h3>Coast reference</h3>
      <Fields
        rows={[
          ['Primary timber mark', reference.primaryTimberMark],
          ['Reference mark', reference.referenceMark],
          ['Net cruise volume (m³)', reference.netCruiseVolume],
          ['Single tree selection volume (m³)', reference.singleTreeSelectionVolume],
          ['Net second-growth coniferous volume (m³)', reference.secondGrowthConiferousVolume],
          ['Sampling error at 2 standard deviations (%)', reference.samplingErrorPercent],
          ['Net merchantable area', reference.netMerchantableArea],
          ['Initial merchantable area', reference.initialMerchantableArea],
        ]}
      />
      <Fields rows={[['Point of appraisal distance', reference.pointOfAppraisalDistance]]} />
      <Fields
        rows={[
          ['Major centre', reference.majorCentreCode],
          ['Major centre distance', reference.majorCentreDistance],
        ]}
      />
      <h3>Volume by species</h3>
      {reference.speciesVolumes.length === 0 ? (
        <p>No species volumes are available for this submission.</p>
      ) : (
        <TableFrame ariaLabel="Volume by species">
          <Table
            useZebraStyles
            size="md"
            aria-label="Volume by species"
            className="taps-species-volume-table"
          >
            <TableHead>
              <TableRow>
                <TableHeader>Species</TableHeader>
                <TableHeader className="taps-numeric-cell">Volume (m³)</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {reference.speciesVolumes.map((row) => (
                <TableRow key={`${reference.header.ecasId}:${row.species.code}`}>
                  <TableCell>
                    {row.species.code !== null && row.species.description !== null
                      ? `${row.species.code} - ${row.species.description}`
                      : displayCode(row.species)}
                  </TableCell>
                  <TableCell className="taps-numeric-cell">{row.volume ?? '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableFrame>
      )}
      <h3>Submitted timber marks</h3>
      {reference.timberMarks.length === 0 ? (
        <p>No submitted timber marks are available for this submission.</p>
      ) : (
        <TableFrame ariaLabel="Submitted timber marks">
          <Table
            useZebraStyles
            size="md"
            aria-label="Submitted timber marks"
            className="taps-submitted-marks-table"
          >
            <TableHead>
              <TableRow>
                <TableHeader>Timber mark</TableHeader>
                <TableHeader className="taps-numeric-cell">Volume (m³)</TableHeader>
                <TableHeader>Primary</TableHeader>
                <TableHeader className="taps-numeric-cell">Mark revision</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {reference.timberMarks.map((mark) => (
                <TableRow key={mark.timberMark}>
                  <TableCell>{mark.timberMark ?? '—'}</TableCell>
                  <TableCell className="taps-numeric-cell">{mark.cruiseVolume ?? '—'}</TableCell>
                  <TableCell>{yesNo(mark.primary)}</TableCell>
                  <TableCell className="taps-numeric-cell">{mark.revisionCount ?? '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableFrame>
      )}
    </>
  )
}

export function InteriorReferenceDetails({ reference }: { reference: InteriorReference }) {
  return (
    <>
      <HeaderDetails header={reference.header} />
      <h3>Interior reference</h3>
      <Fields
        rows={[
          ['Timber mark', reference.timberMark],
          ['Timber mark revision', reference.timberMarkRevisionCount],
          ['Reference mark', reference.referenceMark],
        ]}
      />
      <Fields
        rows={[
          ['Point of appraisal', displayCode(reference.pointOfAppraisal)],
          ['Selling price zone', reference.sellingPriceZoneCode],
        ]}
      />
      <Fields
        rows={[
          ['Comparative cruise', yesNo(reference.comparativeCruise)],
          ['Salvage', yesNo(reference.salvage)],
        ]}
      />
    </>
  )
}

export function GasStoredSummary({
  summary,
  api,
  auditApi,
}: {
  summary: GasAppraisedSummary
  api?: Pick<ReadApi, 'licenceInformation'>
  auditApi?: GasAuditApi
}) {
  return (
    <>
      <p>Stored worksheet and rate values. Calculated breakdowns aren't shown here.</p>
      <Fields
        rows={[
          ['Worksheet', summary.key.worksheetId],
          ['Worksheet type', worksheetTypeLabels[summary.key.type]],
          ['ECAS ID', summary.ecasId],
          ['Method', summary.appraisalMethod === 'C' ? 'Coast' : 'Interior'],
        ]}
      />
      <Fields
        rows={[
          ['Rate calculation method', summary.rateCalculationMethodCode],
          ['TOA eligible', yesNo(summary.toaEligible)],
        ]}
      />
      <Fields rows={[['Status', displayCode(summary.status)]]} />
      <Fields
        rows={[
          ['Effective date', formatDisplayDate(summary.effectiveDate)],
          ['Expiry date', formatDisplayDate(summary.expiryDate)],
        ]}
      />
      <Fields
        rows={[
          ['Reference type', summary.referenceTypeCode],
          ['Cease adjustment date', formatDisplayDate(summary.ceaseAdjustmentDate)],
        ]}
      />
      <Fields rows={[['Timber marks', summary.timberMarks.join(', ')]]} />
      <GasFtaContext
        key={`APPRAISED:${summary.key.worksheetId}`}
        timberMark={summary.primaryTimberMark}
        api={api}
      />
      <h3>Stored rates</h3>
      {summary.rates.length === 0 ? (
        <p>No stored stumpage rates are recorded for this worksheet.</p>
      ) : (
        <TableFrame ariaLabel="Stored stumpage rates">
          <Table
            useZebraStyles
            size="md"
            aria-label="Stored stumpage rates"
            className="taps-stored-rates-table"
          >
            <TableHead>
              <TableRow>
                <TableHeader>Rate ID</TableHeader>
                <TableHeader>Effective date</TableHeader>
                <TableHeader className="taps-numeric-cell">Total stumpage rate</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {summary.rates.map((rate) => (
                <TableRow key={rate.rateId}>
                  <TableCell>{rate.rateId}</TableCell>
                  <TableCell>{formatDisplayDate(rate.effectiveDate)}</TableCell>
                  <TableCell className="taps-numeric-cell">{rate.totalStumpageRate}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableFrame>
      )}
      <GasAuditDetails worksheetKey={summary.key} api={auditApi} />
    </>
  )
}
