import { expect, test, vi } from 'vitest'
import { notifySessionExpired, SESSION_EXPIRED_EVENT } from '@/context/auth/session-expiry'

test('emits a session-expired event with the expiry reason', () => {
  const listener = vi.fn()
  window.addEventListener(SESSION_EXPIRED_EVENT, listener)

  notifySessionExpired('api-unauthorized')

  expect(listener).toHaveBeenCalledOnce()
  expect(listener.mock.calls[0][0]).toMatchObject({ detail: { reason: 'api-unauthorized' } })
  window.removeEventListener(SESSION_EXPIRED_EVENT, listener)
})
