import { afterEach, expect, test } from 'vitest'
import {
  clearLoginDestination,
  getLoginDestination,
  setLoginDestination,
} from '@/context/auth/login-destination'

const key = 'taps.login-destination'

afterEach(() => window.sessionStorage.clear())

test('keeps a local destination until it is cleared', () => {
  setLoginDestination('/ecas/ECAS05?ecasId=1001#results')

  expect(getLoginDestination()).toBe('/ecas/ECAS05?ecasId=1001#results')
  clearLoginDestination()
  expect(getLoginDestination()).toBeNull()
})

test('replaces an abandoned destination when sign in starts without one', () => {
  setLoginDestination('/gas')
  setLoginDestination()

  expect(window.sessionStorage.getItem(key)).toBeNull()
})

test.each([
  'https://example.com/ecas',
  '//example.com/ecas',
  '/\\example.com/ecas',
  'ecas/ECAS05',
  '/ecas\n/ECAS05',
  '/ecas\r/ECAS05',
  '/\t/example.com',
])('does not return the unsafe destination %j', (destination) => {
  window.sessionStorage.setItem(key, destination)

  expect(getLoginDestination()).toBeNull()
})
