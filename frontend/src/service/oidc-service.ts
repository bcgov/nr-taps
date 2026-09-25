import { ErrorResponse, UserManager, WebStorageStateStore, type User } from 'oidc-client-ts'
import { env } from '@/env'

export const AUTH_CALLBACK_PATH = '/authCallback'

export function isOidcConfigured(): boolean {
  return Boolean(env('VITE_OIDC_ISSUER_URI').trim() && env('VITE_OIDC_CLIENT_ID').trim())
}

let manager: UserManager | undefined
let renewal: Promise<User | null> | undefined
let callback: Promise<User> | undefined

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
  if (!isOidcConfigured()) return null
  if (renewal) return renewal
  const user = await userManager().getUser()
  if (!user) return null
  if (!user.expired && (user.expires_in ?? 0) > 60) return user
  if (!user.refresh_token) return null
  // Another caller may have started renewing while this one read storage.
  if (renewal) return renewal

  const attempt = renew()
  renewal = attempt
  try {
    return await attempt
  } finally {
    if (renewal === attempt) renewal = undefined
  }
}

// SSO rejects the refresh token once its session has ended, for example after the idle timeout.
// Remove that session so the user is signed out. Keep it after network or server failures, which a
// later attempt can recover from.
async function renew(): Promise<User | null> {
  try {
    return await userManager().signinSilent()
  } catch (error) {
    if (!(error instanceof ErrorResponse)) throw error
    await userManager().removeUser()
    return null
  }
}

export async function startLogin(provider: 'idir' | 'business-bceid'): Promise<void> {
  if (!isOidcConfigured()) throw new Error('TAPS sign in is not configured.')
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
  callback ??= userManager().signinRedirectCallback()
  return callback
}

export async function logout(): Promise<void> {
  await userManager().signoutRedirect()
}

export async function clearLogin(): Promise<void> {
  if (manager) await manager.removeUser()
}
