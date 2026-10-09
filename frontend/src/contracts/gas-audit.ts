export type GasAuditEvent = {
  eventId: string
  rateId: string | null
  userId: string | null
  eventDate: string
  attribute: string
  value: string | null
  comment: string | null
}

export type GasAuditHistoryPage = {
  key: { type: 'NON_APPRAISED'; worksheetId: string }
  items: GasAuditEvent[]
  total: number
  page: number
  size: 10
}
