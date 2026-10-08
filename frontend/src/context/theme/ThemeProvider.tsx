import { Theme } from '@carbon/react'
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { ThemeContext, type UiTheme } from './ThemeContext'

const PREFERENCE_KEY = 'taps.ui.theme'

function readTheme(): UiTheme {
  try {
    return localStorage.getItem(PREFERENCE_KEY) === 'g100' ? 'g100' : 'white'
  } catch {
    return 'white'
  }
}

export default function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<UiTheme>(readTheme)
  const previousRootThemeRef = useRef(document.documentElement.getAttribute('data-carbon-theme'))

  useEffect(() => {
    document.documentElement.setAttribute('data-carbon-theme', theme)
    try {
      localStorage.setItem(PREFERENCE_KEY, theme)
    } catch {
      // The theme still works when preference storage is unavailable.
    }
  }, [theme])

  useEffect(() => {
    const previous = previousRootThemeRef.current
    return () => {
      if (previous === null) document.documentElement.removeAttribute('data-carbon-theme')
      else document.documentElement.setAttribute('data-carbon-theme', previous)
    }
  }, [])

  const toggleTheme = useCallback(() => {
    setTheme((current) => (current === 'white' ? 'g100' : 'white'))
  }, [])
  const value = useMemo(() => ({ theme, toggleTheme }), [theme, toggleTheme])

  return (
    <ThemeContext value={value}>
      <Theme theme={theme}>{children}</Theme>
    </ThemeContext>
  )
}
