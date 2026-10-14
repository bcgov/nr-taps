import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@carbon/react'
import type { GasWorksheetSummary, StoredNonAppraisedRate } from '@/contracts/appraisal'
import TableFrame from '../TableFrame'
import { GasStoredSummary } from './AppraisalDetails'
import { displayCode } from './AppraisalResults'
import type { GasAuditApi } from '@/service/gas-audit-service'
import GasAuditDetails from './GasAuditDetails'
import type { ReadApi } from '@/service/read-service'
import GasFtaContext from './GasFtaContext'
import { formatDisplayDate } from '../iso-date'

const yesNo = (value: boolean | null) => (value === null ? '—' : value ? 'Yes' : 'No')

function StoredDetailsTable({
  title,
  headers,
  rows,
  numericColumns = [],
}: {
  title: string
  headers: string[]
  rows: { id: string; values: (string | null)[] }[]
  numericColumns?: number[]
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
                {headers.map((header, index) => (
                  <TableHeader
                    key={header}
                    className={numericColumns.includes(index) ? 'taps-numeric-cell' : undefined}
                  >
                    {header}
                  </TableHeader>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.id}>
                  {row.values.map((value, index) => (
                    <TableCell
                      key={headers[index]}
                      className={numericColumns.includes(index) ? 'taps-numeric-cell' : undefined}
                    >
                      {value ?? '—'}
                    </TableCell>
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
  if (rates.length === 0) return <p>No non-appraised rates are recorded for this worksheet.</p>
  return (
    <TableFrame ariaLabel="Non-appraised rates">
      <Table useZebraStyles size="md" aria-label="Non-appraised rates">
        <TableHead>
          <TableRow>
            {[
              'Species',
              'Product',
              'Grade',
              'Reserve rate',
              'Silviculture levy',
              'Development levy',
              'Upset rate',
              'Bonus bid',
              'Total rate',
            ].map((label, index) => (
              <TableHeader key={label} className={index >= 3 ? 'taps-numeric-cell' : undefined}>
                {label}
              </TableHeader>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rates.map((rate) => (
            <TableRow key={rate.rateId}>
              {[
                displayCode(rate.scaleSpecies),
                displayCode(rate.scaleProduct),
                displayCode(rate.scaleGrade),
                rate.reserveStumpageRate,
                rate.silvicultureLevy,
                rate.developmentLevy,
                rate.upsetStumpageRate,
                rate.bonusBidAmount,
                rate.totalStumpageRate,
              ].map((value, index) => (
                <TableCell
                  className={index >= 3 ? 'taps-numeric-cell' : undefined}
                  key={
                    [
                      'species',
                      'product',
                      'grade',
                      'reserve',
                      'silviculture',
                      'development',
                      'upset',
                      'bonus',
                      'total',
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

export default function OtherWorksheetDetails({
  summary,
  auditApi,
  api,
}: {
  summary: GasWorksheetSummary
  auditApi?: GasAuditApi
  api?: Pick<ReadApi, 'licenceInformation'>
}) {
  if ('ecasId' in summary)
    return <GasStoredSummary summary={summary} api={api} auditApi={auditApi} />
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
            ['Effective date', formatDisplayDate(summary.effectiveDate)],
            ['Expiry date', formatDisplayDate(summary.expiryDate)],
          ] as const
        ).map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value ?? '—'}</dd>
          </div>
        ))}
      </dl>
      <GasFtaContext
        key={`${summary.key.type}:${summary.key.worksheetId}`}
        timberMark={summary.timberMark}
        api={api}
      />
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
              <dd>{formatDisplayDate(summary.ceaseAdjustmentDate)}</dd>
            </div>
          </dl>
          <h3>Stored stumpage rates</h3>
          {summary.rates.length === 0 ? (
            <p>No stored stumpage rates are recorded for this worksheet.</p>
          ) : (
            <TableFrame ariaLabel="Historic stored rates">
              <Table
                useZebraStyles
                size="md"
                aria-label="Historic stored rates"
                className="taps-stored-rates-table"
              >
                <TableHead>
                  <TableRow>
                    <TableHeader>Rate ID</TableHeader>
                    <TableHeader>Effective date</TableHeader>
                    <TableHeader className="taps-numeric-cell">Rate</TableHeader>
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
          <h3>Non-appraised rates</h3>
          <RateComponents rates={summary.nonAppraisedRates} />
          <StoredDetailsTable
            title="Historic species"
            numericColumns={[2, 3, 4, 5, 6]}
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
            numericColumns={[3]}
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
                ['Reference type', displayCode(summary.referenceType)],
                ...(summary.appraisalMethod === 'C'
                  ? [['Timber supply block', displayCode(summary.timberSupplyBlock)] as const]
                  : []),
                ['Forest zone', displayCode(summary.appraisalForestZone)],
                ['Rate type', displayCode(summary.nonAppraisedRateType)],
                ['Adjustment type', displayCode(summary.rateAdjustmentType)],
                ['SDM acceptance date', formatDisplayDate(summary.sdmDeclarationAcceptanceDate)],
              ] as const
            ).map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value ?? '—'}</dd>
              </div>
            ))}
          </dl>
          <h3>Non-appraised rates</h3>
          <RateComponents rates={summary.rates} />
          <StoredDetailsTable
            title="Selected rate add-ons"
            headers={['Code', 'Description', 'Effective', 'Expiry', 'Code updated']}
            rows={summary.selectedRateAddons.map((addon) => ({
              id: addon.code,
              values: [
                addon.code,
                addon.description,
                formatDisplayDate(addon.effectiveDate),
                formatDisplayDate(addon.expiryDate),
                addon.updateTimestamp,
              ],
            }))}
          />
          <GasAuditDetails worksheetKey={summary.key} api={auditApi} />
        </>
      )}
    </>
  )
}
