import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@carbon/react'
import type { GasWorksheetSummary, StoredNonAppraisedRate } from '@/contracts/appraisal'
import TableFrame from '../TableFrame'
import { GasStoredSummary } from './AppraisalDetails'
import { displayCode } from './AppraisalResults'

const yesNo = (value: boolean | null) => (value === null ? '—' : value ? 'Yes' : 'No')

function StoredDetailsTable({
  title,
  headers,
  rows,
}: {
  title: string
  headers: string[]
  rows: { id: string; values: (string | null)[] }[]
}) {
  return (
    <>
      <h3>{title}</h3>
      {rows.length === 0 ? (
        <p>No {title.toLowerCase()} recorded.</p>
      ) : (
        <TableFrame ariaLabel={title}>
          <Table useZebraStyles size="md" aria-label={title}>
            <TableHead>
              <TableRow>
                {headers.map((header) => (
                  <TableHeader key={header}>{header}</TableHeader>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.id}>
                  {row.values.map((value, index) => (
                    <TableCell key={headers[index]}>{value ?? '—'}</TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableFrame>
      )}
    </>
  )
}

function RateComponents({ rates }: { rates: StoredNonAppraisedRate[] }) {
  return (
    <TableFrame ariaLabel="Stored rate components">
      <Table useZebraStyles size="md" aria-label="Stored rate components">
        <TableHead>
          <TableRow>
            {[
              'Rate ID',
              'Species',
              'Product',
              'Grade',
              'Reserve',
              'Bonus',
              'Development levy',
              'Silviculture levy',
            ].map((label) => (
              <TableHeader key={label}>{label}</TableHeader>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rates.map((rate) => (
            <TableRow key={rate.rateId}>
              {[
                rate.rateId,
                rate.scaleSpeciesCode,
                rate.scaleProductCode,
                rate.scaleGradeCode,
                rate.reserveStumpageRate,
                rate.bonusBidAmount,
                rate.developmentLevy,
                rate.silvicultureLevy,
              ].map((value, index) => (
                <TableCell
                  key={
                    [
                      'id',
                      'species',
                      'product',
                      'grade',
                      'reserve',
                      'bonus',
                      'development',
                      'silviculture',
                    ][index]
                  }
                >
                  {value ?? '—'}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableFrame>
  )
}

export default function OtherWorksheetDetails({ summary }: { summary: GasWorksheetSummary }) {
  if ('ecasId' in summary) return <GasStoredSummary summary={summary} />
  const historic = 'nonAppraisedRates' in summary
  return (
    <>
      <dl className="taps-field-grid">
        {(
          [
            ['Worksheet', summary.key.worksheetId],
            ['Type', historic ? 'Historic' : 'Non-appraised'],
            ['Licence', summary.licence],
            ['Timber mark', summary.timberMark],
            ['Method', summary.appraisalMethod === 'C' ? 'Coast' : 'Interior'],
            ['Status', displayCode(summary.status)],
            ['Effective date', summary.effectiveDate],
            ['Expiry date', summary.expiryDate],
          ] as const
        ).map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value ?? '—'}</dd>
          </div>
        ))}
      </dl>
      {historic ? (
        <>
          <dl className="taps-field-grid">
            <div>
              <dt>Variant</dt>
              <dd>{summary.variant}</dd>
            </div>
            <div>
              <dt>Policy version</dt>
              <dd>{summary.policyVersion ?? '—'}</dd>
            </div>
            <div>
              <dt>Calculation method</dt>
              <dd>{summary.rateCalculationMethodCode}</dd>
            </div>
            <div>
              <dt>Tenure obligation adjustment</dt>
              <dd>{yesNo(summary.tenureObligationAdjustment)}</dd>
            </div>
            <div>
              <dt>Adjust quarterly</dt>
              <dd>{yesNo(summary.adjustQuarterly)}</dd>
            </div>
            <div>
              <dt>Active</dt>
              <dd>{yesNo(summary.active)}</dd>
            </div>
            <div>
              <dt>Cease adjustment date</dt>
              <dd>{summary.ceaseAdjustmentDate ?? '—'}</dd>
            </div>
          </dl>
          <h3>Stored stumpage rates</h3>
          <TableFrame ariaLabel="Historic stored rates">
            <Table useZebraStyles size="md" aria-label="Historic stored rates">
              <TableHead>
                <TableRow>
                  <TableHeader>Rate ID</TableHeader>
                  <TableHeader>Effective date</TableHeader>
                  <TableHeader>Rate</TableHeader>
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
          <h3>Non-appraised rate components</h3>
          <RateComponents rates={summary.nonAppraisedRates} />
          <StoredDetailsTable
            title="Historic species"
            headers={[
              'Species',
              'Selling price zone',
              'Volume (m³)',
              'Lumber recovery factor',
              'Decay %',
              'Stud %',
              'Burn %',
            ]}
            rows={summary.historicSpecies.map((species) => ({
              id: species.speciesId,
              values: [
                species.scaleSpeciesCode,
                species.sellingPriceZone,
                species.speciesVolume,
                species.lumberRecoveryFactor,
                species.speciesDecayPercent,
                species.speciesStudPercent,
                species.speciesBurnPercent,
              ],
            }))}
          />
          <StoredDetailsTable
            title="Coast species grades"
            headers={['Species', 'Product', 'Grade', 'Grade %']}
            rows={summary.coastSpeciesGrades.map((grade) => ({
              id: grade.speciesGradeId,
              values: [
                grade.scaleSpeciesCode,
                grade.scaleProductCode,
                grade.scaleGradeCode,
                grade.speciesGradePercent,
              ],
            }))}
          />
        </>
      ) : (
        <>
          <dl className="taps-field-grid">
            {(
              [
                ['Reference type', summary.referenceTypeCode],
                ['TSB', summary.tsbNumberCode],
                ['Forest zone', summary.appraisalForestZoneCode],
                ['Rate type', summary.nonAppraisedRateTypeCode],
                ['Adjustment type', summary.rateAdjustmentTypeCode],
                ['SDM acceptance date', summary.sdmDeclarationAcceptanceDate],
              ] as const
            ).map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value ?? '—'}</dd>
              </div>
            ))}
          </dl>
          <h3>Stored rate components</h3>
          <RateComponents rates={summary.rates} />
          <StoredDetailsTable
            title="Selected rate add-ons"
            headers={['Code', 'Description', 'Effective', 'Expiry', 'Code updated']}
            rows={summary.selectedRateAddons.map((addon) => ({
              id: addon.code,
              values: [
                addon.code,
                addon.description,
                addon.effectiveDate,
                addon.expiryDate,
                addon.updateTimestamp,
              ],
            }))}
          />
        </>
      )}
    </>
  )
}
