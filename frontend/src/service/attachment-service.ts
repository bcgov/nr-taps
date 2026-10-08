import type { EcasAttachmentPage } from '@/contracts/attachments'
import { readRequest } from './read-service'

export interface AttachmentApi {
  inventory(ecasId: string, page: number, signal: AbortSignal): Promise<EcasAttachmentPage>
}

export const attachmentApi: AttachmentApi = {
  inventory: (ecasId, page, signal) =>
    readRequest(`/api/ecas/${encodeURIComponent(ecasId)}/attachments?page=${page}`, signal),
}
