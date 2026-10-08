import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useEffect, useState } from 'react'
import { clearLoginDestination, getLoginDestination } from '@/context/auth/login-destination'
import { completeLogin } from '@/service/oidc-service'
import { InlineLoading } from '@carbon/react'
import PageHeader from '@/components/PageHeader'
import AppNotification from '@/components/AppNotification'

export const Route = createFileRoute('/authCallback')({ component: AuthCallback })

function AuthCallback() {
  const [error, setError] = useState(false)
  const navigate = useNavigate()
  useEffect(() => {
    let active = true
    void completeLogin()
      .then(() => {
        if (!active) return
        const destination = getLoginDestination() ?? '/'
        clearLoginDestination()
        void navigate({ href: destination, replace: true })
      })
      .catch(() => {
        if (!active) return
        clearLoginDestination()
        setError(true)
      })
    return () => {
      active = false
    }
  }, [navigate])
  return (
    <section className="taps-page">
      <PageHeader title="Log in" />
      {error ? (
        <>
          <AppNotification
            kind="error"
            title="Log in unsuccessful"
            subtitle="Log in could not be completed. Please try again."
          />
          <Link to="/">Return to home</Link>
        </>
      ) : (
        <InlineLoading role="status" aria-live="polite" description="Completing log in…" />
      )}
    </section>
  )
}
