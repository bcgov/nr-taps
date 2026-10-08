import { createContext, use } from 'react'
import type { Session } from '@/service/session-service'
import type { Capability } from '@/context/auth/capabilities'

export type LoginProvider = 'idir' | 'business-bceid'

export type AuthState =
  | { kind: 'loading' }
  // expired marks a session that ended on its own rather than by logging out.
  | { kind: 'signed-out'; expired?: boolean }
  | { kind: 'signed-in'; session: Session }
  | { kind: 'error'; message: string }

export type AuthContextValue = {
  state: AuthState
  can: (capability: Capability) => boolean
  reloadSession: () => Promise<void>
  // destination is the local path to return to after the sign-in callback.
  login: (provider: LoginProvider, destination?: string) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = use(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
