import { createFileRoute } from '@tanstack/react-router'
import { ApplicationPage } from '@/components/ApplicationPages'

export const Route = createFileRoute('/ecas/')({
  component: () => <ApplicationPage application="ecas" />,
})
