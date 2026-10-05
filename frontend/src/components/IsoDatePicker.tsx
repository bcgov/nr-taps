import { DatePicker, DatePickerInput } from '@carbon/react'
import { useEffect, useRef, useState } from 'react'
import { isValidIsoDate, parseIsoDate } from './iso-date'

// Based on the nr-lexis ISO date picker; keeps invalid typed text visible for validation.
export default function IsoDatePicker({
  id,
  labelText,
  value,
  invalid = false,
  invalidText,
  onChange,
}: {
  id: string
  labelText: string
  value: string
  invalid?: boolean
  invalidText?: string
  onChange: (value: string) => void
}) {
  const latestRef = useRef(value)
  useEffect(() => {
    latestRef.current = value
  }, [value])
  // Carbon clears its input whenever `value` becomes empty, so keep the last valid date while a
  // typed edit is incomplete. An empty value from the parent still resets the calendar.
  const [calendarValue, setCalendarValue] = useState(() => (isValidIsoDate(value) ? value : ''))
  const nextCalendarValue = isValidIsoDate(value) ? value : calendarValue
  if (nextCalendarValue !== calendarValue) setCalendarValue(nextCalendarValue)
  return (
    <DatePicker
      datePickerType="single"
      dateFormat="Y-m-d"
      allowInput
      parseDate={parseIsoDate}
      value={calendarValue || undefined}
      onChange={(_selected, text) => {
        if (!text && latestRef.current && !isValidIsoDate(latestRef.current)) return
        if (text !== value) {
          latestRef.current = text
          onChange(text)
        }
      }}
    >
      <DatePickerInput
        id={id}
        labelText={labelText}
        placeholder="YYYY-MM-DD"
        pattern="[0-9]{4}-[0-9]{2}-[0-9]{2}"
        invalid={invalid}
        invalidText={invalidText}
        data-1p-ignore="true"
        data-lpignore="true"
        onChange={(event) => {
          latestRef.current = event.target.value
          if (event.target.value !== value) onChange(event.target.value)
        }}
        onBlur={(event) => {
          const input = event.currentTarget
          const text = latestRef.current
          if (text && !isValidIsoDate(text))
            requestAnimationFrame(() => {
              if (latestRef.current === text) input.value = text
            })
        }}
      />
    </DatePicker>
  )
}
