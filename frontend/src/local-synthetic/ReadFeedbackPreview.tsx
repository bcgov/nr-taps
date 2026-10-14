import { Select, SelectItem } from '@carbon/react'
import { useMemo, useState } from 'react'
import { EcasInboxReadPage, GasSearchReadPage } from '@/components/appraisal/ReadWorkflowPages'
import { ReadApiError, type ReadApi } from '@/service/read-service'
import { syntheticRelatedApis } from './AsyncReadPreview'
import { syntheticGasAuditApi } from './synthetic-gas-audit-api'
import { syntheticReadApi } from './synthetic-read-api'

const scenarios = [
  { id: 'normal', label: 'Normal' },
  { id: 'choices', label: 'Search choices fail once' },
  { id: 'results', label: 'Search results fail once' },
  { id: 'details', label: 'Record details fail once' },
  { id: 'fta', label: 'FTA information fails once' },
  { id: 'reappraisal', label: 'Reappraisal date draft (ECAS)' },
] as const
type FeedbackScenario = (typeof scenarios)[number]['id']

function delay(signal: AbortSignal) {
  signal.throwIfAborted()
  return new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', abort)
      resolve()
    }, 400)
    function abort() {
      clearTimeout(timer)
      reject(signal.reason)
    }
    signal.addEventListener('abort', abort, { once: true })
  })
}

// A cancelled request does not consume the failure, including StrictMode's initial cleanup.
export function createFeedbackReadApi(scenario: FeedbackScenario): ReadApi {
  let pendingFailure = true
  const failOnce = async (signal: AbortSignal) => {
    if (!pendingFailure) return
    await delay(signal)
    signal.throwIfAborted()
    if (pendingFailure) {
      pendingFailure = false
      throw new ReadApiError(503)
    }
  }
  const api = { ...syntheticReadApi }
  if (scenario === 'choices') {
    api.ecasLookups = async (signal) => {
      await failOnce(signal)
      return syntheticReadApi.ecasLookups(signal)
    }
    api.marks = async (licence, signal) => {
      await failOnce(signal)
      return syntheticReadApi.marks(licence, signal)
    }
  } else if (scenario === 'results') {
    api.inbox = async (filters, page, signal) => {
      await failOnce(signal)
      return syntheticReadApi.inbox(filters, page, signal)
    }
    api.worksheets = async (licence, mark, page, signal) => {
      await failOnce(signal)
      return syntheticReadApi.worksheets(licence, mark, page, signal)
    }
  } else if (scenario === 'details') {
    api.reference = async (method, id, signal) => {
      await failOnce(signal)
      return syntheticReadApi.reference(method, id, signal)
    }
    api.relatedSummary = async (id, signal) => {
      await failOnce(signal)
      return syntheticReadApi.relatedSummary(id, signal)
    }
    api.worksheet = async (key, signal) => {
      await failOnce(signal)
      return syntheticReadApi.worksheet(key, signal)
    }
  } else if (scenario === 'fta') {
    api.licenceInformation = async (licence, mark, signal) => {
      await failOnce(signal)
      return syntheticReadApi.licenceInformation(licence, mark, signal)
    }
  } else if (scenario === 'reappraisal') {
    api.reference = async (method, id, signal) => {
      const reference = await syntheticReadApi.reference(method, id, signal)
      return 'timberMarks' in reference
        ? {
            ...reference,
            header: {
              ...reference.header,
              appraisalCategoryCode: 'R',
              effectiveDate: null,
              expiryDate: null,
            },
          }
        : reference
    }
  }
  return api
}

function FeedbackPage({
  application,
  scenario,
}: {
  application: 'ecas' | 'gas'
  scenario: FeedbackScenario
}) {
  const api = useMemo(() => createFeedbackReadApi(scenario), [scenario])
  return application === 'ecas' ? (
    <EcasInboxReadPage
      api={api}
      relatedApis={syntheticRelatedApis}
      gasAuditApi={syntheticGasAuditApi}
    />
  ) : (
    <GasSearchReadPage api={api} gasAuditApi={syntheticGasAuditApi} />
  )
}

export default function ReadFeedbackPreview() {
  const [application, setApplication] = useState<'ecas' | 'gas'>('ecas')
  const [scenario, setScenario] = useState<FeedbackScenario>('normal')
  return (
    <>
      <div className="taps-preview-intro">
        <p>
          Synthetic read feedback. Choose the synthetic staff session. Each failure happens once;
          use the page's retry action to recover. Changing either selector resets the page. No
          service requests are made.
        </p>
        <p>
          Press Search for result feedback, then open a row for details. For GAS search choices,
          enter licence X99998. For FTA feedback, search GAS timber mark ZZ9996, or open a worksheet
          from either application.
        </p>
        {scenario === 'reappraisal' && (
          <p>
            In ECAS, open a Coast reference and expand Appraisal dates draft to review required
            fields and local date validation. These synthetic draft changes are not saved.
          </p>
        )}
        <div className="taps-preview-toolbar">
          <Select
            id="feedback-application"
            labelText="Synthetic application"
            value={application}
            onChange={(event) => setApplication(event.target.value as 'ecas' | 'gas')}
          >
            <SelectItem value="ecas" text="ECAS" />
            <SelectItem value="gas" text="GAS" />
          </Select>
          <Select
            id="feedback-scenario"
            labelText="Synthetic read scenario"
            value={scenario}
            onChange={(event) => setScenario(event.target.value as FeedbackScenario)}
          >
            {scenarios.map((item) => (
              <SelectItem key={item.id} value={item.id} text={item.label} />
            ))}
          </Select>
        </div>
      </div>
      <FeedbackPage
        key={`${application}:${scenario}`}
        application={application}
        scenario={scenario}
      />
    </>
  )
}
