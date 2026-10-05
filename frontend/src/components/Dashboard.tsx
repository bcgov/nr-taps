import { Table, TableHead, TableHeader, TableRow, TableBody, TableCell, Tile } from '@carbon/react'
import { Link } from '@tanstack/react-router'
import { applications, type ApplicationId } from '@/application-catalogue'
import { useAuth } from '@/context/auth/AuthContext'
import { describeGrant } from '@/context/auth/capabilities'
import SessionStatus, { NoRoleNotice } from '@/components/SessionStatus'
import PageHeader from './PageHeader'
import TableFrame from './TableFrame'

export default function Dashboard() {
  const { state, can } = useAuth()

  return (
    <section className="taps-page">
      <PageHeader title="TAPS" subtitle="Timber Appraisal and Pricing System" />
      <SessionStatus />
      {state.kind === 'signed-in' && (
        <>
          <p className="taps-session-summary">
            Signed in as {state.session.displayName}
            {state.session.businessName && ` for ${state.session.businessName}`}.
          </p>
          {state.session.roles.length ? (
            <>
              <Tile className="taps-access-summary">
                <h2>Your TAPS access</h2>
                <ul className="taps-grants">
                  {state.session.roles.map((grant) => {
                    const label = describeGrant(grant)
                    return <li key={label}>{label}</li>
                  })}
                </ul>
              </Tile>
              <Tile>
                <h2 className="taps-section-title">Modules</h2>
                <TableFrame ariaLabel="Available modules table">
                  <Table size="md" useZebraStyles aria-label="Available modules">
                    <TableHead>
                      <TableRow>
                        <TableHeader>Module</TableHeader>
                        <TableHeader>Description</TableHeader>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {(Object.keys(applications) as ApplicationId[])
                        .filter((application) => applications[application].capabilities.some(can))
                        .map((application) => (
                          <TableRow key={application}>
                            <TableCell>
                              <Link to={`/${application}`}>{applications[application].title}</Link>
                            </TableCell>
                            <TableCell>{applications[application].description}</TableCell>
                          </TableRow>
                        ))}
                    </TableBody>
                  </Table>
                </TableFrame>
              </Tile>
            </>
          ) : (
            <NoRoleNotice />
          )}
        </>
      )}
    </section>
  )
}
