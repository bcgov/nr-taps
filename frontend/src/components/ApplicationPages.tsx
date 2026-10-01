import { Link } from '@tanstack/react-router'
import { applications, applicationScreens, type ApplicationId } from '@/application-catalogue'
import { useAuth } from '@/context/auth/AuthContext'
import RequireAccess from '@/components/RequireAccess'
import NotFound from '@/components/NotFound'

export function ApplicationPage({ application }: { application: ApplicationId }) {
  const { can } = useAuth()
  const app = applications[application]
  const screens = applicationScreens.filter(
    (screen) => screen.application === application && can(screen.capability),
  )
  return (
    <RequireAccess capabilities={app.capabilities}>
      <main className="container taps-content">
        <Link to="/">TAPS home</Link>
        <h1 className="mt-3">{app.title}</h1>
        <p>{app.description}</p>
        <h2 className="h4">Pages</h2>
        {screens.length ? (
          <ul className="list-unstyled taps-page-list">
            {screens.map((screen) => (
              <li key={screen.id}>
                <Link to={`/${application}/$screenId`} params={{ screenId: screen.id }}>
                  {screen.title}
                </Link>
                <p className="mb-0">{screen.description}</p>
              </li>
            ))}
          </ul>
        ) : (
          <p role="status">Your {app.title} pages will appear here as they become available.</p>
        )}
      </main>
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
  const screen = applicationScreens.find(
    (entry) => entry.application === application && entry.id === screenId,
  )
  if (!screen) return <NotFound />
  return (
    <RequireAccess capabilities={[screen.capability]}>
      <main className="container taps-content">
        <Link to={`/${application}`}>Back to {applications[application].title}</Link>
        <h1 className="mt-3">{screen.title}</h1>
        <p>{screen.description}</p>
        <div className="alert alert-info" role="status">
          This page is being modernized. It is not available yet.
        </div>
      </main>
    </RequireAccess>
  )
}
