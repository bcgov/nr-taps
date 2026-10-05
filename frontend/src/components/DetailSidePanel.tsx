import { SidePanel } from '@carbon/ibm-products'
import { useTheme } from '@carbon/react'
import { useEffect, useLayoutEffect, useRef, type ReactNode, type RefObject } from 'react'
import { createPortal } from 'react-dom'
import useMediaQuery from './useMediaQuery'

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
  const slideIn = useMediaQuery('(min-width: 1312px)')
  const { theme } = useTheme()
  const hostRef = useRef<HTMLDivElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const returnFocusRef = useRef(false)
  const closeRequestedRef = useRef(false)

  useEffect(() => {
    if (open) closeRequestedRef.current = false
  }, [open])

  const requestClose = () => {
    // IBM's overlay handles Escape at both the panel and window levels.
    if (closeRequestedRef.current) return
    closeRequestedRef.current = true
    onClose()
  }

  useLayoutEffect(() => {
    // IBM includes the scroll body in its Tab wrap; it needs to accept focus.
    const scrollBody = panelRef.current?.querySelector<HTMLElement>('.c4p--side-panel--scrolls')
    if (scrollBody) scrollBody.tabIndex = 0
  }, [open])

  useLayoutEffect(() => {
    if (!open || slideIn || !hostRef.current) return
    // The portal stays outside Layout's inert main content. Isolate the whole
    // shell, including its header/navigation, while the drawer is modal.
    const background = [...document.body.children]
      .filter((element) => element !== hostRef.current)
      .map((element) => ({ element, inert: element.getAttribute('inert') }))
    background.forEach(({ element }) => element.setAttribute('inert', ''))
    return () => {
      background.forEach(({ element, inert }) => {
        if (inert === null) element.removeAttribute('inert')
        else element.setAttribute('inert', inert)
      })
    }
  }, [open, slideIn])

  useEffect(() => {
    if (open) {
      const opening = !returnFocusRef.current
      returnFocusRef.current = true
      const frame = requestAnimationFrame(() => {
        // After a resize, bring background focus into the modal drawer while
        // preserving focus (and edited content) already inside the panel.
        if (opening || (!slideIn && !panelRef.current?.contains(document.activeElement))) {
          panelRef.current?.querySelector<HTMLElement>(initialFocusSelector)?.focus()
        }
      })
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
  }, [open, slideIn, initialFocusSelector, launcherRef, fallbackFocusSelector])

  useLayoutEffect(() => {
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
  }, [contentSelector, open, slideIn])

  if (!open) return null
  return createPortal(
    <div
      ref={hostRef}
      className={`taps-detail-panel-host cds--${theme} cds--layer-one${slideIn ? '' : ' is-modal'}`}
      onKeyDown={(event) => {
        if (slideIn && event.key === 'Escape' && !event.defaultPrevented) {
          event.stopPropagation()
          requestClose()
        }
      }}
    >
      <SidePanel
        ref={panelRef}
        open
        title={title}
        size="md"
        className="taps-detail-panel"
        {...(slideIn ? {} : { role: 'dialog', 'aria-modal': true })}
        slideIn={slideIn}
        selectorPageContent={contentSelector}
        selectorPrimaryFocus={initialFocusSelector}
        includeOverlay={!slideIn}
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
