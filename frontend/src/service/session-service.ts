import { clearLogin, getOidcUser } from '@/service/oidc-service'

export type IdentityProvider = 'IDIR' | 'BCEID_BUSINESS'

export type RoleScope = { type: 'DISTRICT' | 'REGION' | 'FOREST_CLIENT'; value: string }

export type RoleGrant = { role: string; scopes: RoleScope[] }

export type Session = {
  userId: string
  displayName: string
  email: string | null
  identityProvider: IdentityProvider
  businessName: string | null
  roles: RoleGrant[]
  capabilities: string[]
  forestClients: string[]
}

export class SessionUnavailableError extends Error {}

export async function fetchSession(): Promise<Session | null> {
  const oidcUser = await getOidcUser()
  if (!oidcUser) return null
  const response = await fetch('/api/me', {
    headers: { Authorization: `Bearer ${oidcUser.access_token}` },
  })
  if (response.status === 401) {
    await clearLogin()
    return null
  }
  if (!response.ok) throw new SessionUnavailableError('The TAPS service is unavailable.')
  return (await response.json()) as Session
}
