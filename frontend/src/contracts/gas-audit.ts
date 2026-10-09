export type GasAuditEvent = {
  eventId: string
  rateId: string | null
  userId: string | null
  eventDate: string
  attribute: string
  value: string | null
  comment: string | null
}

export type GasAuditWorksheetKey = { type: 'APPRAISED' | 'NON_APPRAISED'; worksheetId: string }

export type GasAuditHistoryPage = {
  key: GasAuditWorksheetKey
  items: GasAuditEvent[]
  total: number
  page: number
  size: 10
}
