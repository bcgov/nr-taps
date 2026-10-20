import { SidePanel } from '@carbon/ibm-products'
import { useTheme } from '@carbon/react'
import {
  useEffect,
  useLayoutEffect,
  useRef,
  type KeyboardEvent,
  type ReactNode,
  type RefObject,
} from 'react'
import { createPortal } from 'react-dom'
import useMediaQuery from './useMediaQuery'

const SLIDE_OVER_MARKER = 'data-taps-detail-panel-slide-over'
const closesOpenMenu = (target: Element) =>
  target.getAttribute('aria-expanded') === 'true' &&
  (target.getAttribute('role') === 'combobox' || target.hasAttribute('aria-haspopup'))

export default function DetailSidePanel({
  open,
  title,
  children,
  onClose,
  launcherRef,
  initialFocusSelector,
  contentSelector = '#main-content',
  fallbackFocusSelector = '#main-content',
}: {
  open: boolean
  title: string
  children: ReactNode
  onClose: () => void
  launcherRef: RefObject<HTMLElement | null>
  initialFocusSelector: string
  contentSelector?: string
  fallbackFocusSelector?: string
}) {
  const besidePage = useMediaQuery('(min-width: 1312px)')
  const { theme } = useTheme()
  const panelRef = useRef<HTMLDivElement>(null)
  const returnFocusRef = useRef(false)
  const closeRequestedRef = useRef(false)

  useEffect(() => {
    if (open) closeRequestedRef.current = false
  }, [open])

  const requestClose = () => {
    // Ignore repeated close requests before the parent removes the panel.
    if (closeRequestedRef.current) return
    closeRequestedRef.current = true
    onClose()
  }

  useLayoutEffect(() => {
    // Keep long drawer content keyboard-scrollable without trapping focus in the panel.
    const scrollBody = panelRef.current?.querySelector<HTMLElement>('.c4p--side-panel--scrolls')
    if (scrollBody) scrollBody.tabIndex = 0
  }, [open])

  useEffect(() => {
    if (open) {
      returnFocusRef.current = true
      const frame = requestAnimationFrame(() =>
        panelRef.current?.querySelector<HTMLElement>(initialFocusSelector)?.focus(),
      )
      return () => cancelAnimationFrame(frame)
    }
    if (!returnFocusRef.current) return
    returnFocusRef.current = false
    const frame = requestAnimationFrame(() => {
      const launcher = launcherRef.current
      const target = launcher?.isConnected
        ? launcher
        : document.querySelector<HTMLElement>(fallbackFocusSelector)
      target?.focus()
    })
    return () => cancelAnimationFrame(frame)
  }, [open, initialFocusSelector, launcherRef, fallbackFocusSelector])

  useLayoutEffect(() => {
    if (!open || !besidePage) return
    const content = document.querySelector<HTMLElement>(contentSelector)
    if (!content) return
    const properties = ['margin-inline-end', 'inline-size', 'transition']
    const original = properties.map((property) => content.style.getPropertyValue(property))
    return () => {
      // IBM changes page sizing during slide-in; restore it when closing or resizing.
      properties.forEach((property, index) => {
        if (original[index]) content.style.setProperty(property, original[index])
        else content.style.removeProperty(property)
      })
    }
  }, [contentSelector, open, besidePage])

  const panelEscapeTarget = (event: KeyboardEvent<HTMLDivElement>): Element | null => {
    const target = event.target
    return event.key === 'Escape' &&
      !event.defaultPrevented &&
      target instanceof Element &&
      panelRef.current?.contains(target) &&
      !target.closest('[role="dialog"], [role="alertdialog"]')
      ? target
      : null
  }

  const onKeyDownCapture = (event: KeyboardEvent<HTMLDivElement>) => {
    const target = panelEscapeTarget(event)
    // Carbon's closed ListBox can stop native Escape before the bubble handler sees it.
    if (
      target?.getAttribute('role') !== 'combobox' ||
      target.getAttribute('aria-expanded') !== 'false'
    )
      return
    event.stopPropagation()
    requestClose()
  }

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const target = panelEscapeTarget(event)
    if (!target || closesOpenMenu(target)) return
    event.stopPropagation()
    requestClose()
  }

  if (!open) return null
  return createPortal(
    <div
      className={`taps-detail-panel-host cds--${theme} cds--layer-one`}
      onKeyDownCapture={onKeyDownCapture}
      onKeyDown={onKeyDown}
    >
      {!besidePage && <span hidden {...{ [SLIDE_OVER_MARKER]: '' }} />}
      <SidePanel
        ref={panelRef}
        open
        title={title}
        size="md"
        className={`taps-detail-panel${besidePage ? '' : ' taps-detail-panel--over-page'}`}
        slideIn
        selectorPageContent={besidePage ? contentSelector : `[${SLIDE_OVER_MARKER}]`}
        preventCloseOnClickOutside
        animateTitle={false}
        onRequestClose={requestClose}
      >
        {/* IBM treats bubbled child animation events as the panel's opening animation,
            which can reset focus after asynchronous content arrives. Keep child handlers
            running, but stop these events before they reach the panel lifecycle handler. */}
        <div
          onAnimationStart={(event) => event.stopPropagation()}
          onAnimationEnd={(event) => event.stopPropagation()}
        >
          {children}
        </div>
      </SidePanel>
    </div>,
    document.body,
  )
}
