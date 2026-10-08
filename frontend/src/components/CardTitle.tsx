import type { CarbonIconType } from '@carbon/icons-react'
import type { ReactNode } from 'react'

/** A card's title: an h2 in heading-03, led by a 24px icon. */
export default function CardTitle({
  icon: Icon,
  id,
  children,
}: {
  icon?: CarbonIconType
  id?: string
  children: ReactNode
}) {
  return (
    <h2 id={id} className="taps-card-title">
      {Icon && <Icon size={24} aria-hidden="true" />}
      {children}
    </h2>
  )
}
