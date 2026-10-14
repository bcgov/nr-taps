import type { FC } from 'react'
import { Button } from '@carbon/react'
import { useNavigate } from '@tanstack/react-router'
import EmptyState from './EmptyState'

const NotFound: FC = () => {
  const navigate = useNavigate()
  const buttonClicked = () => {
    navigate({
      to: '/',
    })
  }
  return (
    <section className="taps-not-found">
      <EmptyState
        headingLevel={1}
        title="404"
        description="The page you’re looking for does not exist."
        action={
          <Button kind="tertiary" size="md" onClick={() => buttonClicked()}>
            Back home
          </Button>
        }
      />
    </section>
  )
}

export default NotFound
