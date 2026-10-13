import { useCallback } from 'react'
import { Button, InlineLoading } from '@carbon/react'
import { ReadApiError, readApi, type ReadApi } from '@/service/read-service'
import AppNotification from '../AppNotification'
import { displayCode } from './AppraisalResults'
import useReadResource from './useReadResource'
import useReadSessionFailure from './useReadSessionFailure'

export default function GasFtaContext({
  timberMark,
  api = readApi,
}: {
  timberMark: string | null
  api?: Pick<ReadApi, 'licenceInformation'>
}) {
  const mark = timberMark?.trim().toUpperCase() || null
  const load = useCallback(
    async (signal: AbortSignal) => {
      const result = await api.licenceInformation('', mark!, signal)
      if (result && result.timberMark !== mark) throw new ReadApiError(503)
      return result
    },
    [api, mark],
  )
  const context = useReadResource(mark ? load : null)
  useReadSessionFailure(context.error)
  return (
    <section aria-label="Worksheet FTA information">
      <h3>FTA information</h3>
      <dl className="taps-field-grid">
        <div>
          <dt>Timber mark</dt>
          <dd>{mark ?? '—'}</dd>
        </div>
        <div>
          <dt>FTA Status</dt>
          <dd>{displayCode(context.value?.markStatus ?? null)}</dd>
        </div>
        <div>
          <dt>Cruise based</dt>
          <dd>
            {context.value?.cruiseBased === true
              ? 'Yes'
              : context.value?.cruiseBased === false
                ? 'No'
                : '—'}
          </dd>
        </div>
      </dl>
      {!mark && <p>No primary timber mark is recorded for this worksheet.</p>}
      {context.loading && <InlineLoading description="Loading FTA information…" />}
      {context.value === null && <p>No FTA information is available for this timber mark.</p>}
      {context.error && (
        <>
          <AppNotification
            kind="error"
            title="FTA information unavailable"
            subtitle={context.error.message}
          />
          <Button kind="tertiary" size="md" onClick={context.retry}>
            Retry FTA information
          </Button>
        </>
      )}
    </section>
  )
}
