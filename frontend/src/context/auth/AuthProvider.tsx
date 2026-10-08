import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { AuthContext, type AuthState, type LoginProvider } from '@/context/auth/AuthContext'
import type { Capability } from '@/context/auth/capabilities'
import { clearLoginDestination, setLoginDestination } from '@/context/auth/login-destination'
import { SESSION_EXPIRED_EVENT } from '@/context/auth/session-expiry'
import { clearLogin, logout as endSession, startLogin } from '@/service/oidc-service'
import { fetchSession } from '@/service/session-service'

export default function AuthProvider({
  children,
  deferSessionLoad = false,
}: {
  children: ReactNode
  deferSessionLoad?: boolean
}) {
  const [state, setState] = useState<AuthState>({ kind: 'loading' })
  const requestRef = useRef(0)

  const reloadSession = useCallback(async () => {
    const current = ++requestRef.current
    setState({ kind: 'loading' })
    try {
      const session = await fetchSession()
      if (current === requestRef.current) {
        setState(session ? { kind: 'signed-in', session } : { kind: 'signed-out' })
      }
    } catch {
      if (current === requestRef.current) {
        setState({ kind: 'error', message: 'Unable to load your session. Please try again.' })
      }
    }
  }, [])

  useEffect(() => {
    // Load /api/me only after the callback has stored the user.
    if (!deferSessionLoad) void reloadSession()
    return () => {
      requestRef.current += 1
    }
  }, [deferSessionLoad, reloadSession])

  useEffect(() => {
    // The services have already cleared the stored login; drop access without another request.
    const onSessionExpired = () => {
      requestRef.current += 1
      setState({ kind: 'signed-out' })
      clearLoginDestination()
    }
    window.addEventListener(SESSION_EXPIRED_EVENT, onSessionExpired)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onSessionExpired)
  }, [])

  const login = useCallback(async (provider: LoginProvider, destination?: string) => {
    requestRef.current += 1
    setState({ kind: 'loading' })
    try {
      setLoginDestination(destination)
      await startLogin(provider)
    } catch {
      setState({ kind: 'error', message: 'Unable to start sign in.' })
      clearLoginDestination()
    }
  }, [])

  const logout = useCallback(async () => {
    // Invalidate pending session requests before logout.
    requestRef.current += 1
    setState({ kind: 'signed-out' })
    clearLoginDestination()
    try {
      await endSession()
    } catch {
      try {
        await clearLogin()
      } catch {
        setState({ kind: 'error', message: 'Unable to sign out. Please try again.' })
      }
    }
  }, [])

  const value = useMemo(() => {
    const capabilities = new Set(
      state.kind === 'signed-in' && state.session.roles.length ? state.session.capabilities : [],
    )
    return {
      state,
      can: (capability: Capability) => capabilities.has(capability),
      reloadSession,
      login,
      logout,
    }
  }, [state, reloadSession, login, logout])

  return <AuthContext value={value}>{children}</AuthContext>
}
