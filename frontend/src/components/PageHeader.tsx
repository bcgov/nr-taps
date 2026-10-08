import { useId, type ReactNode } from 'react'

type PageHeaderProps = {
  title: string
  subtitle?: ReactNode
  status?: ReactNode
  actions?: ReactNode
}

// Pages reachable from the side navigation have no return link. A page one level below its
// parent puts "Back to <parent>" above this header; deeper pages use a breadcrumb instead.
export default function PageHeader({ title, subtitle, status, actions }: PageHeaderProps) {
  const id = useId()
  return (
    <header className="taps-page-header" aria-labelledby={id}>
      <div className="taps-page-header__top">
        <div className="taps-page-header__title-group">
          <h1 id={id}>{title}</h1>
          {status}
        </div>
        {actions && (
          <div className="taps-actions" role="group" aria-label="Page actions">
            {actions}
          </div>
        )}
      </div>
      {subtitle && <p className="taps-page-header__subtitle">{subtitle}</p>}
    </header>
  )
}
