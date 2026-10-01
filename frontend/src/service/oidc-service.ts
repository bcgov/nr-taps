import { ErrorResponse, UserManager, WebStorageStateStore, type User } from 'oidc-client-ts'
import { env } from '@/env'

export const AUTH_CALLBACK_PATH = '/authCallback'

export function isOidcConfigured(): boolean {
  return Boolean(env('VITE_OIDC_ISSUER_URI').trim() && env('VITE_OIDC_CLIENT_ID').trim())
}

let manager: UserManager | undefined
let generation = 0
let signedOut = false
let renewal: Promise<User | null> | undefined
let callback: Promise<User> | undefined
let endingSession: Promise<void> | undefined

function userManager(): UserManager {
  if (!manager) {
    manager = new UserManager({
      authority: env('VITE_OIDC_ISSUER_URI').trim(),
      client_id: env('VITE_OIDC_CLIENT_ID').trim(),
      redirect_uri: `${window.location.origin}${AUTH_CALLBACK_PATH}`,
      post_logout_redirect_uri: window.location.origin,
      response_type: 'code',
      scope: 'openid profile email',
      userStore: new WebStorageStateStore({ store: window.sessionStorage }),
      stateStore: new WebStorageStateStore({ store: window.sessionStorage }),
      automaticSilentRenew: false,
      loadUserInfo: false,
    })
  }
  return manager
}

export async function getOidcUser(): Promise<User | null> {
  if (!isOidcConfigured() || signedOut) return null
  const started = generation
  if (renewal) return renewal
  const user = await userManager().getUser()
  if (started !== generation || signedOut || !user) return null
  if (!user.expired && (user.expires_in ?? 0) > 60) return user
  if (!user.refresh_token) {
    await clearLogin()
    return null
  }
  // Another caller may have started renewing while this one read storage.
  if (renewal) return renewal

  const attempt = renew(started)
  renewal = attempt
  try {
    return await attempt
  } finally {
    if (renewal === attempt) renewal = undefined
  }
}

// SSO rejects the refresh token as invalid_grant once it is expired or revoked, or its session has
// ended, for example after the idle timeout. Remove that session so the user is signed out. Keep it
// after anything else, including OAuth errors such as server_error or temporarily_unavailable,
// because a later attempt can recover from those.
async function renew(started: number): Promise<User | null> {
  try {
    const user = await userManager().signinSilent()
    if (started !== generation || signedOut) {
      // The SDK stores a refresh result before resolving. Discard a late write after sign out.
      await endingSession?.catch(() => undefined)
      await userManager().removeUser()
      return null
    }
    return user
  } catch (error) {
    if (!(error instanceof ErrorResponse && error.error === 'invalid_grant')) throw error
    await clearLogin()
    return null
  }
}

export async function startLogin(provider: 'idir' | 'business-bceid'): Promise<void> {
  if (!isOidcConfigured()) throw new Error('TAPS sign in is not configured.')
  // An old renewal must finish discarding its credentials before another login can store a user.
  await renewal?.catch(() => null)
  await endingSession?.catch(() => undefined)
  signedOut = false
  endingSession = undefined
  await userManager().signinRedirect({
    extraQueryParams: {
      kc_idp_hint:
        provider === 'idir'
          ? env('VITE_OIDC_IDIR_HINT') || 'azureidir'
          : env('VITE_OIDC_BCEID_HINT') || 'bceidbusiness',
    },
  })
}

// React StrictMode can mount a callback twice. The authorization code can only be used once.
export function completeLogin(): Promise<User> {
  if (!callback) {
    const started = generation
    callback = (async () => {
      const user = await userManager().signinRedirectCallback()
      if (started !== generation || signedOut) {
        await userManager().removeUser()
        throw new Error('The session ended during login.')
      }
      return user
    })()
  }
  return callback
}

// Keycloak does not end the SiteMinder session behind Business BCeID. Without SiteMinder logoff, the
// next Business BCeID sign in in this browser returns the previous account without a password. When
// configured, the browser logs off SiteMinder first, and SiteMinder returns it to Keycloak.
export function logout(
  navigate: (url: string) => void = (url) => window.location.assign(url),
): Promise<void> {
  if (endingSession) return endingSession
  signedOut = true
  generation += 1
  endingSession = (async () => {
    try {
      const user = await userManager().getUser()
      // Read the ID token before removing the user: Keycloak needs it to find the session to end.
      const url = endSessionUrl(user?.id_token)
      await userManager().removeUser()
      navigate(url)
    } catch (error) {
      endingSession = undefined
      throw error
    }
  })()
  return endingSession
}

function endSessionUrl(idTokenHint?: string): string {
  const issuer = env('VITE_OIDC_ISSUER_URI').trim().replace(/\/+$/, '')
  const params = new URLSearchParams({
    client_id: env('VITE_OIDC_CLIENT_ID').trim(),
    post_logout_redirect_uri: window.location.origin,
  })
  if (idTokenHint) params.set('id_token_hint', idTokenHint)
  const keycloakLogoutUrl = `${issuer}/protocol/openid-connect/logout?${params}`
  const siteminderLogoutUrl = env('VITE_OIDC_SITEMINDER_LOGOUT_URL').trim()
  return siteminderLogoutUrl
    ? `${siteminderLogoutUrl}?retnow=1&returl=${encodeURIComponent(keycloakLogoutUrl)}`
    : keycloakLogoutUrl
}

export async function clearLogin(): Promise<void> {
  signedOut = true
  generation += 1
  if (manager) await manager.removeUser()
}
