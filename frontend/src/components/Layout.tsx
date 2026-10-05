import {
  Dashboard as HomeIcon,
  Document,
  Finance,
  Moon,
  Sun,
  UserAvatar,
} from '@carbon/icons-react'
import { HeaderMenuButton, IconButton, SideNavItems, SkipToContent } from '@carbon/react'
import { Link, useLocation } from '@tanstack/react-router'
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { applications, applicationScreens, type ApplicationId } from '@/application-catalogue'
import { useAuth } from '@/context/auth/AuthContext'
import { useTheme } from '@/context/theme/ThemeContext'
import bcLogo from '@/assets/BCID_H_rgb_pos.png'
import reverseLogo from '@/assets/gov-bc-logo-horiz.png'
import ProfilePanel from './ProfilePanel'
import SideNavigationGroup from './SideNavigationGroup'
import SideNavigationTooltip from './SideNavigationTooltip'
import useMediaQuery from './useMediaQuery'

const COLLAPSED_KEY = 'taps.ui.sideNavCollapsed'

function readCollapsed() {
  try {
    return localStorage.getItem(COLLAPSED_KEY) === 'true'
  } catch {
    return false
  }
}

function Layout({ children }: { children: ReactNode }) {
  const { state, can } = useAuth()
  const { theme, toggleTheme } = useTheme()
  const pathname = useLocation().pathname
  const narrow = useMediaQuery('(max-width: 671px)')
  const [desktopCollapsed, setDesktopCollapsed] = useState(readCollapsed)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [profileOpen, setProfileOpen] = useState(false)
  const profileLauncherRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLButtonElement>(null)
  const mainRef = useRef<HTMLElement>(null)
  const navigationRef = useRef<HTMLElement>(null)
  const previousPathRef = useRef(pathname)
  const hasNavigation = state.kind === 'signed-in' && state.session.roles.length > 0
  const expanded = narrow ? mobileOpen : !desktopCollapsed
  const overlayOpen = hasNavigation && narrow && mobileOpen
  const activeApplication = (Object.keys(applications) as ApplicationId[]).find(
    (app) => pathname.startsWith(`/${app}/`) || pathname === `/${app}`,
  )

  useEffect(() => {
    try {
      localStorage.setItem(COLLAPSED_KEY, String(desktopCollapsed))
    } catch {
      // Navigation preferences are optional.
    }
  }, [desktopCollapsed])

  useEffect(() => {
    const media = window.matchMedia('(max-width: 671px)')
    const close = () => setMobileOpen(false)
    media.addEventListener('change', close)
    return () => media.removeEventListener('change', close)
  }, [])

  useEffect(() => {
    if (previousPathRef.current !== pathname) {
      previousPathRef.current = pathname
      setMobileOpen(false)
      const frame = requestAnimationFrame(() => mainRef.current?.focus())
      return () => cancelAnimationFrame(frame)
    }
  }, [pathname])

  useEffect(() => {
    if (!overlayOpen) return
    const frame = requestAnimationFrame(() => navigationRef.current?.querySelector('a')?.focus())
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !event.defaultPrevented) {
        setMobileOpen(false)
        menuRef.current?.focus()
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => {
      cancelAnimationFrame(frame)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [overlayOpen])

  const closeMobileNavigation = () => {
    setMobileOpen(false)
    menuRef.current?.focus()
  }

  const closeProfile = useCallback((returnFocus = false) => {
    setProfileOpen(false)
    if (returnFocus) requestAnimationFrame(() => profileLauncherRef.current?.focus())
  }, [])

  return (
    <div
      className={`taps-shell${hasNavigation ? ' has-navigation' : ''}${expanded ? ' is-navigation-expanded' : ''}`}
    >
      <SkipToContent href="#main-content" />
      <header className="cds--header taps-header" aria-label="TAPS">
        {hasNavigation && (
          <HeaderMenuButton
            ref={menuRef}
            isCollapsible
            aria-label={expanded ? 'Close menu' : 'Open menu'}
            aria-controls="side-navigation"
            aria-expanded={expanded}
            isActive={expanded}
            onClick={() => {
              closeProfile()
              if (narrow) setMobileOpen((current) => !current)
              else setDesktopCollapsed((current) => !current)
            }}
          />
        )}
        <Link to="/" className="taps-brand" aria-label="TAPS home">
          <span className="taps-brand__name">TAPS</span>
          <span className="taps-brand__title">Timber Appraisal and Pricing System</span>
        </Link>
        <div className="cds--header__global taps-header__actions">
          <div className="taps-header__theme">
            <button
              type="button"
              className="taps-theme-switch"
              role="switch"
              aria-checked={theme === 'g100'}
              aria-label="Dark theme"
              onClick={toggleTheme}
            >
              <span className="taps-theme-switch__thumb" aria-hidden="true">
                {theme === 'g100' ? <Moon size={14} /> : <Sun size={14} />}
              </span>
            </button>
          </div>
          {state.kind === 'signed-in' && (
            <IconButton
              ref={profileLauncherRef}
              kind="ghost"
              label={profileOpen ? 'Close profile panel' : 'Open profile panel'}
              align="bottom-right"
              aria-expanded={profileOpen}
              aria-controls="profile-panel"
              onClick={() => {
                setMobileOpen(false)
                setProfileOpen((current) => !current)
              }}
            >
              <UserAvatar size={20} />
            </IconButton>
          )}
        </div>
      </header>
      <ProfilePanel open={profileOpen} onClose={closeProfile} launcherRef={profileLauncherRef} />
      {hasNavigation && (
        <nav
          ref={navigationRef}
          id="side-navigation"
          className={`cds--side-nav taps-navigation${expanded ? '' : ' is-collapsed'}`}
          aria-label="Side navigation"
        >
          <SideNavItems isSideNavExpanded={expanded}>
            <li>
              <SideNavigationTooltip enabled={!expanded} label="Home">
                {(descriptionId) => (
                  <Link
                    to="/"
                    activeOptions={{ exact: true }}
                    className={`cds--side-nav__link${pathname === '/' ? ' cds--side-nav__link--active' : ''}`}
                    aria-current={pathname === '/' ? 'page' : undefined}
                    aria-describedby={descriptionId}
                    aria-label="Home"
                  >
                    <span className="cds--side-nav__icon" aria-hidden="true">
                      <HomeIcon size={20} />
                    </span>
                    <span className="cds--side-nav__link-text">Home</span>
                  </Link>
                )}
              </SideNavigationTooltip>
            </li>
            {(Object.keys(applications) as ApplicationId[])
              .filter((app) => applications[app].capabilities.some(can))
              .map((app) => (
                <SideNavigationGroup
                  key={`${app}:${pathname}`}
                  label={applications[app].title}
                  icon={app === 'ecas' ? Document : Finance}
                  collapsed={!expanded}
                  active={activeApplication === app}
                  onExpandNavigation={() => {
                    if (narrow) setMobileOpen(true)
                    else setDesktopCollapsed(false)
                  }}
                >
                  <li>
                    <Link
                      to={`/${app}`}
                      activeOptions={{ exact: true }}
                      className="cds--side-nav__link"
                      aria-current={
                        pathname === `/${app}` || pathname === `/${app}/` ? 'page' : undefined
                      }
                    >
                      <span className="cds--side-nav__link-text">
                        {applications[app].title} overview
                      </span>
                    </Link>
                  </li>
                  {applicationScreens
                    .filter((screen) => screen.application === app && can(screen.capability))
                    .map((screen) => (
                      <li key={screen.id}>
                        <Link
                          to={`/${app}/$screenId`}
                          activeOptions={{ exact: true }}
                          params={{ screenId: screen.id }}
                          className="cds--side-nav__link"
                          aria-current={pathname === `/${app}/${screen.id}` ? 'page' : undefined}
                        >
                          <span className="cds--side-nav__link-text">{screen.title}</span>
                        </Link>
                      </li>
                    ))}
                </SideNavigationGroup>
              ))}
          </SideNavItems>
        </nav>
      )}
      {overlayOpen && (
        <button
          className="taps-navigation-backdrop"
          type="button"
          aria-label="Close navigation"
          onClick={closeMobileNavigation}
        />
      )}
      <main id="main-content" ref={mainRef} className="taps-main" tabIndex={-1} inert={overlayOpen}>
        <div className="taps-content">{children}</div>
      </main>
      <footer className="taps-footer" inert={overlayOpen}>
        <img
          src={theme === 'g100' ? reverseLogo : bcLogo}
          alt="Government of British Columbia"
          className="taps-footer__logo"
        />
        <a href="https://www2.gov.bc.ca/gov/content/home">B.C. Government</a>
        <a href="https://www2.gov.bc.ca/gov/content/home/privacy">Privacy</a>
        <a href="https://www2.gov.bc.ca/gov/content/home/accessibility">Accessibility</a>
        <a href="https://www2.gov.bc.ca/gov/content/home/copyright">Copyright</a>
      </footer>
    </div>
  )
}

export default Layout
