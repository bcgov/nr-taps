import { Popover, PopoverContent } from '@carbon/react'
import { useId, useState, type ReactNode } from 'react'

export default function SideNavigationTooltip({
  enabled,
  label,
  children,
}: {
  enabled: boolean
  label: string
  children: (descriptionId: string | undefined) => ReactNode
}) {
  const id = useId()
  const [hovered, setHovered] = useState(false)
  const [focused, setFocused] = useState(false)
  const open = enabled && (hovered || focused)
  const close = () => {
    setHovered(false)
    setFocused(false)
  }
  return (
    <Popover
      as="div"
      className="cds--tooltip cds--icon-tooltip taps-navigation-tooltip"
      align="right"
      autoAlign
      highContrast
      dropShadow={false}
      open={open}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onFocus={() => setFocused(true)}
      onBlur={() => setFocused(false)}
      onClick={close}
      onRequestClose={close}
      onKeyDown={(event) => {
        if (event.key === 'Escape' && open) {
          event.stopPropagation()
          close()
        }
      }}
    >
      <div className="taps-navigation-tooltip__trigger">{children(open ? id : undefined)}</div>
      <PopoverContent id={id} role="tooltip" aria-hidden={!open} className="cds--tooltip-content">
        {label}
      </PopoverContent>
    </Popover>
  )
}
