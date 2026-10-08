if (import.meta.env.DEV) {
  void import('./UiPreview').then(({ mountUiPreview }) => mountUiPreview())
} else {
  document.getElementById('root')!.textContent = 'The synthetic UI preview is local-only.'
}

export {}
