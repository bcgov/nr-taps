if (import.meta.env.DEV) {
  void import('./UiPreview').then(({ mountUiPreview }) => mountUiPreview())
} else {
  document.getElementById('root')!.textContent = 'The synthetic UI preview is development-only.'
}

export {}
