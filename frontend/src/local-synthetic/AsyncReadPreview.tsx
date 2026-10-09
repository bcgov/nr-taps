import { Button } from '@carbon/react'
import { useState } from 'react'
import { EcasInboxReadPage, GasSearchReadPage } from '@/components/appraisal/ReadWorkflowPages'
import { ReadApiError } from '@/service/read-service'
import { workflowFixture as fixture } from './WorkflowPreview'
import type { EcasRelatedApis } from '@/components/appraisal/EcasReferenceSections'
import { syntheticReadApi } from './synthetic-read-api'

const syntheticRelatedApis: EcasRelatedApis = {
  audit: {
    history: async (ecasId, page) => ({ ecasId, page, total: 0, items: [] }),
    details: async () => {
      throw new ReadApiError(404)
    },
  },
  attachments: {
    inventory: async (ecasId, page) => ({
      ecasId,
      page,
      total: 0,
      items: [],
      size: 50,
      appraisalMethod: ecasId === fixture.ecasInboxItem.ecasId ? 'I' : 'C',
    }),
  },
}

export default function AsyncReadPreview() {
  const [module, setModule] = useState<'ecas' | 'gas'>('ecas')
  return (
    <>
      <div className="taps-actions taps-preview-intro">
        <p>
          Synthetic asynchronous read preview. Uses the production page components with test
          fixtures; no database or sign-in requests. In GAS search, licence X99995 and timber mark
          ZZ9995 open a fictional non-appraised worksheet with labels and rate totals.
        </p>
        <Button
          kind="tertiary"
          size="md"
          onClick={() => setModule(module === 'ecas' ? 'gas' : 'ecas')}
        >
          {module === 'ecas' ? 'GAS appraisal search' : 'ECAS inbox search'}
        </Button>
      </div>
      {module === 'ecas' ? (
        <EcasInboxReadPage api={syntheticReadApi} relatedApis={syntheticRelatedApis} />
      ) : (
        <GasSearchReadPage api={syntheticReadApi} />
      )}
    </>
  )
}
