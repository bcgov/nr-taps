import { ChevronDown, type CarbonIconType } from '@carbon/icons-react'
import { useId, useRef, useState, type ReactNode } from 'react'
import SideNavigationTooltip from './SideNavigationTooltip'

export default function SideNavigationGroup({
  label,
  icon: Icon,
  collapsed,
  active,
  activePage,
  onExpandNavigation,
  children,
}: {
  label: string
  icon: CarbonIconType
  collapsed: boolean
  active: boolean
  // The title of the current page when this group holds it.
  activePage?: string
  onExpandNavigation: () => void
  children: ReactNode
}) {
  const id = useId()
  const buttonRef = useRef<HTMLButtonElement>(null)
  const [open, setOpen] = useState(active)
  const expanded = !collapsed && open
  const currentGroup = active && !expanded
  return (
    <li
      className={`cds--side-nav__item${currentGroup ? ' cds--side-nav__item--active' : ''}`}
      onKeyDown={(event) => {
        if (event.key === 'Escape' && expanded) {
          event.stopPropagation()
          setOpen(false)
          buttonRef.current?.focus()
        }
      }}
    >
      <SideNavigationTooltip
        enabled={collapsed}
        label={activePage ? `${label}: ${activePage}` : label}
      >
        {(descriptionId) => (
          <button
            ref={buttonRef}
            type="button"
            className="cds--side-nav__submenu"
            aria-label={label}
            aria-expanded={expanded}
            aria-controls={id}
            aria-current={currentGroup ? 'true' : undefined}
            aria-description={
              currentGroup && activePage ? `Contains current page: ${activePage}` : undefined
            }
            aria-describedby={descriptionId}
            onClick={() => {
              if (collapsed) {
                setOpen(true)
                onExpandNavigation()
              } else setOpen((current) => !current)
            }}
          >
            <span className="cds--side-nav__icon" aria-hidden="true">
              <Icon size={20} />
            </span>
            <span className="cds--side-nav__submenu-title">{label}</span>
            <span className="cds--side-nav__submenu-chevron" aria-hidden="true">
              <ChevronDown size={20} />
            </span>
          </button>
        )}
      </SideNavigationTooltip>
      <ul
        id={id}
        className="cds--side-nav__menu"
        hidden={!expanded}
        style={{ display: expanded ? undefined : 'none' }}
      >
        {children}
      </ul>
    </li>
  )
}
