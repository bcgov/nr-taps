import { Link } from '@tanstack/react-router'
import { Table, TableHead, TableHeader, TableRow, TableBody, TableCell, Tile } from '@carbon/react'
import { applications, applicationScreens, type ApplicationId } from '@/application-catalogue'
import { useAuth } from '@/context/auth/AuthContext'
import RequireAccess from '@/components/RequireAccess'
import NotFound from '@/components/NotFound'
import PageHeader from './PageHeader'
import EmptyState from './EmptyState'
import TableFrame from './TableFrame'
import { EcasInboxReadPage, GasSearchReadPage } from './appraisal/ReadWorkflowPages'

export function ApplicationPage({ application }: { application: ApplicationId }) {
  const { can } = useAuth()
  const app = applications[application]
  const screens = applicationScreens.filter(
    (screen) => screen.application === application && can(screen.capability),
  )
  return (
    <RequireAccess capabilities={app.capabilities}>
      <section className="taps-page">
        <PageHeader
          title={app.title}
          subtitle={app.description}
          backLink={<Link to="/">TAPS home</Link>}
        />
        <Tile>
          <h2 className="taps-section-title">Pages</h2>
          {screens.length ? (
            <TableFrame ariaLabel={`${app.title} pages table`}>
              <Table size="md" useZebraStyles aria-label={`${app.title} pages`}>
                <TableHead>
                  <TableRow>
                    <TableHeader>Page</TableHeader>
                    <TableHeader>Description</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {screens.map((screen) => (
                    <TableRow key={screen.id}>
                      <TableCell>
                        <Link to={`/${application}/$screenId`} params={{ screenId: screen.id }}>
                          {screen.title}
                        </Link>
                      </TableCell>
                      <TableCell>{screen.description}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableFrame>
          ) : (
            <EmptyState
              title="No pages available"
              description={`Your ${app.title} pages will appear here as they become available.`}
              role="status"
            />
          )}
        </Tile>
      </section>
    </RequireAccess>
  )
}

export function ApplicationScreenPage({
  application,
  screenId,
}: {
  application: ApplicationId
  screenId: string
}) {
  const { state } = useAuth()
  const screen = applicationScreens.find(
    (entry) => entry.application === application && entry.id === screenId,
  )
  if (!screen) return <NotFound />
  if (state.kind === 'signed-in' && state.session.readApiEnabled) {
    if (application === 'ecas' && screenId === 'ECAS05')
      return (
        <RequireAccess capabilities={[screen.capability]}>
          <EcasInboxReadPage />
        </RequireAccess>
      )
    if (application === 'gas' && screenId === 'showAppraisalSearch')
      return (
        <RequireAccess capabilities={[screen.capability]}>
          <GasSearchReadPage />
        </RequireAccess>
      )
  }
  return (
    <RequireAccess capabilities={[screen.capability]}>
      <section className="taps-page">
        <PageHeader
          title={screen.title}
          subtitle={screen.description}
          backLink={<Link to={`/${application}`}>Back to {applications[application].title}</Link>}
        />
        <EmptyState
          title="Page in development"
          description="This page is being modernized. It is not available yet."
          role="status"
        />
      </section>
    </RequireAccess>
  )
}
