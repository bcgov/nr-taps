import '@bcgov/bc-sans/css/BC_Sans.css'
import '@/scss/styles.scss'
import './local-synthetic.scss'
import { Select, SelectItem } from '@carbon/react'
import {
  Outlet,
  RouterProvider,
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  useLocation,
} from '@tanstack/react-router'
import { StrictMode, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { ApplicationPage, ApplicationScreenPage } from '@/components/ApplicationPages'
import Dashboard from '@/components/Dashboard'
import Layout from '@/components/Layout'
import NotFound from '@/components/NotFound'
import RouteError from '@/components/RouteError'
import RequireAccess from '@/components/RequireAccess'
import { AuthContext, type AuthContextValue, type AuthState } from '@/context/auth/AuthContext'
import { Capability } from '@/context/auth/capabilities'
import ThemeProvider from '@/context/theme/ThemeProvider'
import type { Session } from '@/service/session-service'
import ControlsSample from './ControlsSample'
import WorkflowPreview from './WorkflowPreview'
import ReadFeedbackPreview from './ReadFeedbackPreview'
import AsyncReadPreview, {
  SyntheticEcasInboxPage,
  SyntheticGasSearchPage,
} from './AsyncReadPreview'

const scenarios = [
  { id: 'signed-out', label: 'Synthetic signed-out session' },
  { id: 'signed-out-expired', label: 'Synthetic expired signed-out session' },
  { id: 'loading', label: 'Synthetic loading session' },
  { id: 'error', label: 'Synthetic session error' },
  { id: 'no-role', label: 'Synthetic signed-in session without a role' },
  { id: 'staff', label: 'Synthetic staff: ECAS and GAS' },
  { id: 'ecas', label: 'Synthetic ECAS-only session' },
  { id: 'long-identity', label: 'Synthetic long name and organization' },
  { id: 'gas-reports', label: 'Synthetic GAS client-report-only session' },
] as const

type Scenario = (typeof scenarios)[number]['id']

const previewPaths = [
  { path: '/', label: 'TAPS dashboard' },
  { path: '/ecas', label: 'ECAS module' },
  { path: '/ecas/ECAS05', label: 'ECAS Inbox Search' },
  { path: '/ecas/ECAS88', label: 'ECAS profile shell' },
  { path: '/gas', label: 'GAS module' },
  { path: '/gas/showAppraisalSearch', label: 'GAS Appraisal Search' },
  { path: '/gas/showAppraisedSummary', label: 'GAS appraised summary shell' },
  { path: '/gas/showNonAppraisedSummary', label: 'GAS non-appraised summary shell' },
  { path: '/ui-controls', label: 'Synthetic reusable controls' },
  { path: '/workflow', label: 'Synthetic ECAS and GAS workflow' },
  { path: '/async-reads', label: 'Synthetic asynchronous read pages' },
  { path: '/read-feedback', label: 'Synthetic read feedback' },
  { path: '/not-a-page', label: 'Page not found' },
  { path: '/route-error', label: 'Synthetic route failure' },
]

const previewHistory = createMemoryHistory({ initialEntries: ['/'] })

function syntheticState(scenario: Scenario): AuthState {
  if (scenario === 'signed-out' || scenario === 'loading') return { kind: scenario }
  if (scenario === 'signed-out-expired') return { kind: 'signed-out', expired: true }
  if (scenario === 'error') {
    return { kind: 'error', message: 'Synthetic session error. No service request was made.' }
  }

  const capabilities =
    scenario === 'staff'
      ? Object.values(Capability)
      : scenario === 'ecas' || scenario === 'long-identity'
        ? Object.values(Capability).filter((capability) => capability.startsWith('ECAS_'))
        : scenario === 'gas-reports'
          ? [Capability.GasClientReports]
          : []
  const session: Session = {
    ecasMyToDoAvailable: scenario === 'staff',
    userId: 'synthetic-preview-user',
    displayName:
      scenario === 'long-identity'
        ? 'SyntheticLongNameWithoutSpacesForProfileWrappingValidation'
        : 'Synthetic preview user',
    email: null,
    identityProvider: scenario === 'staff' ? 'IDIR' : 'BCEID_BUSINESS',
    businessName:
      scenario === 'staff'
        ? null
        : scenario === 'long-identity'
          ? 'SyntheticOrganizationWithAnIntentionallyUnbrokenNameForLayoutReview'
          : 'Synthetic preview organization',
    roles:
      scenario === 'no-role'
        ? []
        : [
            {
              role: scenario === 'staff' ? 'TAPS_ADMIN' : 'TAPS_LICENSEE_VIEWER',
              scopes: [],
            },
          ],
    capabilities,
    forestClients: [],
  }
  return { kind: 'signed-in', session }
}

function PreviewRoot() {
  const [scenario, setScenario] = useState<Scenario>('staff')
  const pathname = useLocation().pathname.replace(/\/$/, '') || '/'
  const auth = useMemo<AuthContextValue>(() => {
    const state = syntheticState(scenario)
    return {
      state,
      can: (capability) =>
        state.kind === 'signed-in' &&
        state.session.roles.length > 0 &&
        state.session.capabilities.includes(capability),
      reloadSession: async () => setScenario('staff'),
      login: async (provider) => setScenario(provider === 'idir' ? 'staff' : 'ecas'),
      logout: async () => setScenario('signed-out'),
    }
  }, [scenario])

  const banner = (
    <details className="taps-preview-banner" aria-label="Local synthetic preview controls">
      <summary>Local synthetic UI preview</summary>
      <p>
        All accounts and sample rows are synthetic. Session controls only change this preview; they
        do not log in or call TAPS services. The My to do list sample is empty; an ECAS ID lookup
        still finds sample records.
      </p>
      <div className="taps-preview-toolbar">
        <Select
          id="synthetic-session"
          labelText="Synthetic session"
          value={scenario}
          onChange={(event) => setScenario(event.target.value as Scenario)}
        >
          {scenarios.map((item) => (
            <SelectItem key={item.id} value={item.id} text={item.label} />
          ))}
        </Select>
        <Select
          id="synthetic-route"
          labelText="Preview page"
          value={previewPaths.some((item) => item.path === pathname) ? pathname : '/'}
          onChange={(event) => previewHistory.push(event.target.value)}
        >
          {previewPaths.map((item) => (
            <SelectItem key={item.path} value={item.path} text={item.label} />
          ))}
        </Select>
      </div>
    </details>
  )

  return (
    <ThemeProvider>
      <AuthContext value={auth}>
        {/* Signed-out sessions get the login page without the shell, so keep the controls above it. */}
        {auth.state.kind === 'signed-out' && (
          <div className="taps-preview-landing-controls">{banner}</div>
        )}
        <Layout loginConfigured>
          <div className="taps-preview-workspace">
            {auth.state.kind !== 'signed-out' && banner}
            <Outlet />
          </div>
        </Layout>
      </AuthContext>
    </ThemeProvider>
  )
}

export function createPreviewRouter() {
  const rootRoute = createRootRoute({
    component: PreviewRoot,
    notFoundComponent: NotFound,
    errorComponent: () => (
      <ThemeProvider>
        <RouteError />
      </ThemeProvider>
    ),
  })
  const indexRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/',
    component: Dashboard,
  })
  const ecasRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/ecas',
    component: () => <ApplicationPage application="ecas" />,
  })
  const gasRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/gas',
    component: () => <ApplicationPage application="gas" />,
  })
  const ecasScreenRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/ecas/$screenId',
    component: () => {
      const { screenId } = ecasScreenRoute.useParams()
      return screenId === 'ECAS05' ? (
        <RequireAccess capabilities={[Capability.EcasSubmissionView]}>
          <SyntheticEcasInboxPage />
        </RequireAccess>
      ) : (
        <ApplicationScreenPage application="ecas" screenId={screenId} />
      )
    },
  })
  const gasScreenRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/gas/$screenId',
    component: () => {
      const { screenId } = gasScreenRoute.useParams()
      return screenId === 'showAppraisalSearch' ? (
        <RequireAccess capabilities={[Capability.GasAppraisalView]}>
          <SyntheticGasSearchPage />
        </RequireAccess>
      ) : (
        <ApplicationScreenPage application="gas" screenId={screenId} />
      )
    },
  })
  const controlsRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/ui-controls',
    component: ControlsSample,
  })
  const workflowRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/workflow',
    component: WorkflowPreview,
  })
  const readsRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/async-reads',
    component: AsyncReadPreview,
  })
  const failureRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/route-error',
    component: () => {
      throw new Error('Synthetic preview route failure')
    },
  })
  const feedbackRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/read-feedback',
    component: ReadFeedbackPreview,
  })

  return createRouter({
    history: previewHistory,
    routeTree: rootRoute.addChildren([
      indexRoute,
      ecasRoute,
      ecasScreenRoute,
      gasRoute,
      gasScreenRoute,
      controlsRoute,
      workflowRoute,
      readsRoute,
      feedbackRoute,
      failureRoute,
    ]),
  })
}

export function mountLocalSyntheticPreview() {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <RouterProvider router={createPreviewRouter()} />
    </StrictMode>,
  )
}
