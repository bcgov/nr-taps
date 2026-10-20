import { RouterProvider } from '@tanstack/react-router'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

// Vite's local serve flag is unrelated to the OpenShift DEV environment.
if (import.meta.env.DEV) {
  void import('./LocalSyntheticPreview').then(({ createPreviewRouter }) =>
    createRoot(document.getElementById('root')!).render(
      <StrictMode>
        <RouterProvider router={createPreviewRouter()} />
      </StrictMode>,
    ),
  )
} else {
  document.getElementById('root')!.textContent = 'The synthetic UI preview is local-only.'
}
