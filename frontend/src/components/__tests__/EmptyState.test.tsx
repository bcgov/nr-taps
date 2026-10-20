import { render, screen } from '@testing-library/react'
import EmptyState from '../EmptyState'

test('connects the empty state to its heading and description with unique IDs', () => {
  render(
    <>
      <EmptyState title="No submissions" description="Submissions will appear here." />
      <EmptyState title="No worksheets" description="Worksheets will appear here." />
    </>,
  )

  const submissions = screen.getByRole('region', { name: 'No submissions' })
  const worksheets = screen.getByRole('region', { name: 'No worksheets' })
  expect(screen.getByRole('heading', { level: 2, name: 'No submissions' })).toBeInTheDocument()
  expect(submissions).toHaveAccessibleDescription('Submissions will appear here.')
  expect(worksheets).toHaveAccessibleDescription('Worksheets will appear here.')
  expect(submissions.getAttribute('aria-describedby')).not.toBe(
    worksheets.getAttribute('aria-describedby'),
  )
})

test('supports a subsection heading beneath a titled card', () => {
  render(
    <section>
      <h2>Pages</h2>
      <EmptyState
        headingLevel={3}
        title="No pages available"
        description="No pages are available."
      />
    </section>,
  )

  expect(screen.getByRole('heading', { level: 3, name: 'No pages available' })).toBeInTheDocument()
  expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(1)
})
