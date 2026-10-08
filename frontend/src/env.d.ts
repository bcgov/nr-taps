interface ImportMetaEnv {
  readonly VITE_OIDC_ISSUER_URI?: string
  readonly VITE_OIDC_CLIENT_ID?: string
  readonly VITE_OIDC_IDIR_HINT?: string
  readonly VITE_OIDC_BCEID_HINT?: string
  readonly VITE_OIDC_SITEMINDER_LOGOUT_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
