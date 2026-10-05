import { Close, Logout } from '@carbon/icons-react'
import { IconButton } from '@carbon/react'
import { useEffect, useRef, type RefObject } from 'react'
import { useAuth } from '@/context/auth/AuthContext'
import { describeGrant } from '@/context/auth/capabilities'

export default function ProfilePanel({
  open,
  onClose,
  launcherRef,
}: {
  open: boolean
  onClose: (returnFocus?: boolean) => void
  launcherRef: RefObject<HTMLButtonElement | null>
}) {
  const { state, logout } = useAuth()
  const panelRef = useRef<HTMLElement>(null)
  const closeRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return
    const frame = requestAnimationFrame(() => closeRef.current?.focus())
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation()
        onClose(true)
      }
    }
    const onPointerDown = (event: PointerEvent) => {
      if (
        event.target instanceof Node &&
        !panelRef.current?.contains(event.target) &&
        !launcherRef.current?.contains(event.target)
      )
        onClose()
    }
    document.addEventListener('keydown', onKeyDown, true)
    document.addEventListener('pointerdown', onPointerDown)
    return () => {
      cancelAnimationFrame(frame)
      document.removeEventListener('keydown', onKeyDown, true)
      document.removeEventListener('pointerdown', onPointerDown)
    }
  }, [open, onClose, launcherRef])

  if (!open || state.kind !== 'signed-in') return null
  const name = state.session.displayName
  const initials = name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase()
  return (
    <aside
      ref={panelRef}
      id="profile-panel"
      className="taps-profile-panel"
      role="dialog"
      aria-modal={false}
      aria-labelledby="profile-title"
    >
      <div className="taps-profile-panel__header">
        <h2 id="profile-title">My profile</h2>
        <IconButton
          ref={closeRef}
          kind="ghost"
          label="Close profile panel"
          align="bottom-right"
          onClick={() => onClose(true)}
        >
          <Close size={20} />
        </IconButton>
      </div>
      <div className="taps-profile-panel__body">
        <div className="taps-profile-panel__identity">
          <div className="taps-profile-avatar" aria-hidden="true">
            {initials}
          </div>
          <div>
            <p className="taps-profile-panel__name">{name}</p>
            {state.session.businessName && <p>{state.session.businessName}</p>}
          </div>
        </div>
        {state.session.roles.length > 0 && (
          <ul className="taps-grants">
            {state.session.roles.map((grant) => (
              <li key={describeGrant(grant)}>{describeGrant(grant)}</li>
            ))}
          </ul>
        )}
      </div>
      <button
        className="taps-profile-panel__signout"
        type="button"
        onClick={() => {
          onClose()
          void logout()
        }}
      >
        <Logout size={16} />
        Sign out
      </button>
    </aside>
  )
}
