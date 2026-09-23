#!/bin/sh
set -eu

if [ -z "${VITE_OIDC_ISSUER_URI:-}" ] || [ -z "${VITE_OIDC_CLIENT_ID:-}" ]; then
  echo 'VITE_OIDC_ISSUER_URI and VITE_OIDC_CLIENT_ID are required.' >&2
  exit 1
fi

escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

cat > /srv/config.js <<EOF
window.config = {
  VITE_OIDC_ISSUER_URI: "$(escape "$VITE_OIDC_ISSUER_URI")",
  VITE_OIDC_CLIENT_ID: "$(escape "$VITE_OIDC_CLIENT_ID")",
  VITE_OIDC_IDIR_HINT: "$(escape "${VITE_OIDC_IDIR_HINT:-azureidir}")",
  VITE_OIDC_BCEID_HINT: "$(escape "${VITE_OIDC_BCEID_HINT:-bceidbusiness}")"
};
EOF

exec /usr/bin/caddy "$@"
