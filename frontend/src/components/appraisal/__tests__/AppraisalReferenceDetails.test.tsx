import { render, screen, within } from '@testing-library/react'
import { expect, test } from 'vitest'
import { workflowFixture } from '@/local-synthetic/WorkflowPreview'
import { CoastReferenceDetails, InteriorReferenceDetails } from '../AppraisalDetails'

test('reference details display calendar dates in words and preserve unknown date values', () => {
  render(
    <CoastReferenceDetails
      reference={{
        ...workflowFixture.ecasCoastMultiMarkReference,
        header: {
          ...workflowFixture.ecasCoastMultiMarkReference.header,
          effectiveDate: '2026-09-28',
          expiryDate: 'unknown',
        },
      }}
    />,
  )
  expect(
    within(screen.getByText('Effective date').parentElement!).getByText('Sep 28, 2026'),
  ).toBeInTheDocument()
  expect(
    within(screen.getByText('Expiry date').parentElement!).getByText('unknown'),
  ).toBeInTheDocument()
})

test('Coast reference displays each species once with exact stored quantities and units', () => {
  const reference = {
    ...workflowFixture.ecasCoastMultiMarkReference,
    singleTreeSelectionVolume: 0,
    secondGrowthConiferousVolume: 123.456,
    samplingErrorPercent: 4.25,
    speciesVolumes: [
      { species: { code: 'FI', description: 'Fir' }, volume: 123.456, revisionCount: 2 },
      { species: { code: 'ZZ', description: null }, volume: 0, revisionCount: 3 },
      {
        species: { code: 'BA', description: '<script>Species as text</script>' },
        volume: null,
        revisionCount: null,
      },
    ],
  }
  const { container } = render(<CoastReferenceDetails reference={reference} />)
  const table = within(screen.getByRole('table', { name: 'Volume by species' }))
  expect(table.getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
    'Species',
    'Volume (m³)',
  ])
  expect(table.getAllByRole('row')).toHaveLength(4)
  expect(table.getByRole('row', { name: 'FI - Fir 123.456' })).toBeInTheDocument()
  expect(table.getByRole('row', { name: 'ZZ 0' })).toBeInTheDocument()
  expect(
    table.getByRole('row', { name: 'BA - <script>Species as text</script> —' }),
  ).toBeInTheDocument()
  expect(container.querySelector('script')).toBeNull()
  expect(
    within(screen.getByText('Single tree selection volume (m³)').parentElement!).getByText('0'),
  ).toBeInTheDocument()
  expect(
    within(screen.getByText('Net second-growth coniferous volume (m³)').parentElement!).getByText(
      '123.456',
    ),
  ).toBeInTheDocument()
  expect(
    within(
      screen.getByText('Sampling error at 2 standard deviations (%)').parentElement!,
    ).getByText('4.25'),
  ).toBeInTheDocument()
  expect(screen.getByText(/ZZ9997 — volume/)).toBeInTheDocument()
  expect(screen.getByText(/ZZ9998 — volume/)).toBeInTheDocument()
  expect(screen.queryByRole('button')).not.toBeInTheDocument()
  expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
})

test('Coast reference distinguishes missing species and scalar values from zero', () => {
  render(
    <CoastReferenceDetails
      reference={{
        ...workflowFixture.ecasCoastMultiMarkReference,
        speciesVolumes: [],
        singleTreeSelectionVolume: null,
        secondGrowthConiferousVolume: null,
        samplingErrorPercent: null,
      }}
    />,
  )
  expect(
    screen.getByText('No species volumes are available for this submission.'),
  ).toBeInTheDocument()
  expect(screen.queryByRole('table', { name: 'Volume by species' })).not.toBeInTheDocument()
  for (const label of [
    'Single tree selection volume (m³)',
    'Net second-growth coniferous volume (m³)',
    'Sampling error at 2 standard deviations (%)',
  ]) {
    expect(within(screen.getByText(label).parentElement!).getByText('—')).toBeInTheDocument()
  }
})

test('Interior reference does not acquire Coast species or cruise fields', () => {
  render(<InteriorReferenceDetails reference={workflowFixture.ecasInteriorReference} />)
  expect(screen.queryByRole('heading', { name: 'Volume by species' })).not.toBeInTheDocument()
  expect(screen.queryByText('Single tree selection volume (m³)')).not.toBeInTheDocument()
})

test('Coast reference states when no submitted timber marks are recorded', () => {
  const { container } = render(
    <CoastReferenceDetails
      reference={{ ...workflowFixture.ecasCoastMultiMarkReference, timberMarks: [] }}
    />,
  )
  expect(
    screen.getByText('No submitted timber marks are available for this submission.'),
  ).toBeInTheDocument()
  expect(container.querySelector('.taps-appraisal-mark-list')).toBeNull()
})

test.each(['Coast', 'Interior'] as const)(
  '%s reference shows stand rate eligibility labels',
  (method) => {
    const header = {
      ...workflowFixture.ecasCoastMultiMarkReference.header,
      coniferousStandRateEligibility: { code: 'S', description: 'Sawlog Grades' },
      deciduousStandRateEligibility: { code: 'N', description: 'No Grades' },
    }
    render(
      method === 'Coast' ? (
        <CoastReferenceDetails
          reference={{ ...workflowFixture.ecasCoastMultiMarkReference, header }}
        />
      ) : (
        <InteriorReferenceDetails
          reference={{
            ...workflowFixture.ecasInteriorReference,
            header: { ...header, appraisalMethod: 'I' },
          }}
        />
      ),
    )
    expect(
      within(screen.getByText('Coniferous stand rate eligibility').parentElement!).getByText(
        'Sawlog Grades',
      ),
    ).toBeInTheDocument()
    expect(
      within(screen.getByText('Deciduous stand rate eligibility').parentElement!).getByText(
        'No Grades',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  },
)

test('reference retains an eligibility code without a label and shows missing data distinctly', () => {
  render(
    <CoastReferenceDetails
      reference={{
        ...workflowFixture.ecasCoastMultiMarkReference,
        header: {
          ...workflowFixture.ecasCoastMultiMarkReference.header,
          coniferousStandRateEligibility: { code: 'X', description: null },
          deciduousStandRateEligibility: null,
        },
      }}
    />,
  )
  expect(
    within(screen.getByText('Coniferous stand rate eligibility').parentElement!).getByText('X'),
  ).toBeInTheDocument()
  expect(
    within(screen.getByText('Deciduous stand rate eligibility').parentElement!).getByText('—'),
  ).toBeInTheDocument()
})
