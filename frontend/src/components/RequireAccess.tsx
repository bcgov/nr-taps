import type { ReactNode } from 'react'
import { Link } from '@tanstack/react-router'
import { Button } from 'react-bootstrap'
import { useAuth } from '@/context/auth/AuthContext'
import type { Capability } from '@/context/auth/capabilities'
import SessionStatus, { NoRoleNotice } from '@/components/SessionStatus'

export default function RequireAccess({
  capabilities,
  children,
}: {
  capabilities: readonly Capability[]
  children: ReactNode
}) {
  const { state, can, logout } = useAuth()
  if (state.kind === 'signed-in' && capabilities.some(can)) return children
  return (
    <main className="container taps-content">
      <h1>TAPS access</h1>
      {state.kind === 'signed-in' ? (
        <>
          {state.session.roles.length ? (
            <p role="alert">You do not have access to this page.</p>
          ) : (
            <NoRoleNotice />
          )}
          <div className="d-flex flex-wrap align-items-center gap-3">
            <Link to="/">Return to home</Link>
            <Button variant="secondary" onClick={() => void logout()}>
              Sign out
            </Button>
          </div>
        </>
      ) : (
        <SessionStatus />
      )}
    </main>
  )
}
