import { Apps, UserAccess } from '@carbon/icons-react'
import { Table, TableHead, TableHeader, TableRow, TableBody, TableCell, Tile } from '@carbon/react'
import { Link } from '@tanstack/react-router'
import { applications, type ApplicationId } from '@/application-catalogue'
import { useAuth } from '@/context/auth/AuthContext'
import { describeGrant } from '@/context/auth/capabilities'
import SessionStatus, { NoRoleNotice } from '@/components/SessionStatus'
import CardTitle from './CardTitle'
import PageHeader from './PageHeader'
import TableFrame from './TableFrame'

export default function Dashboard() {
  const { state, can } = useAuth()
  const session = state.kind === 'signed-in' ? state.session : null

  return (
    <section className="taps-page">
      <PageHeader
        title="Home"
        subtitle={
          session &&
          `Logged in as ${session.displayName}${session.businessName ? ` for ${session.businessName}` : ''}.`
        }
      />
      <SessionStatus />
      {session &&
        (session.roles.length ? (
          <>
            <Tile className="taps-card">
              <CardTitle icon={UserAccess}>Your TAPS access</CardTitle>
              <ul className="taps-grants">
                {session.roles.map((grant) => {
                  const label = describeGrant(grant)
                  return <li key={label}>{label}</li>
                })}
              </ul>
            </Tile>
            <Tile className="taps-card">
              <CardTitle icon={Apps}>Modules</CardTitle>
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
        ))}
    </section>
  )
}
