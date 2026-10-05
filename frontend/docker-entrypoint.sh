#!/bin/sh
# Writes /srv/config.js from VITE_* env vars at container start. index.html loads /config.js
# before the app bundle, and src/env.ts prefers window.config over import.meta.env.
set -eu

require_non_blank() {
  variable_name="$1"
  variable_value="$2"
  normalized_value="$(printf '%s' "${variable_value}" | tr -d '[:space:]')"
  if [ -z "${normalized_value}" ]; then
    echo "${variable_name} is required for TAPS sign-in." >&2
    exit 1
  fi
}

# Report only the missing name, never a configured value.
require_non_blank "VITE_OIDC_ISSUER_URI" "${VITE_OIDC_ISSUER_URI:-}"
require_non_blank "VITE_OIDC_CLIENT_ID" "${VITE_OIDC_CLIENT_ID:-}"

CONFIG_FILE=/srv/config.js

escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

cat > "$CONFIG_FILE" <<EOF
// Generated at container start by docker-entrypoint.sh from VITE_* env vars.
window.config = {
  VITE_OIDC_ISSUER_URI: "$(escape "${VITE_OIDC_ISSUER_URI:-}")",
  VITE_OIDC_CLIENT_ID: "$(escape "${VITE_OIDC_CLIENT_ID:-}")",
  VITE_OIDC_IDIR_HINT: "$(escape "${VITE_OIDC_IDIR_HINT:-azureidir}")",
  VITE_OIDC_BCEID_HINT: "$(escape "${VITE_OIDC_BCEID_HINT:-bceidbusiness}")",
  VITE_OIDC_SITEMINDER_LOGOUT_URL: "$(escape "${VITE_OIDC_SITEMINDER_LOGOUT_URL:-}")"
};
EOF

exec /usr/bin/caddy "$@"
