import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { AuthContext, type AuthState, type LoginProvider } from '@/context/auth/AuthContext'
import type { Capability } from '@/context/auth/capabilities'
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

  const login = useCallback(async (provider: LoginProvider) => {
    requestRef.current += 1
    setState({ kind: 'loading' })
    try {
      await startLogin(provider)
    } catch {
      setState({ kind: 'error', message: 'Unable to start sign in.' })
    }
  }, [])

  const logout = useCallback(async () => {
    // Invalidate pending session requests before logout.
    requestRef.current += 1
    setState({ kind: 'signed-out' })
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
