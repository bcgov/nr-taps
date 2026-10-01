import { Capability } from '@/context/auth/capabilities'

export type ApplicationId = 'ecas' | 'gas'

export const applications = {
  ecas: {
    title: 'ECAS',
    description: 'Appraisal data submissions',
    capabilities: Object.values(Capability).filter((capability) => capability.startsWith('ECAS_')),
  },
  gas: {
    title: 'GAS',
    description: 'Appraisal worksheets and stumpage rates',
    capabilities: Object.values(Capability).filter((capability) => capability.startsWith('GAS_')),
  },
} as const

type ApplicationScreen = {
  application: ApplicationId
  id: string
  title: string
  description: string
  capability: Capability
}

// Initial shells verified against ECAS/GAS action, Tiles and JSP definitions. This list only
// contains the pages available in the UI.
export const applicationScreens: readonly ApplicationScreen[] = [
  {
    application: 'ecas',
    id: 'ECAS05',
    title: 'Inbox Search',
    description: 'Find and review appraisal data submissions.',
    capability: Capability.EcasSubmissionView,
  },
  {
    application: 'ecas',
    id: 'ECAS88',
    title: 'Your profile',
    description: 'Your ECAS contact information and notification preferences.',
    // The legacy Maintain Users screen also has an administrator mode. This shell is self-profile
    // only; user administration will require its own backend permission and endpoints.
    capability: Capability.EcasSubmissionView,
  },
  {
    application: 'gas',
    id: 'showAppraisalSearch',
    title: 'Appraisal Search',
    description: 'Find appraisal worksheets.',
    capability: Capability.GasAppraisalView,
  },
  {
    application: 'gas',
    id: 'showAppraisedSummary',
    title: 'Summary - Appraised Worksheet',
    description: 'Review an appraised worksheet summary.',
    capability: Capability.GasAppraisalView,
  },
  {
    application: 'gas',
    id: 'showNonAppraisedSummary',
    title: 'Summary - Non-Appraised Rates',
    description: 'Review non-appraised worksheet rates.',
    capability: Capability.GasAppraisalView,
  },
]
