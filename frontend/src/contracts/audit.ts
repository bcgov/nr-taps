import type { CodeOption } from './appraisal'

export type AuditEvent = {
  eventId: string
  userId: string | null
  eventDate: string | null
  action: CodeOption
  sentToUserId: string | null
  submittedFileId: string | null
  fileName: string | null
  commentPreview: string | null
  hasMoreComment: boolean
  commentsSuppressed: boolean
}

export type AuditHistoryPage = { ecasId: string; items: AuditEvent[]; total: number; page: number }

export type AuditFieldChange = {
  detailId: string
  userId: string | null
  eventDate: string | null
  businessIdentifier: string | null
  tableName: string | null
  columnName: string | null
  previousValue: string | null
  changedValue: string | null
  previousValueTruncated: boolean
  changedValueTruncated: boolean
}

export type AuditDetailPage = {
  ecasId: string
  event: AuditEvent
  comment: string | null
  commentTruncated: boolean
  items: AuditFieldChange[]
  total: number
  page: number
}
