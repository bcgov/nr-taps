import { createFileRoute } from '@tanstack/react-router'
import { ApplicationPage } from '@/components/ApplicationPages'

export const Route = createFileRoute('/gas/')({
  component: () => <ApplicationPage application="gas" />,
})
