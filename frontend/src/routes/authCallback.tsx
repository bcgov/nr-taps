import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useEffect, useState } from 'react'
import { completeLogin } from '@/service/oidc-service'

export const Route = createFileRoute('/authCallback')({ component: AuthCallback })

function AuthCallback() {
  const [error, setError] = useState(false)
  const navigate = useNavigate()
  useEffect(() => {
    let active = true
    void completeLogin()
      .then(() => {
        if (active) void navigate({ to: '/' })
      })
      .catch(() => {
        if (active) setError(true)
      })
    return () => {
      active = false
    }
  }, [navigate])
  return (
    <main className="container taps-content">
      <h1>Sign in</h1>
      {error ? (
        <>
          <p role="alert">Sign in could not be completed. Please try again.</p>
          <Link to="/">Return to home</Link>
        </>
      ) : (
        <p role="status">Completing sign in…</p>
      )}
    </main>
  )
}
