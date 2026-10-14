import { DesignResearch } from '@carbon/pictograms-react'
import { useId, type ReactNode } from 'react'

export default function EmptyState({
  title,
  description,
  action,
  role,
  headingLevel = 2,
}: {
  title: string
  description: ReactNode
  action?: ReactNode
  role?: 'status' | 'alert'
  headingLevel?: 1 | 2 | 3 | 4
}) {
  const id = useId()
  const descriptionId = `${id}-description`
  const Heading = `h${headingLevel}` as 'h1' | 'h2' | 'h3' | 'h4'
  return (
    <section
      className="taps-empty-state"
      aria-labelledby={id}
      aria-describedby={descriptionId}
      role={role}
    >
      <DesignResearch width={48} height={48} aria-hidden="true" />
      <Heading id={id} className="taps-empty-state__title">
        {title}
      </Heading>
      <div id={descriptionId} className="taps-empty-state__description">
        {description}
      </div>
      {action && <div className="taps-actions">{action}</div>}
    </section>
  )
}
