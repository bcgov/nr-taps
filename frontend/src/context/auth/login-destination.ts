const LOGIN_DESTINATION_KEY = 'taps.login-destination'

export function clearLoginDestination(): void {
  window.sessionStorage.removeItem(LOGIN_DESTINATION_KEY)
}

// Only same-origin paths are returned; the router shows Not Found for unknown routes.
export function getLoginDestination(): string | null {
  const destination = window.sessionStorage.getItem(LOGIN_DESTINATION_KEY)
  if (
    !destination?.startsWith('/') ||
    destination.startsWith('//') ||
    /[\\\r\n\t]/.test(destination)
  ) {
    return null
  }
  return destination
}

export function setLoginDestination(destination?: string): void {
  clearLoginDestination()
  if (destination) window.sessionStorage.setItem(LOGIN_DESTINATION_KEY, destination)
}
