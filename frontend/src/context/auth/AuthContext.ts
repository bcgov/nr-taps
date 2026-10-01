import { createContext, use } from 'react'
import type { Session } from '@/service/session-service'
import type { Capability } from '@/context/auth/capabilities'

export type LoginProvider = 'idir' | 'business-bceid'

export type AuthState =
  | { kind: 'loading' }
  | { kind: 'signed-out' }
  | { kind: 'signed-in'; session: Session }
  | { kind: 'error'; message: string }

export type AuthContextValue = {
  state: AuthState
  can: (capability: Capability) => boolean
  reloadSession: () => Promise<void>
  login: (provider: LoginProvider) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = use(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
