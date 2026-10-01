import { act, cleanup, render } from '@testing-library/react'
import { afterEach } from 'vitest'
import { createMemoryHistory, createRouter, RouterProvider } from '@tanstack/react-router'
import { routeTree } from '@/routeTree.gen'
import type { Session } from '@/service/session-service'

afterEach(() => {
  cleanup()
})

function customRender(ui: React.ReactElement, options = {}) {
  return render(ui, {
    // wrap provider(s) here if needed
    wrapper: ({ children }) => children,
    ...options,
  })
}

export async function renderRoute(path = '/') {
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: [path] }),
  })
  const result = render(<RouterProvider router={router} />)
  await act(async () => {
    await router.load()
  })
  return { ...result, router }
}

export const staffSession: Session = {
  userId: 'user-123',
  displayName: 'TAPS User',
  email: 'user@example.invalid',
  identityProvider: 'IDIR',
  businessName: null,
  roles: [{ role: 'TAPS_DISTRICT_APPRAISER', scopes: [{ type: 'DISTRICT', value: 'DCR' }] }],
  capabilities: ['ECAS_SUBMISSION_VIEW', 'GAS_APPRAISAL_VIEW'],
  forestClients: [],
}

export * from '@testing-library/react'
export { default as userEvent } from '@testing-library/user-event'
// override render export
export { customRender as render }
