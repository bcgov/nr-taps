import type { CoastReference, ReferenceHeader } from './appraisal'
import { isValidIsoDate } from '@/components/iso-date'

// Form state only; nothing here is saved yet.
export type CoastAppraisalDates = { effectiveDate: string; expiryDate: string }
export type CoastDateErrors = Partial<Record<keyof CoastAppraisalDates, string>>
export type CoastDateContext = Pick<ReferenceHeader, 'appraisalCategoryCode'>

export function coastDatesFromReference(reference: CoastReference): CoastAppraisalDates {
  return {
    effectiveDate: reference.header.effectiveDate ?? '',
    expiryDate: reference.header.expiryDate ?? '',
  }
}

/** ECAS30 Save Dates field rules; database/workflow checks remain separate. */
export function validateCoastAppraisalDates(
  draft: CoastAppraisalDates,
  context: CoastDateContext,
): CoastDateErrors {
  const effective = draft.effectiveDate.trim()
  const expiry = draft.expiryDate.trim()
  const errors: CoastDateErrors = {}
  if (effective && !isValidIsoDate(effective)) {
    errors.effectiveDate = 'Enter a valid effective date in YYYY-MM-DD format.'
  } else if (!effective && context.appraisalCategoryCode === 'R') {
    errors.effectiveDate = 'An effective date is required for a reappraisal.'
  } else if (effective && effective < '2002-04-01') {
    errors.effectiveDate = 'The Coast effective date must be on or after 2002-04-01.'
  }
  if (expiry && !isValidIsoDate(expiry)) {
    errors.expiryDate = 'Enter a valid expiry date in YYYY-MM-DD format.'
  } else if (expiry && !effective) {
    errors.expiryDate = 'Enter an effective date before entering an expiry date.'
  } else if (expiry && effective && !errors.effectiveDate && expiry < effective) {
    errors.expiryDate = 'The expiry date must be on or after the effective date.'
  }
  return errors
}
