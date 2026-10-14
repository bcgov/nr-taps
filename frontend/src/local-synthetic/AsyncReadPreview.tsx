import { Button } from '@carbon/react'
import { useState } from 'react'
import { EcasInboxReadPage, GasSearchReadPage } from '@/components/appraisal/ReadWorkflowPages'
import { ReadApiError } from '@/service/read-service'
import { workflowFixture as fixture } from './WorkflowPreview'
import type { EcasRelatedApis } from '@/components/appraisal/EcasReferenceSections'
import { syntheticReadApi } from './synthetic-read-api'
import { syntheticGasAuditApi } from './synthetic-gas-audit-api'
import type { AuditEvent, AuditFieldChange } from '@/contracts/audit'
import type { EcasAttachment } from '@/contracts/attachments'

// Fictional metadata only. Full first pages and a final row exercise existing pagination.
const syntheticFileName = `SYNTHETIC_${'LONGFILENAME'.repeat(18)}.pdf`
const syntheticPreviousValue = `SYNTHETIC_PREVIOUS_${'A'.repeat(200)}`
const syntheticChangedValue = `SYNTHETIC_CHANGED_${'B'.repeat(200)}`
const auditEvents: AuditEvent[] = Array.from({ length: 101 }, (_, index) => ({
  eventId: `SYNTHETIC-EVENT-${index + 1}`,
  userId: 'SYNTHETIC-USER',
  eventDate: '2026-10-01T09:00:00',
  action: { code: 'SYN', description: 'Synthetic metadata update' },
  sentToUserId: null,
  submittedFileId: 'SYNTHETIC-FILE-1',
  fileName: index === 0 ? syntheticFileName : `SYNTHETIC-document-${index + 1}.pdf`,
  commentPreview: 'Synthetic audit comment for local UI review.',
  hasMoreComment: false,
  commentsSuppressed: false,
}))
const auditChanges: AuditFieldChange[] = Array.from({ length: 101 }, (_, index) => ({
  detailId: `SYNTHETIC-CHANGE-${index + 1}`,
  userId: 'SYNTHETIC-USER',
  eventDate: '2026-10-01T09:00:00',
  businessIdentifier: `SYNTHETIC-RECORD-${index + 1}`,
  tableName: 'SYNTHETIC_PREVIEW',
  columnName: `SYNTHETIC_FIELD_${index + 1}`,
  previousValue: index === 0 ? syntheticPreviousValue : 'Synthetic previous value',
  changedValue: index === 0 ? syntheticChangedValue : 'Synthetic changed value',
  previousValueTruncated: false,
  changedValueTruncated: false,
}))
const attachments: EcasAttachment[] = Array.from({ length: 51 }, (_, index) => ({
  documentId: `SYNTHETIC-DOCUMENT-${index + 1}`,
  documentType: { code: 'SYN', description: 'Synthetic supporting document' },
  transmissionTypeCode: 'E',
  fileName: index === 0 ? syntheticFileName : `SYNTHETIC-document-${index + 1}.pdf`,
  description: `Synthetic document ${index + 1} for local UI review.`,
  revisionCount: 1,
  createdAt: '2026-10-01T09:00:00',
  updatedAt: '2026-10-02T10:30:00',
}))

export const syntheticRelatedApis: EcasRelatedApis = {
  audit: {
    history: async (ecasId, page, signal) => {
      signal.throwIfAborted()
      return {
        ecasId,
        page,
        total: auditEvents.length,
        items: auditEvents.slice(page * 100, (page + 1) * 100),
      }
    },
    details: async (ecasId, eventId, page, signal) => {
      signal.throwIfAborted()
      const event = auditEvents.find((item) => item.eventId === eventId)
      if (!event) throw new ReadApiError(404)
      return {
        ecasId,
        event,
        comment: 'Synthetic audit comment for local UI review.',
        commentTruncated: false,
        page,
        total: auditChanges.length,
        items: auditChanges.slice(page * 100, (page + 1) * 100),
      }
    },
  },
  attachments: {
    inventory: async (ecasId, page, signal) => {
      signal.throwIfAborted()
      return {
        ecasId,
        page,
        total: attachments.length,
        items: attachments.slice(page * 50, (page + 1) * 50),
        size: 50,
        appraisalMethod: ecasId === fixture.ecasInboxItem.ecasId ? 'I' : 'C',
      }
    },
  },
}

export function SyntheticEcasInboxPage() {
  return (
    <EcasInboxReadPage
      api={syntheticReadApi}
      relatedApis={syntheticRelatedApis}
      gasAuditApi={syntheticGasAuditApi}
    />
  )
}

export function SyntheticGasSearchPage() {
  return <GasSearchReadPage api={syntheticReadApi} gasAuditApi={syntheticGasAuditApi} />
}

export default function AsyncReadPreview() {
  const [module, setModule] = useState<'ecas' | 'gas'>('ecas')
  return (
    <>
      <div className="taps-actions taps-preview-intro">
        <p>
          Synthetic asynchronous read preview. Uses the production page components with test
          fixtures; no database or sign-in requests. In GAS search, licence X99995 and timber mark
          ZZ9995 open fictional non-appraised and historic worksheets with stored rates, species and
          selected add-ons. ECAS references include synthetic audit and attachment pages.
        </p>
        <Button
          kind="tertiary"
          size="md"
          onClick={() => setModule(module === 'ecas' ? 'gas' : 'ecas')}
        >
          {module === 'ecas' ? 'GAS appraisal search' : 'ECAS inbox search'}
        </Button>
      </div>
      {module === 'ecas' ? <SyntheticEcasInboxPage /> : <SyntheticGasSearchPage />}
    </>
  )
}
