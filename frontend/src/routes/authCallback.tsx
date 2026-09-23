import { createFileRoute } from '@tanstack/react-router'
import { useEffect, useState } from 'react'
import { completeLogin } from '@/service/oidc-service'

export const Route = createFileRoute('/authCallback')({ component: AuthCallback })

function AuthCallback() {
  const [error, setError] = useState(false)
  useEffect(() => {
    void completeLogin()
      .then(() => window.location.replace('/'))
      .catch(() => setError(true))
  }, [])
  return error ? (
    <p role="alert">Sign in could not be completed. Return to the home page and try again.</p>
  ) : (
    <p>Completing sign in…</p>
  )
}
