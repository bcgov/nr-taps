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
import { AuthContext, type AuthContextValue, type AuthState } from '@/context/auth/AuthContext'
import { Capability } from '@/context/auth/capabilities'
import ThemeProvider from '@/context/theme/ThemeProvider'
import type { Session } from '@/service/session-service'
import ControlsSample from './ControlsSample'
import WorkflowPreview from './WorkflowPreview'
import AsyncReadPreview from './AsyncReadPreview'

const scenarios = [
  { id: 'signed-out', label: 'Synthetic signed-out session' },
  { id: 'loading', label: 'Synthetic loading session' },
  { id: 'error', label: 'Synthetic session error' },
  { id: 'no-role', label: 'Synthetic signed-in session without a role' },
  { id: 'staff', label: 'Synthetic staff: ECAS and GAS' },
  { id: 'ecas', label: 'Synthetic ECAS-only session' },
  { id: 'gas-reports', label: 'Synthetic GAS client-report-only session' },
] as const

type Scenario = (typeof scenarios)[number]['id']

const previewPaths = [
  { path: '/', label: 'TAPS dashboard' },
  { path: '/ecas', label: 'ECAS module' },
  { path: '/ecas/ECAS05', label: 'ECAS Inbox Search shell' },
  { path: '/ecas/ECAS88', label: 'ECAS profile shell' },
  { path: '/gas', label: 'GAS module' },
  { path: '/gas/showAppraisalSearch', label: 'GAS Appraisal Search shell' },
  { path: '/gas/showAppraisedSummary', label: 'GAS appraised summary shell' },
  { path: '/gas/showNonAppraisedSummary', label: 'GAS non-appraised summary shell' },
  { path: '/ui-controls', label: 'Synthetic reusable controls' },
  { path: '/workflow', label: 'Synthetic ECAS and GAS workflow' },
  { path: '/async-reads', label: 'Synthetic asynchronous read pages' },
]

const previewHistory = createMemoryHistory({ initialEntries: ['/'] })

function syntheticState(scenario: Scenario): AuthState {
  if (scenario === 'signed-out' || scenario === 'loading') return { kind: scenario }
  if (scenario === 'error') {
    return { kind: 'error', message: 'Synthetic session error. No service request was made.' }
  }

  const capabilities =
    scenario === 'staff'
      ? Object.values(Capability)
      : scenario === 'ecas'
        ? Object.values(Capability).filter((capability) => capability.startsWith('ECAS_'))
        : scenario === 'gas-reports'
          ? [Capability.GasClientReports]
          : []
  const session: Session = {
    ecasMyToDoAvailable: scenario === 'staff',
    userId: 'synthetic-preview-user',
    displayName: 'Synthetic preview user',
    email: null,
    identityProvider: scenario === 'staff' ? 'IDIR' : 'BCEID_BUSINESS',
    businessName: scenario === 'staff' ? null : 'Synthetic preview organization',
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
        {scenario === 'signed-out' && <div className="taps-preview-landing-controls">{banner}</div>}
        <Layout>
          <div className="taps-preview-workspace">
            {scenario !== 'signed-out' && banner}
            <Outlet />
          </div>
        </Layout>
      </AuthContext>
    </ThemeProvider>
  )
}

function createPreviewRouter() {
  const rootRoute = createRootRoute({
    component: PreviewRoot,
    notFoundComponent: NotFound,
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
    component: () => (
      <ApplicationScreenPage application="ecas" screenId={ecasScreenRoute.useParams().screenId} />
    ),
  })
  const gasScreenRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: '/gas/$screenId',
    component: () => (
      <ApplicationScreenPage application="gas" screenId={gasScreenRoute.useParams().screenId} />
    ),
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
