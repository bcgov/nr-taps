import { Button, InlineLoading } from '@carbon/react'
import { useAuth } from '@/context/auth/AuthContext'
import AppNotification from './AppNotification'

export function NoRoleNotice() {
  return (
    <AppNotification
      title="TAPS access pending"
      subtitle="You do not have TAPS access yet. Ask your TAPS access administrator to grant you access, then log in again."
    />
  )
}

// Signed-out visitors see the login page instead of the application shell.
export default function SessionStatus() {
  const { state, logout, reloadSession } = useAuth()
  if (state.kind === 'loading')
    return <InlineLoading role="status" aria-live="polite" description="Loading your session…" />
  if (state.kind === 'error') {
    return (
      <>
        <AppNotification kind="error" title="Session unavailable" subtitle={state.message} />
        <div className="taps-actions">
          <Button size="md" onClick={() => void reloadSession()}>
            Try again
          </Button>
          <Button kind="tertiary" size="md" onClick={() => void logout()}>
            Log out
          </Button>
        </div>
      </>
    )
  }
  return null
}
