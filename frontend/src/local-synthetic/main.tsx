// Vite's local serve flag is unrelated to the OpenShift DEV environment.
if (import.meta.env.DEV) {
  void import('./LocalSyntheticPreview').then(({ mountLocalSyntheticPreview }) =>
    mountLocalSyntheticPreview(),
  )
} else {
  document.getElementById('root')!.textContent = 'The synthetic UI preview is local-only.'
}

export {}
