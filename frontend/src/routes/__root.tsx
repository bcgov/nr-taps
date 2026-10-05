import { createRootRoute, ErrorComponent, Outlet, useLocation } from '@tanstack/react-router'
import Layout from '@/components/Layout'
import NotFound from '@/components/NotFound'
import AuthProvider from '@/context/auth/AuthProvider'
import ThemeProvider from '@/context/theme/ThemeProvider'
import { AUTH_CALLBACK_PATH } from '@/service/oidc-service'

export const Route = createRootRoute({
  component: Root,
  notFoundComponent: () => <NotFound />,
  errorComponent: ({ error }) => <ErrorComponent error={error} />,
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
