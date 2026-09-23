declare global {
  interface Window {
    config?: Record<string, string>
  }
}

export function env(name: string): string {
  return window.config?.[name] || String(import.meta.env[name] || '')
}
