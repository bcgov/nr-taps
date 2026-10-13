import { render, screen, within } from '@testing-library/react'
import { expect, test } from 'vitest'
import { workflowFixture } from '@/local-synthetic/WorkflowPreview'
import { CoastReferenceDetails, InteriorReferenceDetails } from '../AppraisalDetails'

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
