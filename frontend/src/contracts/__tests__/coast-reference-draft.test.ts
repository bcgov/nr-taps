import { expect, test } from 'vitest'
import { validateCoastAppraisalDates } from '../coast-reference-draft'

const validate = (effectiveDate: string, expiryDate = '', category: string | null = 'N') =>
  validateCoastAppraisalDates({ effectiveDate, expiryDate }, { appraisalCategoryCode: category })

test('blank optional dates remain allowed for new appraisals but effective is required for reappraisals', () => {
  expect(validate('')).toEqual({})
  expect(validate('', '', 'R')).toHaveProperty('effectiveDate')
  expect(validate('2026-10-02', '', 'R')).toEqual({})
})

test.each([
  '2002-03-31',
  '2026-02-29',
  '2026-04-31',
  '2026-13-01',
  '0000-01-01',
  '26-10-02',
  '2026-10-02T00:00:00Z',
])('rejects invalid or pre-Coast-boundary effective date %s', (date) =>
  expect(validate(date)).toHaveProperty('effectiveDate'),
)

test.each(['2002-04-01', '2024-02-29', '2400-02-29', '9999-12-31'])(
  'accepts real dates at the inclusive lower boundary and leap/year boundaries: %s',
  (date) => expect(validate(date)).toEqual({}),
)

test('expiry requires effective, keeps equality valid, and rejects reversed or malformed dates', () => {
  expect(validate('', '2026-10-02')).toHaveProperty('expiryDate')
  expect(validate('2026-10-02', '2026-10-02')).toEqual({})
  expect(validate('2026-10-02', '2026-10-01')).toHaveProperty('expiryDate')
  expect(validate('2026-10-02', '2100-02-29')).toHaveProperty('expiryDate')
  expect(validate('2026-10-02', '2028-02-29')).toEqual({})
})

test('checks trimmed values without mutating the draft or interpreting dates as UTC instants', () => {
  const draft = { effectiveDate: ' 2026-10-02 ', expiryDate: ' 2026-10-02 ' }
  expect(validateCoastAppraisalDates(draft, { appraisalCategoryCode: 'N' })).toEqual({})
  expect(draft).toEqual({ effectiveDate: ' 2026-10-02 ', expiryDate: ' 2026-10-02 ' })
})
