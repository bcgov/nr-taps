import { createFileRoute } from '@tanstack/react-router'
import { ApplicationScreenPage } from '@/components/ApplicationPages'

export const Route = createFileRoute('/gas/$screenId')({ component: GasScreen })

function GasScreen() {
  const { screenId } = Route.useParams()
  return <ApplicationScreenPage application="gas" screenId={screenId} />
}
