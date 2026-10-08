import { Login } from '@carbon/icons-react'
import { Button, InlineNotification } from '@carbon/react'
import { useLocation } from '@tanstack/react-router'
import { useState } from 'react'
import { useAuth } from '@/context/auth/AuthContext'
import { useTheme } from '@/context/theme/ThemeContext'
import { isOidcConfigured } from '@/service/oidc-service'
import bcLogo from '@/assets/BCID_H_rgb_pos.png'
import reverseLogo from '@/assets/gov-bc-logo-horiz.png'
import landingImage from '@/assets/landing.jpg'

export default function Landing({ expired = false }: { expired?: boolean }) {
  const { login } = useAuth()
  const { theme } = useTheme()
  const destination = useLocation({ select: (location) => location.href })
  const [showExpired, setShowExpired] = useState(expired)
  const configured = isOidcConfigured()
  return (
    <main id="main-content" className="taps-landing">
      <div className="taps-landing__content">
        <img
          src={theme === 'g100' ? reverseLogo : bcLogo}
          alt="Government of British Columbia"
          className="taps-landing__logo"
        />
        <div className="taps-landing__title-group">
          <h1 className="taps-landing__title">TAPS</h1>
          <p className="taps-landing__subtitle">Timber Appraisal and Pricing System</p>
          <p className="taps-landing__description">
            TAPS helps you review appraisal data submissions, worksheets and stumpage rates.
          </p>
        </div>
        {showExpired && (
          <InlineNotification
            className="taps-landing__notice"
            kind="warning"
            lowContrast
            title="You've been logged out"
            subtitle="Your session expired for security reasons. Log in again to continue."
            onCloseButtonClick={() => setShowExpired(false)}
          />
        )}
        <div className="taps-landing__actions">
          <Button
            size="md"
            renderIcon={Login}
            disabled={!configured}
            onClick={() => void login('idir', destination)}
          >
            Log in with IDIR
          </Button>
          <Button
            kind="tertiary"
            size="md"
            renderIcon={Login}
            disabled={!configured}
            onClick={() => void login('business-bceid', destination)}
          >
            Log in with Business BCeID
          </Button>
          {!configured && (
            <p className="taps-landing__help">
              TAPS log in is not configured for this environment. Contact the system administrator.
            </p>
          )}
        </div>
      </div>
      <img src={landingImage} alt="" aria-hidden="true" className="taps-landing__image" />
    </main>
  )
}
