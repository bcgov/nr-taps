import { Button } from '@carbon/react'
import EmptyState from './EmptyState'

export default function RouteError({
  onReload = () => window.location.reload(),
}: {
  onReload?: () => void
}) {
  return (
    <main id="main-content" className="taps-not-found taps-route-error">
      <EmptyState
        headingLevel={1}
        role="alert"
        title="Page could not be loaded"
        description="TAPS could not load this page. Reload and try again."
        action={
          <Button size="md" onClick={onReload}>
            Reload application
          </Button>
        }
      />
    </main>
  )
}
