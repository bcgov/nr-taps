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
    // Self-profile only; legacy user administration needs separate permissions.
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
