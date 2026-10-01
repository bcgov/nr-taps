import { createFileRoute } from '@tanstack/react-router'
import { ApplicationScreenPage } from '@/components/ApplicationPages'

export const Route = createFileRoute('/ecas/$screenId')({ component: EcasScreen })

function EcasScreen() {
  const { screenId } = Route.useParams()
  return <ApplicationScreenPage application="ecas" screenId={screenId} />
}
