import { useEffect, useMemo, useState } from 'react'
import { ReadApiError } from '@/service/read-service'

type State<T> = { loading: boolean; value?: T; error?: Error }

// Callers memoize load so filter/detail identity changes cancel the previous request.
export default function useReadResource<T>(load: ((signal: AbortSignal) => Promise<T>) | null) {
  const [state, setState] = useState<State<T> & { identity?: object }>({
    loading: false,
  })
  const [retryCount, setRetryCount] = useState(0)
  const identity = useMemo(() => ({ load, retryCount }), [load, retryCount])
  useEffect(() => {
    if (!load) return
    const controller = new AbortController()
    void Promise.resolve()
      .then(() => load(controller.signal))
      .then(
        (value) => {
          if (!controller.signal.aborted) setState({ loading: false, value, identity })
        },
        (error: unknown) => {
          if (!controller.signal.aborted)
            setState({
              loading: false,
              identity,
              error:
                error instanceof ReadApiError
                  ? error
                  : new Error('The read service is unavailable. Please try again.'),
            })
        },
      )
    return () => controller.abort()
  }, [load, identity])
  const visible: State<T> = state.identity === identity ? state : { loading: load !== null }
  return { ...visible, retry: () => setRetryCount((count) => count + 1) }
}

// Keep the last total while the next page loads so Pagination (and its focus) stays put.
export function useLoadedTotal(total: number | undefined, scope: unknown = null) {
  const [kept, setKept] = useState({ scope, total })
  const next =
    kept.scope !== scope || (total !== undefined && total !== kept.total) ? { scope, total } : kept
  if (next !== kept) setKept(next)
  return total ?? next.total
}
