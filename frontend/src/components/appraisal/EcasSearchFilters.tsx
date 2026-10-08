import {
  Button,
  FilterableMultiSelect,
  InlineLoading,
  Select,
  SelectItem,
  TextInput,
} from '@carbon/react'
import type {
  EcasDateType,
  EcasLookups,
  EcasSearchFilters as Filters,
  EcasSortField,
} from '@/contracts/appraisal'
import SearchFilters from '../SearchFilters'
import IsoDatePicker from '../IsoDatePicker'
import { isValidIsoDate } from '../iso-date'
import AppNotification from '../AppNotification'
import EcasAdditionalFilters from './EcasAdditionalFilters'
import { additionalFilterErrors, primaryFilterErrors } from './ecas-filter-validation'

const dateOptions: { id: EcasDateType; text: string }[] = [
  { id: 'EFFCTV', text: 'Effective date' },
  { id: 'EXPRY', text: 'Expiry date' },
  { id: 'NTRY', text: 'Created date' },
  { id: 'LTMD', text: 'Last modified date' },
  { id: 'STTS', text: 'Status change date' },
  { id: 'FCED', text: 'FTA cutting permit expiry' },
]
const sorts: { id: EcasSortField; text: string }[] = [
  { id: 'ECAS_ID', text: 'ECAS ID' },
  { id: 'TIMBER_MARK', text: 'Timber mark' },
  { id: 'LICENCE', text: 'Licence' },
  { id: 'CLIENT_NAME', text: 'Client name' },
  { id: 'STATUS', text: 'Status' },
  { id: 'STATUS_CHANGE_DATE', text: 'Status change date' },
  { id: 'APPRAISAL_TYPE', text: 'Appraisal type' },
  { id: 'EFFECTIVE_DATE', text: 'Effective date' },
  { id: 'EXPIRY_DATE', text: 'Expiry date' },
  { id: 'SUBMITTED_DATE', text: 'Submitted date' },
  { id: 'DISTRICT_RECEIVED_DATE', text: 'District received date' },
  { id: 'SENT_TO_REGION_DATE', text: 'Sent to region date' },
  { id: 'UPDATE_DATE', text: 'Last modified date' },
]
const label = (option: { text: string } | null) => option?.text ?? ''

export default function EcasSearchFilters({
  draft,
  onChange,
  onSearch,
  onReset,
  loading,
  lookups,
  lookupLoading,
  lookupError,
  onRetryLookups,
}: {
  draft: Filters
  onChange: (filters: Filters) => void
  onSearch: () => void
  onReset: () => void
  loading: boolean
  lookups?: EcasLookups
  lookupLoading: boolean
  lookupError?: string
  onRetryLookups: () => void
}) {
  const set = <K extends keyof Filters>(key: K, value: Filters[K]) =>
    onChange({ ...draft, [key]: value })
  const from = draft.dateFrom ?? '',
    to = draft.dateTo ?? ''
  const statusFrom = draft.statusDateFrom ?? '',
    statusTo = draft.statusDateTo ?? ''
  const dateTypes = draft.dateTypes ?? [],
    statusCodes = draft.statusCodes ?? []
  const invalidRange = Boolean(from && to && to < from)
  const invalidStatusRange = Boolean(statusFrom && statusTo && statusTo < statusFrom)
  const missingDateType = Boolean((from || to) && !dateTypes.length)
  const incompleteFtaRange = Boolean((from || to) && dateTypes.includes('FCED') && (!from || !to))
  const invalidStatusDates = Boolean(
    (statusFrom || statusTo) && (!statusCodes.length || statusCodes.includes('EE')),
  )
  const invalidDates = [from, to, statusFrom, statusTo].some((value) => !isValidIsoDate(value))
  const errors = primaryFilterErrors(draft)
  const statuses = (lookups?.appraisalStatuses ?? []).map((code) => ({
    id: code.code,
    text: `${code.code} — ${code.description ?? code.code}${code.active ? '' : ' (inactive)'}`,
  }))
  const selectedStatuses = statusCodes.map(
    (code) => statuses.find((item) => item.id === code) ?? { id: code, text: code },
  )
  return (
    <>
      {lookupLoading && <InlineLoading description="Loading search choices…" />}
      {lookupError && (
        <>
          <AppNotification kind="error" title="Search choices unavailable" subtitle={lookupError} />
          <Button kind="tertiary" size="md" onClick={onRetryLookups}>
            Retry search choices
          </Button>
        </>
      )}
      <SearchFilters
        title="Inbox search filters"
        loading={loading}
        onSearch={onSearch}
        onReset={onReset}
        disabled={
          invalidDates ||
          invalidRange ||
          invalidStatusRange ||
          missingDateType ||
          incompleteFtaRange ||
          invalidStatusDates ||
          Object.keys(errors).length > 0 ||
          Object.keys(additionalFilterErrors(draft)).length > 0
        }
      >
        <TextInput
          id="read-ecas-id"
          labelText="ECAS ID"
          maxLength={12}
          value={draft.ecasId}
          onChange={(event) => set('ecasId', event.target.value)}
          invalid={Boolean(errors.ecasId)}
          invalidText={errors.ecasId}
          helperText={
            draft.ecasId.trim()
              ? 'Direct ID lookup takes precedence over method, licence, mark, status and date filters.'
              : undefined
          }
        />
        <TextInput
          id="read-ecas-licence"
          labelText="Licence"
          maxLength={10}
          value={draft.licence}
          onChange={(event) => set('licence', event.target.value)}
          invalid={Boolean(errors.licence)}
          invalidText={errors.licence}
        />
        <TextInput
          id="read-ecas-mark"
          labelText="Timber mark"
          maxLength={6}
          value={draft.timberMark}
          onChange={(event) => set('timberMark', event.target.value)}
        />
        <Select
          id="read-ecas-method"
          labelText="Appraisal method"
          value={draft.appraisalMethod ?? ''}
          onChange={(event) =>
            set('appraisalMethod', event.target.value as Filters['appraisalMethod'])
          }
        >
          <SelectItem value="" text="All methods" />
          <SelectItem value="C" text="Coast" />
          <SelectItem value="I" text="Interior" />
        </Select>
        <FilterableMultiSelect<{ id: string; text: string }>
          id="read-ecas-status"
          titleText="Statuses"
          placeholder="All statuses"
          items={statuses}
          selectedItems={selectedStatuses}
          itemToString={label}
          disabled={lookupLoading || Boolean(lookupError)}
          onChange={({ selectedItems }) =>
            set(
              'statusCodes',
              selectedItems.map((item) => item.id),
            )
          }
        />
        <Select
          id="read-ecas-sort"
          labelText="Sort by"
          value={draft.sortBy ?? 'ECAS_ID'}
          onChange={(event) => set('sortBy', event.target.value as EcasSortField)}
        >
          {sorts.map((item) => (
            <SelectItem key={item.id} value={item.id} text={item.text} />
          ))}
        </Select>
        <Select
          id="read-ecas-direction"
          labelText="Sort direction"
          value={draft.sortDirection ?? 'DESC'}
          onChange={(event) => set('sortDirection', event.target.value as 'ASC' | 'DESC')}
        >
          <SelectItem value="ASC" text="Ascending" />
          <SelectItem value="DESC" text="Descending" />
        </Select>
        <FilterableMultiSelect<{ id: EcasDateType; text: string }>
          id="read-ecas-date-types"
          titleText="Dates to match"
          placeholder="Select date fields"
          items={dateOptions}
          selectedItems={dateOptions.filter((item) => dateTypes.includes(item.id))}
          itemToString={label}
          onChange={({ selectedItems }) =>
            set(
              'dateTypes',
              selectedItems.map((item) => item.id),
            )
          }
          invalid={missingDateType}
          invalidText="Select at least one date field for the range."
        />
        <IsoDatePicker
          id="read-ecas-from"
          labelText="From date"
          value={from}
          onChange={(value) => set('dateFrom', value)}
          invalid={!isValidIsoDate(from) || incompleteFtaRange}
          invalidText={
            incompleteFtaRange
              ? 'FTA expiry requires both dates.'
              : 'Enter a valid YYYY-MM-DD date.'
          }
        />
        <IsoDatePicker
          id="read-ecas-to"
          labelText="To date"
          value={to}
          onChange={(value) => set('dateTo', value)}
          invalid={!isValidIsoDate(to) || invalidRange || incompleteFtaRange}
          invalidText={
            invalidRange
              ? 'To date must be on or after from date.'
              : incompleteFtaRange
                ? 'FTA expiry requires both dates.'
                : 'Enter a valid YYYY-MM-DD date.'
          }
        />
        <IsoDatePicker
          id="read-ecas-status-from"
          labelText="Status action from date"
          value={statusFrom}
          onChange={(value) => set('statusDateFrom', value)}
          invalid={!isValidIsoDate(statusFrom) || invalidStatusDates}
          invalidText={
            invalidStatusDates
              ? 'Choose statuses other than EE to search their action dates.'
              : 'Enter a valid YYYY-MM-DD date.'
          }
        />
        <IsoDatePicker
          id="read-ecas-status-to"
          labelText="Status action to date"
          value={statusTo}
          onChange={(value) => set('statusDateTo', value)}
          invalid={!isValidIsoDate(statusTo) || invalidStatusRange || invalidStatusDates}
          invalidText={
            invalidStatusRange
              ? 'To date must be on or after from date.'
              : invalidStatusDates
                ? 'Choose statuses other than EE to search their action dates.'
                : 'Enter a valid YYYY-MM-DD date.'
          }
        />
        <EcasAdditionalFilters
          draft={draft}
          onChange={onChange}
          lookups={lookups}
          lookupUnavailable={lookupLoading || Boolean(lookupError)}
        />
        <p className="taps-filter-help">
          Each selected date field must fall within the range. Status action dates match recorded
          status changes; without them, statuses match the current status.
        </p>
      </SearchFilters>
    </>
  )
}
