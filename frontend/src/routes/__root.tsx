import { createRootRoute, Outlet, useLocation } from '@tanstack/react-router'
import Layout from '@/components/Layout'
import NotFound from '@/components/NotFound'
import RouteError from '@/components/RouteError'
import AuthProvider from '@/context/auth/AuthProvider'
import ThemeProvider from '@/context/theme/ThemeProvider'
import { AUTH_CALLBACK_PATH } from '@/service/oidc-service'

export const Route = createRootRoute({
  component: Root,
  notFoundComponent: () => <NotFound />,
  errorComponent: () => (
    <ThemeProvider>
      <RouteError />
    </ThemeProvider>
  ),
})

function Root() {
  const isAuthCallback = useLocation().pathname === AUTH_CALLBACK_PATH
  return (
    <ThemeProvider>
      <AuthProvider deferSessionLoad={isAuthCallback}>
        <Layout>
          <Outlet />
        </Layout>
      </AuthProvider>
    </ThemeProvider>
  )
}
