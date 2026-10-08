import { Button, InlineLoading, Tile } from '@carbon/react'
import { useLocation } from '@tanstack/react-router'
import { useAuth } from '@/context/auth/AuthContext'
import { isOidcConfigured } from '@/service/oidc-service'
import AppNotification from './AppNotification'

export function NoRoleNotice() {
  return (
    <AppNotification
      title="TAPS access pending"
      subtitle="You do not have TAPS access yet. Ask your TAPS access administrator to grant you access, then sign in again."
    />
  )
}

export default function SessionStatus() {
  const { state, login, logout, reloadSession } = useAuth()
  const destination = useLocation({ select: (location) => location.href })
  if (state.kind === 'loading')
    return <InlineLoading role="status" aria-live="polite" description="Loading your session…" />
  if (state.kind === 'error') {
    return (
      <>
        <AppNotification kind="error" title="Session unavailable" subtitle={state.message} />
        <div className="taps-actions">
          <Button onClick={() => void reloadSession()}>Try again</Button>
          <Button kind="secondary" onClick={() => void logout()}>
            Sign out
          </Button>
        </div>
      </>
    )
  }
  if (state.kind === 'signed-in') return null
  if (!isOidcConfigured())
    return (
      <AppNotification
        title="Sign in unavailable"
        subtitle="Sign in is not configured for this environment."
      />
    )
  return (
    <Tile className="taps-sign-in">
      <h2>Sign in to access TAPS.</h2>
      <p>
        Access appraisal data submissions, worksheets and stumpage rates with your government or
        business account.
      </p>
      <div className="taps-actions">
        <Button onClick={() => void login('idir', destination)}>Sign in with IDIR</Button>
        <Button kind="secondary" onClick={() => void login('business-bceid', destination)}>
          Sign in with Business BCeID
        </Button>
      </div>
    </Tile>
  )
}
