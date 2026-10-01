import { createRootRoute, ErrorComponent, Outlet, useLocation } from '@tanstack/react-router'
import Layout from '@/components/Layout'
import NotFound from '@/components/NotFound'
import AuthProvider from '@/context/auth/AuthProvider'
import { AUTH_CALLBACK_PATH } from '@/service/oidc-service'

export const Route = createRootRoute({
  component: Root,
  notFoundComponent: () => <NotFound />,
  errorComponent: ({ error }) => <ErrorComponent error={error} />,
})

function Root() {
  const isAuthCallback = useLocation().pathname === AUTH_CALLBACK_PATH
  return (
    <AuthProvider deferSessionLoad={isAuthCallback}>
      <Layout>
        <Outlet />
      </Layout>
    </AuthProvider>
  )
}
