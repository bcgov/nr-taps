import { useId, type ReactNode } from 'react'

type PageHeaderProps = {
  title: string
  subtitle?: ReactNode
  actions?: ReactNode
  backLink?: ReactNode
}

export default function PageHeader({ title, subtitle, actions, backLink }: PageHeaderProps) {
  const id = useId()
  return (
    <header className="taps-page-header" aria-labelledby={id}>
      {backLink && <div className="taps-page-header__back">{backLink}</div>}
      <div className="taps-page-header__top">
        <div>
          <h1 id={id}>{title}</h1>
          {subtitle && <p className="taps-page-header__subtitle">{subtitle}</p>}
        </div>
        {actions && (
          <div className="taps-actions" role="group" aria-label="Page actions">
            {actions}
          </div>
        )}
      </div>
    </header>
  )
}
