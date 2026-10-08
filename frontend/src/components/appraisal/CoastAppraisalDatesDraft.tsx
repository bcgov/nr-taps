import { Button, TextInput } from '@carbon/react'
import { useId, useRef, useState } from 'react'
import type { CoastReference } from '@/contracts/appraisal'
import {
  coastDatesFromReference,
  validateCoastAppraisalDates,
  type CoastAppraisalDates,
} from '@/contracts/coast-reference-draft'
import AppNotification from '../AppNotification'
import './coast-reference-draft.scss'

export default function CoastAppraisalDatesDraft({ reference }: { reference: CoastReference }) {
  // Start a fresh draft when the record, revision or source values change.
  const identity = JSON.stringify([
    reference.header.ecasId,
    reference.header.revisionCount,
    reference.header.appraisalCategoryCode,
    reference.header.effectiveDate,
    reference.header.expiryDate,
  ])
  return <DateFields key={identity} reference={reference} />
}

function DateFields({ reference }: { reference: CoastReference }) {
  const id = useId()
  const original = coastDatesFromReference(reference)
  const [draft, setDraft] = useState(original)
  const [checked, setChecked] = useState(false)
  const effectiveRef = useRef<HTMLInputElement>(null)
  const expiryRef = useRef<HTMLInputElement>(null)
  const errors = checked ? validateCoastAppraisalDates(draft, reference.header) : {}
  const invalid = Object.keys(errors).length > 0
  const dirty =
    draft.effectiveDate !== original.effectiveDate || draft.expiryDate !== original.expiryDate
  const update = (field: keyof CoastAppraisalDates, value: string) => {
    setDraft((current) => ({ ...current, [field]: value }))
    setChecked(false)
  }
  return (
    <section className="taps-reference-draft" aria-labelledby={`${id}-heading`}>
      <h3 id={`${id}-heading`}>Coast appraisal dates draft</h3>
      <p id={`${id}-scope`}>
        Changes are not saved. Leaving this view or refreshing the record discards them. Only the
        date rules are checked here.
      </p>
      <p>
        ECAS {reference.header.ecasId} · Submission revision{' '}
        {reference.header.revisionCount ?? 'not available'} · Appraisal type{' '}
        {reference.header.appraisalCategoryCode === 'R'
          ? 'Reappraisal'
          : (reference.header.appraisalCategoryCode ?? 'not available')}
      </p>
      <form
        aria-labelledby={`${id}-heading`}
        aria-describedby={`${id}-scope`}
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          const nextErrors = validateCoastAppraisalDates(draft, reference.header)
          setChecked(true)
          if (nextErrors.effectiveDate) effectiveRef.current?.focus()
          else if (nextErrors.expiryDate) expiryRef.current?.focus()
        }}
      >
        {/* Contained ISO inputs keep every control inside the drawer's mobile focus boundary. */}
        <div className="taps-reference-draft__fields">
          <TextInput
            ref={effectiveRef}
            id={`${id}-effective-date`}
            labelText="Appraisal effective date"
            helperText="YYYY-MM-DD; on or after 2002-04-01. Required for reappraisals."
            placeholder="YYYY-MM-DD"
            maxLength={10}
            autoComplete="off"
            value={draft.effectiveDate}
            invalid={Boolean(errors.effectiveDate)}
            invalidText={errors.effectiveDate}
            onChange={(event) => update('effectiveDate', event.target.value)}
          />
          <TextInput
            ref={expiryRef}
            id={`${id}-expiry-date`}
            labelText="Appraisal expiry date"
            helperText="YYYY-MM-DD; optional, on or after the effective date."
            placeholder="YYYY-MM-DD"
            maxLength={10}
            autoComplete="off"
            value={draft.expiryDate}
            invalid={Boolean(errors.expiryDate)}
            invalidText={errors.expiryDate}
            onChange={(event) => update('expiryDate', event.target.value)}
          />
        </div>
        {checked && invalid && (
          <AppNotification
            kind="error"
            title="Check the appraisal dates"
            subtitle="Correct the highlighted fields. No changes have been saved."
          />
        )}
        <p role="status" aria-live="polite">
          {checked && !invalid
            ? 'Date fields pass the local checks. Changes are not saved.'
            : dirty
              ? 'Unsaved date changes.'
              : 'No date changes. This is an in-memory draft.'}
        </p>
        <div className="taps-actions">
          <Button type="submit" size="md">
            Check dates
          </Button>
          <Button
            type="button"
            kind="tertiary"
            size="md"
            disabled={!dirty && !checked}
            onClick={() => {
              setDraft(original)
              setChecked(false)
              effectiveRef.current?.focus()
            }}
          >
            Reset draft
          </Button>
        </div>
      </form>
    </section>
  )
}
