import { afterEach, expect, test, vi } from 'vitest'
import { recoverFromStaleChunk, registerStaleChunkRecovery } from '@/config/stale-chunk-recovery'

const createStorage = (): Pick<Storage, 'getItem' | 'setItem'> => {
  const values = new Map<string, string>()
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
  }
}

afterEach(() => {
  vi.restoreAllMocks()
  window.sessionStorage.clear()
})

test('reloads once and prevents the stale chunk error from reaching the router', () => {
  const event = new Event('vite:preloadError', { cancelable: true })
  const reload = vi.fn()

  expect(recoverFromStaleChunk(event, createStorage(), reload, 1_000)).toBe(true)
  expect(event.defaultPrevented).toBe(true)
  expect(reload).toHaveBeenCalledOnce()
})

test('does not enter a reload loop when the replacement chunk also fails', () => {
  const storage = createStorage()
  const reload = vi.fn()
  const firstEvent = new Event('vite:preloadError', { cancelable: true })
  const secondEvent = new Event('vite:preloadError', { cancelable: true })

  recoverFromStaleChunk(firstEvent, storage, reload, 1_000)

  expect(recoverFromStaleChunk(secondEvent, storage, reload, 1_001)).toBe(false)
  expect(secondEvent.defaultPrevented).toBe(false)
  expect(reload).toHaveBeenCalledOnce()
})

test('reloads again once the cooldown has passed', () => {
  const storage = createStorage()
  const reload = vi.fn()

  const first = new Event('vite:preloadError', { cancelable: true })
  const later = new Event('vite:preloadError', { cancelable: true })

  recoverFromStaleChunk(first, storage, reload, 1_000)

  expect(recoverFromStaleChunk(later, storage, reload, 61_000)).toBe(true)
  expect(reload).toHaveBeenCalledTimes(2)
})

test('leaves the error for the route boundary when session storage is unavailable', () => {
  const event = new Event('vite:preloadError', { cancelable: true })
  const reload = vi.fn()
  const unavailableStorage = {
    getItem: () => {
      throw new Error('storage unavailable')
    },
    setItem: vi.fn(),
  }

  expect(recoverFromStaleChunk(event, unavailableStorage, reload, 1_000)).toBe(false)
  expect(event.defaultPrevented).toBe(false)
  expect(reload).not.toHaveBeenCalled()
})

test('handles preload errors raised on the window', () => {
  const reload = vi.fn()
  vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, reload })
  registerStaleChunkRecovery()
  const event = new Event('vite:preloadError', { cancelable: true })

  window.dispatchEvent(event)

  expect(event.defaultPrevented).toBe(true)
  expect(reload).toHaveBeenCalledOnce()
  expect(window.sessionStorage.getItem('taps:stale-chunk-reload-at')).not.toBeNull()
})
