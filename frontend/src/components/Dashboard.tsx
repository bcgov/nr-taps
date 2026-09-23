import { useEffect, useState } from 'react'
import { Button } from 'react-bootstrap'
import {
  clearLogin,
  getOidcUser,
  isOidcConfigured,
  logout,
  startLogin,
} from '@/service/oidc-service'

type CurrentUser = { subject: string; name: string }
type State =
  | { kind: 'loading' }
  | { kind: 'signed-out' }
  | { kind: 'signed-in'; user: CurrentUser }
  | { kind: 'error'; message: string }

export default function Dashboard() {
  const [state, setState] = useState<State>({ kind: 'loading' })

  useEffect(() => {
    let active = true
    async function load() {
      try {
        const oidcUser = await getOidcUser()
        if (!oidcUser) {
          if (active) setState({ kind: 'signed-out' })
          return
        }
        const response = await fetch('/api/me', {
          headers: { Authorization: `Bearer ${oidcUser.access_token}` },
        })
        if (response.status === 401) {
          await clearLogin()
          if (active) setState({ kind: 'signed-out' })
          return
        }
        if (!response.ok) throw new Error('The TAPS service is unavailable.')
        const user = (await response.json()) as CurrentUser
        if (active) setState({ kind: 'signed-in', user })
      } catch {
        if (active) setState({ kind: 'error', message: 'Unable to load your session.' })
      }
    }
    void load()
    return () => {
      active = false
    }
  }, [])

  async function signIn(provider: 'idir' | 'business-bceid') {
    try {
      await startLogin(provider)
    } catch {
      setState({ kind: 'error', message: 'Unable to start sign in.' })
    }
  }

  async function signOut() {
    try {
      await logout()
    } catch {
      await clearLogin()
      setState({ kind: 'signed-out' })
    }
  }

  return (
    <main className="container" style={{ maxWidth: '48rem' }}>
      <h1>TAPS</h1>
      {state.kind === 'loading' && <p>Loading…</p>}
      {state.kind === 'signed-in' && (
        <>
          <p>Signed in as {state.user.name}.</p>
          <p>The TAPS application is being set up.</p>
          <Button onClick={() => void signOut()}>Sign out</Button>
        </>
      )}
      {state.kind === 'error' && <p role="alert">{state.message}</p>}
      {(state.kind === 'signed-out' || state.kind === 'error') &&
        (isOidcConfigured() ? (
          <div className="d-flex gap-2">
            <Button onClick={() => void signIn('idir')}>Sign in with IDIR</Button>
            <Button variant="secondary" onClick={() => void signIn('business-bceid')}>
              Sign in with Business BCeID
            </Button>
          </div>
        ) : (
          <p>Sign in is not configured for this environment.</p>
        ))}
    </main>
  )
}
