import { Accordion, AccordionItem } from '@carbon/react'
import { useState } from 'react'
import type { CoastReference, InteriorReference } from '@/contracts/appraisal'
import type { EcasAuditApi } from '@/service/audit-service'
import type { AttachmentApi } from '@/service/attachment-service'
import { useAuth } from '@/context/auth/AuthContext'
import { Capability } from '@/context/auth/capabilities'
import EcasAuditDetails from './EcasAuditDetails'
import EcasAttachmentDetails from './EcasAttachmentDetails'
import CoastAppraisalDatesDraft from './CoastAppraisalDatesDraft'

export type EcasRelatedApis = { audit?: EcasAuditApi; attachments?: AttachmentApi }

export default function EcasReferenceSections({
  reference,
  apis,
}: {
  reference: CoastReference | InteriorReference
  apis?: EcasRelatedApis
}) {
  const { can } = useAuth()
  const [auditOpen, setAuditOpen] = useState(false)
  const [attachmentsOpen, setAttachmentsOpen] = useState(false)
  return (
    <Accordion className="taps-reference-sections">
      <AccordionItem title="Audit history" onHeadingClick={({ isOpen }) => setAuditOpen(isOpen)}>
        {auditOpen && <EcasAuditDetails ecasId={reference.header.ecasId} api={apis?.audit} />}
      </AccordionItem>
      <AccordionItem
        title="Attachment inventory"
        onHeadingClick={({ isOpen }) => setAttachmentsOpen(isOpen)}
      >
        {attachmentsOpen && (
          <EcasAttachmentDetails ecasId={reference.header.ecasId} api={apis?.attachments} />
        )}
      </AccordionItem>
      {'timberMarks' in reference && can(Capability.EcasSubmissionEdit) && (
        <AccordionItem title="Appraisal dates draft">
          <CoastAppraisalDatesDraft reference={reference} />
        </AccordionItem>
      )}
    </Accordion>
  )
}
