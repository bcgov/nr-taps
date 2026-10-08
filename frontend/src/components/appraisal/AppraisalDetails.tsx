import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@carbon/react'
import type {
  CoastReference,
  InteriorReference,
  ReferenceHeader,
  GasAppraisedSummary,
} from '@/contracts/appraisal'
import TableFrame from '../TableFrame'
import { displayCode } from './AppraisalResults'

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
    <Fields
      rows={[
        ['ECAS ID', header.ecasId],
        ['Submission revision', header.revisionCount],
        ['Licence', header.licence],
        ['Cutting permit', header.cuttingPermit],
        ['Client number', header.clientNumber],
        ['Client location', header.clientLocationCode],
        ['Licensee', header.licenseeName],
        ['Status', displayCode(header.status)],
        ['Appraisal category', header.appraisalCategoryCode],
        ['Reappraisal reason', header.reappraisalReasonCode],
        ['Rate calculation method', header.rateCalculationMethodCode],
        ['Coniferous stand rate eligibility', displayCode(header.coniferousStandRateEligibility)],
        ['Deciduous stand rate eligibility', displayCode(header.deciduousStandRateEligibility)],
        ['Effective date', header.effectiveDate],
        ['Expiry date', header.expiryDate],
        ['Administrative district', displayCode(header.administrativeDistrict)],
        ['Geographic district', displayCode(header.geographicDistrict)],
        ['File type', displayCode(header.fileType)],
        ['Timber supply area', displayCode(header.timberSupplyArea)],
        ['Timber supply block', displayCode(header.timberSupplyBlock)],
      ]}
    />
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
          ['Net cruise volume', reference.netCruiseVolume],
          ['Net merchantable area', reference.netMerchantableArea],
          ['Initial merchantable area', reference.initialMerchantableArea],
          ['Point of appraisal distance', reference.pointOfAppraisalDistance],
          ['Major centre', reference.majorCentreCode],
          ['Major centre distance', reference.majorCentreDistance],
        ]}
      />
      <h3>Submitted timber marks</h3>
      <ul className="taps-appraisal-mark-list">
        {reference.timberMarks.map((mark) => (
          <li key={mark.timberMark}>
            {mark.timberMark ?? '—'} — volume {mark.cruiseVolume ?? '—'}; primary{' '}
            {yesNo(mark.primary)}; mark revision {mark.revisionCount ?? '—'}
          </li>
        ))}
      </ul>
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
          ['Point of appraisal', displayCode(reference.pointOfAppraisal)],
          ['Selling price zone', reference.sellingPriceZoneCode],
          ['Comparative cruise', yesNo(reference.comparativeCruise)],
          ['Salvage', yesNo(reference.salvage)],
        ]}
      />
    </>
  )
}

export function GasStoredSummary({ summary }: { summary: GasAppraisedSummary }) {
  return (
    <>
      <p>Stored worksheet and rate values. Calculated breakdowns aren't shown here.</p>
      <Fields
        rows={[
          ['Worksheet', summary.key.worksheetId],
          ['Worksheet type', summary.key.type],
          ['ECAS ID', summary.ecasId],
          ['Method', summary.appraisalMethod === 'C' ? 'Coast' : 'Interior'],
          ['Summary variant', summary.variant],
          ['Rate calculation method', summary.rateCalculationMethodCode],
          ['TOA eligible', yesNo(summary.toaEligible)],
          ['Status', displayCode(summary.status)],
          ['Effective date', summary.effectiveDate],
          ['Expiry date', summary.expiryDate],
          ['Reference type', summary.referenceTypeCode],
          ['Cease adjustment date', summary.ceaseAdjustmentDate],
          ['Timber marks', summary.timberMarks.join(', ')],
        ]}
      />
      <h3>Stored rates</h3>
      <TableFrame ariaLabel="Stored stumpage rates">
        <Table useZebraStyles size="md" aria-label="Stored stumpage rates">
          <TableHead>
            <TableRow>
              <TableHeader>Rate ID</TableHeader>
              <TableHeader>Effective date</TableHeader>
              <TableHeader>Total stumpage rate</TableHeader>
            </TableRow>
          </TableHead>
          <TableBody>
            {summary.rates.map((rate) => (
              <TableRow key={rate.rateId}>
                <TableCell>{rate.rateId}</TableCell>
                <TableCell>{rate.effectiveDate}</TableCell>
                <TableCell>{rate.totalStumpageRate}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableFrame>
    </>
  )
}
