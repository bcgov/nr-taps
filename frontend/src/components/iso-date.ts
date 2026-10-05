// Calendar-only values: never round-trip user-entered dates through UTC.
export function isValidIsoDate(value: string): boolean {
  if (!value) return true
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const [year, month, day] = value.split('-').map(Number)
  if (year < 1 || year > 9999 || month < 1 || month > 12 || day < 1) return false
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
  return day <= [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1]
}

export function parseIsoDate(value: string): Date | false {
  if (!value || !isValidIsoDate(value)) return false
  const [year, month, day] = value.split('-').map(Number)
  const result = new Date(year, month - 1, day)
  if (year < 100) result.setFullYear(year)
  return result
}
