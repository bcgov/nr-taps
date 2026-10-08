import type { FC } from 'react'
import { Button } from '@carbon/react'
import { useNavigate } from '@tanstack/react-router'
import PageHeader from './PageHeader'
import EmptyState from './EmptyState'

const NotFound: FC = () => {
  const navigate = useNavigate()
  const buttonClicked = () => {
    navigate({
      to: '/',
    })
  }
  return (
    <section className="taps-page">
      <PageHeader title="404" />
      <EmptyState
        title="Page not found"
        description="The page you’re looking for does not exist."
        action={
          <Button size="md" onClick={() => buttonClicked()}>
            Back home
          </Button>
        }
      />
    </section>
  )
}

export default NotFound
