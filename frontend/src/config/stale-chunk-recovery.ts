const STALE_CHUNK_RELOAD_KEY = 'taps:stale-chunk-reload-at'
const STALE_CHUNK_RELOAD_COOLDOWN_MS = 60_000

type ReloadStorage = Pick<Storage, 'getItem' | 'setItem'>

// After a deploy, an open tab can request chunks that no longer exist. Reload once to pick up
// the new build; a second failure inside the cooldown is left for the route error boundary.
export function recoverFromStaleChunk(
  event: Event,
  storage: ReloadStorage,
  reload: () => void,
  now = Date.now(),
): boolean {
  try {
    const previousAttempt = Number.parseInt(storage.getItem(STALE_CHUNK_RELOAD_KEY) ?? '', 10)
    const attemptedRecently =
      Number.isFinite(previousAttempt) &&
      now >= previousAttempt &&
      now - previousAttempt < STALE_CHUNK_RELOAD_COOLDOWN_MS

    if (attemptedRecently) return false

    storage.setItem(STALE_CHUNK_RELOAD_KEY, String(now))
  } catch {
    return false
  }

  event.preventDefault()
  reload()
  return true
}

export function registerStaleChunkRecovery(): void {
  window.addEventListener('vite:preloadError', (event) => {
    recoverFromStaleChunk(event, window.sessionStorage, () => window.location.reload())
  })
}
