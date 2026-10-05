import {
  Accordion,
  AccordionItem,
  FilterableMultiSelect,
  Select,
  SelectItem,
  TextInput,
} from '@carbon/react'
import type { CodeOption, EcasLookups, EcasSearchFilters } from '@/contracts/appraisal'
import { additionalFilterErrors } from './ecas-filter-validation'

export default function EcasAdditionalFilters({
  draft,
  onChange,
  lookups,
  lookupUnavailable,
}: {
  draft: EcasSearchFilters
  onChange: (draft: EcasSearchFilters) => void
  lookups?: EcasLookups
  lookupUnavailable: boolean
}) {
  const errors = additionalFilterErrors(draft)
  const set = <K extends keyof EcasSearchFilters>(key: K, value: EcasSearchFilters[K]) =>
    onChange({ ...draft, [key]: value })
  const organizations = (lookups?.organizations ?? [])
    .filter((item) => item.code)
    .map((item) => ({ id: item.code!, text: item.description ?? item.code! }))
  const selectedOrganizations = (draft.orgUnitNumbers ?? []).map(
    (code) => organizations.find((item) => item.id === code) ?? { id: code, text: code },
  )
  const optionLabel = (item: CodeOption & { active: boolean }) =>
    `${item.code} — ${item.description ?? item.code}${item.active ? '' : ' (inactive)'}`
  const codeFields = [
    {
      field: 'appraisalCategoryCode',
      label: 'Appraisal type',
      items: lookups?.appraisalCategories,
    },
    {
      field: 'reappraisalReasonCode',
      label: 'Reappraisal reason',
      items: lookups?.reappraisalReasons,
    },
    { field: 'fileTypeCode', label: 'File type', items: lookups?.fileTypes },
  ] as const
  return (
    <Accordion className="taps-additional-filters">
      <AccordionItem title="Additional filters" open={Object.keys(errors).length > 0 || undefined}>
        <div className="taps-filter-grid">
          <TextInput
            id="ecas-cutting-permit"
            labelText="Cutting permit"
            maxLength={3}
            value={draft.cuttingPermit ?? ''}
            invalid={Boolean(errors.cuttingPermit)}
            invalidText={errors.cuttingPermit}
            onChange={(event) => set('cuttingPermit', event.target.value)}
          />
          <TextInput
            id="ecas-client-number"
            labelText="Client number"
            maxLength={8}
            inputMode="numeric"
            value={draft.clientNumber ?? ''}
            invalid={Boolean(errors.clientNumber)}
            invalidText={errors.clientNumber}
            onChange={(event) =>
              onChange({ ...draft, clientNumber: event.target.value, clientLocationCode: '' })
            }
          />
          <TextInput
            id="ecas-client-location"
            labelText="Client location"
            maxLength={2}
            value={draft.clientLocationCode ?? ''}
            invalid={Boolean(errors.clientLocationCode)}
            invalidText={errors.clientLocationCode}
            onChange={(event) => set('clientLocationCode', event.target.value)}
          />
          <FilterableMultiSelect<{ id: string; text: string }>
            id="ecas-organizations"
            titleText="Organization units"
            placeholder="All permitted organizations"
            items={organizations}
            selectedItems={selectedOrganizations}
            itemToString={(item) => item?.text ?? ''}
            disabled={lookupUnavailable}
            onChange={({ selectedItems }) =>
              set(
                'orgUnitNumbers',
                selectedItems.map((item) => item.id),
              )
            }
          />
          {codeFields.map(({ field, label, items }) => (
            <Select
              id={`ecas-${field}`}
              key={field}
              labelText={label}
              value={draft[field] ?? ''}
              disabled={lookupUnavailable}
              onChange={(event) => set(field, event.target.value)}
            >
              <SelectItem value="" text="All" />
              {draft[field] && !items?.some((item) => item.code === draft[field]) && (
                <SelectItem value={draft[field]} text={draft[field]} />
              )}
              {(items ?? [])
                .filter((item) => item.code)
                .map((item) => (
                  <SelectItem key={item.code!} value={item.code!} text={optionLabel(item)} />
                ))}
            </Select>
          ))}
          {(
            [
              { field: 'bctsFunded', label: 'BCTS funded' },
              { field: 'certified', label: 'Certification statement' },
            ] as const
          ).map(({ field, label }) => (
            <Select
              id={`ecas-${field}`}
              key={field}
              labelText={label}
              value={draft[field] == null ? '' : draft[field] ? 'Y' : 'N'}
              onChange={(event) =>
                set(field, event.target.value === '' ? null : event.target.value === 'Y')
              }
            >
              <SelectItem value="" text="All" />
              <SelectItem value="Y" text="Yes" />
              <SelectItem value="N" text="No" />
            </Select>
          ))}
          <TextInput
            id="ecas-management-type"
            labelText="Management unit type"
            maxLength={1}
            value={draft.managementUnitType ?? ''}
            invalid={Boolean(errors.managementUnitType)}
            invalidText={errors.managementUnitType}
            onChange={(event) =>
              onChange({ ...draft, managementUnitType: event.target.value, managementUnitId: '' })
            }
          />
          <TextInput
            id="ecas-management-id"
            labelText="Management unit ID"
            maxLength={4}
            value={draft.managementUnitId ?? ''}
            invalid={Boolean(errors.managementUnitId)}
            invalidText={errors.managementUnitId}
            onChange={(event) => set('managementUnitId', event.target.value)}
          />
          <TextInput
            id="ecas-worked-by"
            labelText="Worked on by user ID"
            maxLength={30}
            value={draft.workedOnByUserId ?? ''}
            helperText="Use the user ID recorded in ECAS history."
            onChange={(event) => set('workedOnByUserId', event.target.value)}
          />
          <p className="taps-filter-help">
            Client, location and management-unit identifiers filter stored submissions. Organization
            choices follow your ministry grants.
          </p>
        </div>
      </AccordionItem>
    </Accordion>
  )
}
