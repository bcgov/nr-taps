import type { EcasSearchFilters } from '@/contracts/appraisal'

// Same rules as the backend LegacyIdentifiers, shown inline instead of as a 400.
export function primaryFilterErrors(draft: EcasSearchFilters) {
  const errors: Partial<Record<'ecasId' | 'licence', string>> = {}
  const ecasId = draft.ecasId.trim()
  if (ecasId && (!/^[0-9]{1,12}$/.test(ecasId) || /^0+$/.test(ecasId)))
    errors.ecasId = 'Enter a positive ECAS ID of up to 12 digits.'
  const licence = draft.licence.trim()
  if (licence && !/^[a-z0-9]{1,10}$/i.test(licence))
    errors.licence = 'Use letters and numbers, up to 10 characters.'
  return errors
}

export function additionalFilterErrors(draft: EcasSearchFilters) {
  const errors: Partial<Record<keyof EcasSearchFilters, string>> = {}
  const alphaNumeric = (
    field: 'cuttingPermit' | 'managementUnitType' | 'managementUnitId',
    maximum: number,
  ) => {
    const text = draft[field]?.trim()
    if (text && (!/^[a-z0-9]+$/i.test(text) || text.length > maximum))
      errors[field] = `Use letters and numbers, up to ${maximum} characters.`
  }
  alphaNumeric('cuttingPermit', 3)
  alphaNumeric('managementUnitType', 1)
  alphaNumeric('managementUnitId', 4)
  if (draft.cuttingPermit?.trim() && !draft.licence.trim())
    errors.cuttingPermit = 'Enter a licence when specifying a cutting permit.'
  if (draft.managementUnitId?.trim() && !draft.managementUnitType?.trim())
    errors.managementUnitId = 'Enter a management unit type with the ID.'
  if (draft.clientNumber?.trim() && !/^[0-9]{1,8}$/.test(draft.clientNumber.trim()))
    errors.clientNumber = 'Enter a numeric client number, up to eight digits.'
  if ((draft.clientLocationCode?.trim().length ?? 0) > 2)
    errors.clientLocationCode = 'Use a location code of up to two characters.'
  if (draft.clientLocationCode?.trim() && !draft.clientNumber?.trim())
    errors.clientLocationCode = 'Enter a client number with the location.'
  return errors
}
