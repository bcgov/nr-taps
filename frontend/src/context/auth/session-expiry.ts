export const SESSION_EXPIRED_EVENT = 'taps:session-expired'

export type SessionExpiredReason = 'token-unavailable' | 'api-unauthorized'

export type SessionExpiredEventDetail = {
  reason: SessionExpiredReason
}

export function notifySessionExpired(reason: SessionExpiredReason): void {
  window.dispatchEvent(
    new CustomEvent<SessionExpiredEventDetail>(SESSION_EXPIRED_EVENT, { detail: { reason } }),
  )
}
