import type { AppraisalMethod, CodeOption } from './appraisal'

export interface EcasAttachment {
  documentId: string
  documentType: CodeOption
  transmissionTypeCode: string | null
  fileName: string | null
  description: string | null
  revisionCount: number | null
  createdAt: string | null
  updatedAt: string | null
}

export interface EcasAttachmentPage {
  ecasId: string
  appraisalMethod: AppraisalMethod
  items: EcasAttachment[]
  total: number
  page: number
  size: 50
}
