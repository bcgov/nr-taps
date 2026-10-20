import { vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import NotFound from '@/components/NotFound'

const navigate = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigate,
}))

describe('NotFound', () => {
  beforeEach(() => {
    navigate.mockClear()
  })

  test('renders one page heading and an accessible description', () => {
    render(<NotFound />)
    expect(screen.getAllByRole('heading')).toHaveLength(1)
    expect(screen.getByRole('heading', { level: 1, name: '404' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: '404' })).toHaveAccessibleDescription(
      'The page you’re looking for does not exist.',
    )
    expect(screen.getByRole('button', { name: /back home/i })).toHaveClass('cds--btn--tertiary')
  })

  test('navigates home when the Back Home button is clicked', async () => {
    const user = userEvent.setup()
    render(<NotFound />)
    await user.click(screen.getByRole('button', { name: /back home/i }))
    expect(navigate).toHaveBeenCalledWith({ to: '/' })
  })
})
