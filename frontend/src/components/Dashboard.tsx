import { Button } from 'react-bootstrap'
import { Link } from '@tanstack/react-router'
import { applications, type ApplicationId } from '@/application-catalogue'
import { useAuth } from '@/context/auth/AuthContext'
import { describeGrant } from '@/context/auth/capabilities'
import SessionStatus, { NoRoleNotice } from '@/components/SessionStatus'

export default function Dashboard() {
  const { state, can, logout } = useAuth()

  return (
    <main className="container taps-content">
      <h1>TAPS</h1>
      <p>Timber appraisal services</p>
      <SessionStatus />
      {state.kind === 'signed-in' && (
        <>
          <p>
            Signed in as {state.session.displayName}
            {state.session.businessName && ` for ${state.session.businessName}`}.
          </p>
          {state.session.roles.length ? (
            <>
              <h2 className="h5">Your TAPS access</h2>
              <ul>
                {state.session.roles.map((grant) => {
                  const label = describeGrant(grant)
                  return <li key={label}>{label}</li>
                })}
              </ul>
              <div className="row g-3 mb-4">
                {(Object.keys(applications) as ApplicationId[])
                  .filter((application) => applications[application].capabilities.some(can))
                  .map((application) => (
                    <div className="col-sm-6" key={application}>
                      <div className="card h-100">
                        <div className="card-body">
                          <h2 className="h4">
                            <Link to={`/${application}`}>{applications[application].title}</Link>
                          </h2>
                          <p className="mb-0">{applications[application].description}</p>
                        </div>
                      </div>
                    </div>
                  ))}
              </div>
            </>
          ) : (
            <NoRoleNotice />
          )}
          <Button onClick={() => void logout()}>Sign out</Button>
        </>
      )}
    </main>
  )
}
