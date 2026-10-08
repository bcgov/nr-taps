import { act, renderHook, waitFor } from '@testing-library/react'
import { expect, test, vi } from 'vitest'
import useReadResource from '../useReadResource'
import { ReadApiError } from '@/service/read-service'

test('cancels obsolete work and never displays its late completion', async () => {
  let complete!: (value: string) => void
  const first = vi.fn(
    (_signal: AbortSignal) =>
      new Promise<string>((resolve) => {
        complete = resolve
      }),
  )
  const second = vi.fn(async (_signal: AbortSignal) => 'second result')
  const { result, rerender, unmount } = renderHook(({ load }) => useReadResource(load), {
    initialProps: { load: first },
  })
  await waitFor(() => expect(first).toHaveBeenCalledOnce())
  rerender({ load: second })
  expect(first.mock.calls[0][0].aborted).toBe(true)
  expect(result.current.value).toBeUndefined()
  await waitFor(() => expect(result.current.value).toBe('second result'))
  await act(async () => complete('stale first result'))
  expect(result.current.value).toBe('second result')
  unmount()
  expect(second.mock.calls[0][0].aborted).toBe(true)
})

test('supports safe failures, retry and reset without leaving previous values visible', async () => {
  const load = vi
    .fn()
    .mockRejectedValueOnce(new ReadApiError(403))
    .mockResolvedValue('allowed result')
  const { result, rerender } = renderHook(({ request }) => useReadResource(request), {
    initialProps: { request: load as ((signal: AbortSignal) => Promise<string>) | null },
  })
  await waitFor(() => expect(result.current.error).toMatchObject({ status: 403 }))
  act(() => result.current.retry())
  expect(result.current.error).toBeUndefined()
  await waitFor(() => expect(result.current.value).toBe('allowed result'))
  rerender({ request: null })
  expect(result.current.value).toBeUndefined()
  expect(result.current.loading).toBe(false)
})
