import { DesignResearch } from '@carbon/pictograms-react'
import { useId, type ReactNode } from 'react'

export default function EmptyState({
  title,
  description,
  action,
  role,
}: {
  title: string
  description: ReactNode
  action?: ReactNode
  role?: 'status'
}) {
  const id = useId()
  return (
    <section className="taps-empty-state" aria-labelledby={id} role={role}>
      <DesignResearch width={64} height={64} aria-hidden="true" />
      <h2 id={id}>{title}</h2>
      <div className="taps-empty-state__description">{description}</div>
      {action && <div className="taps-actions">{action}</div>}
    </section>
  )
}
