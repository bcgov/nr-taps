import { InlineNotification, type InlineNotificationProps } from '@carbon/react'

type AppNotificationProps = Pick<InlineNotificationProps, 'kind' | 'title' | 'subtitle' | 'role'>

export default function AppNotification({ kind = 'info', role, ...props }: AppNotificationProps) {
  return (
    <InlineNotification
      {...props}
      kind={kind}
      role={role ?? (kind === 'error' ? 'alert' : 'status')}
      lowContrast
      hideCloseButton
      className="taps-notification"
    />
  )
}
