import { expect, test } from 'vitest'
import { formatDisplayDate } from '../iso-date'

test.each([
  ['2026-09-28', 'Sep 28, 2026'],
  ['2026-01-01', 'Jan 1, 2026'],
  ['2026-12-31', 'Dec 31, 2026'],
  ['2024-02-29', 'Feb 29, 2024'],
])('formats calendar date %s without changing its day', (value, expected) => {
  expect(formatDisplayDate(value)).toBe(expected)
})

test.each([null, undefined, ''])('shows absent dates as an em dash: %s', (value) => {
  expect(formatDisplayDate(value)).toBe('—')
})

test.each(['2026-02-29', '2026-13-01', 'unknown', '2026-09-28T00:00:00Z'])(
  'preserves invalid dates and timestamp text: %s',
  (value) => {
    expect(formatDisplayDate(value)).toBe(value)
  },
)
