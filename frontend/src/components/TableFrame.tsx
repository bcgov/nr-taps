import { useEffect, useRef, type ReactNode } from 'react'

export default function TableFrame({
  ariaLabel,
  children,
  busy = false,
}: {
  ariaLabel: string
  children: ReactNode
  busy?: boolean
}) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const frame = ref.current
    if (!frame) return
    const update = () => {
      if (frame.scrollWidth > frame.clientWidth + 1) frame.tabIndex = 0
      else frame.removeAttribute('tabindex')
    }
    update()
    const observer = new ResizeObserver(update)
    observer.observe(frame)
    const table = frame.querySelector('table')
    if (table) observer.observe(table)
    window.addEventListener('resize', update)
    return () => {
      observer.disconnect()
      window.removeEventListener('resize', update)
    }
  }, [children])
  return (
    <div
      ref={ref}
      className="taps-table-frame"
      role="region"
      aria-label={ariaLabel}
      aria-busy={busy}
    >
      {children}
    </div>
  )
}
