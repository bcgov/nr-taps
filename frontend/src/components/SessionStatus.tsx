import { Button } from 'react-bootstrap'
import { useAuth } from '@/context/auth/AuthContext'
import { isOidcConfigured } from '@/service/oidc-service'

export function NoRoleNotice() {
  return (
    <p role="status">
      You do not have TAPS access yet. Ask your TAPS access administrator to grant you access, then
      sign in again.
    </p>
  )
}

export default function SessionStatus() {
  const { state, login, logout, reloadSession } = useAuth()
  if (state.kind === 'loading') return <p role="status">Loading your session…</p>
  if (state.kind === 'error') {
    return (
      <>
        <p role="alert">{state.message}</p>
        <div className="d-flex flex-wrap gap-2">
          <Button onClick={() => void reloadSession()}>Try again</Button>
          <Button variant="secondary" onClick={() => void logout()}>
            Sign out
          </Button>
        </div>
      </>
    )
  }
  if (state.kind === 'signed-in') return null
  if (!isOidcConfigured()) return <p>Sign in is not configured for this environment.</p>
  return (
    <>
      <p>Sign in to access TAPS.</p>
      <div className="d-flex flex-wrap gap-2">
        <Button onClick={() => void login('idir')}>Sign in with IDIR</Button>
        <Button variant="secondary" onClick={() => void login('business-bceid')}>
          Sign in with Business BCeID
        </Button>
      </div>
    </>
  )
}
