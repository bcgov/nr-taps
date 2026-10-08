import { createContext, use } from 'react'

export type UiTheme = 'white' | 'g100'

export const ThemeContext = createContext<{
  theme: UiTheme
  toggleTheme: () => void
} | null>(null)

export function useTheme() {
  const value = use(ThemeContext)
  if (!value) throw new Error('useTheme must be used inside ThemeProvider')
  return value
}
