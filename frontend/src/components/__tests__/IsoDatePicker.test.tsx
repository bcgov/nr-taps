import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { expect, test } from 'vitest'
import IsoDatePicker from '../IsoDatePicker'

let reset: () => void = () => {}

function StatefulDatePicker({ initial }: { initial: string }) {
  const [value, setValue] = useState(initial)
  reset = () => setValue('')
  return (
    <>
      <IsoDatePicker id="date" labelText="Date" value={value} onChange={setValue} />
      <output aria-label="Current value">{value}</output>
    </>
  )
}

test('typed ISO dates are kept in the input and parent state', async () => {
  render(<StatefulDatePicker initial="" />)
  const input = screen.getByLabelText('Date')

  await userEvent.type(input, '2024-01-15')

  expect(input).toHaveValue('2024-01-15')
  expect(screen.getByLabelText('Current value')).toHaveTextContent('2024-01-15')
})

test('editing a valid date keeps the partial text instead of clearing the input', async () => {
  render(<StatefulDatePicker initial="2024-01-15" />)
  const input = screen.getByLabelText('Date')

  await userEvent.type(input, '{Backspace}')

  expect(input).toHaveValue('2024-01-1')
  expect(screen.getByLabelText('Current value')).toHaveTextContent('2024-01-1')

  await userEvent.type(input, '6')

  expect(input).toHaveValue('2024-01-16')
  expect(screen.getByLabelText('Current value')).toHaveTextContent('2024-01-16')
})

test('typing over a selected valid date replaces every character', async () => {
  render(<StatefulDatePicker initial="2024-01-15" />)
  const input = screen.getByLabelText('Date')

  await userEvent.tripleClick(input)
  await userEvent.keyboard('2025-02-20')

  expect(input).toHaveValue('2025-02-20')
  expect(screen.getByLabelText('Current value')).toHaveTextContent('2025-02-20')
})

test('a parent reset still clears a selected date', () => {
  render(<StatefulDatePicker initial="2024-01-15" />)
  const input = screen.getByLabelText('Date')

  act(() => reset())

  expect(input).toHaveValue('')
  expect(screen.getByLabelText('Current value')).toHaveTextContent('')
})
