import type { ReactNode } from 'react'
import { Link } from '@tanstack/react-router'
import { useAuth } from '@/context/auth/AuthContext'
import type { Capability } from '@/context/auth/capabilities'
import SessionStatus, { NoRoleNotice } from '@/components/SessionStatus'
import PageHeader from './PageHeader'
import AppNotification from './AppNotification'

export default function RequireAccess({
  capabilities,
  children,
}: {
  capabilities: readonly Capability[]
  children: ReactNode
}) {
  const { state, can } = useAuth()
  if (state.kind === 'signed-in' && capabilities.some(can)) return children
  return (
    <section className="taps-page">
      <PageHeader title="TAPS access" />
      {state.kind === 'signed-in' ? (
        <>
          {state.session.roles.length ? (
            <AppNotification
              kind="error"
              title="Access not granted"
              subtitle="You do not have access to this page."
            />
          ) : (
            <NoRoleNotice />
          )}
          <div className="taps-actions">
            <Link to="/">Return to home</Link>
          </div>
        </>
      ) : (
        <SessionStatus />
      )}
    </section>
  )
}
