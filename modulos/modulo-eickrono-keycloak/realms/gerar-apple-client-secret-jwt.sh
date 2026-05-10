#!/bin/sh

set -eu

APPLE_TEAM_ID="${APPLE_TEAM_ID:-}"
APPLE_KEY_ID="${APPLE_KEY_ID:-}"
APPLE_PRIVATE_KEY_P8="${APPLE_PRIVATE_KEY_P8:-}"
APPLE_CLIENT_ID="${APPLE_CLIENT_ID:-${KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_ID:-${KEYCLOAK_IDP_APPLE_CLIENT_ID:-}}}"
APPLE_CLIENT_SECRET_TTL_SECONDS="${APPLE_CLIENT_SECRET_TTL_SECONDS:-15777000}"
OUTPUT_ENV_LINE="${OUTPUT_ENV_LINE:-false}"

if [ -z "$APPLE_TEAM_ID" ]; then
  echo "Variavel obrigatoria ausente: APPLE_TEAM_ID" >&2
  exit 1
fi

if [ -z "$APPLE_KEY_ID" ]; then
  echo "Variavel obrigatoria ausente: APPLE_KEY_ID" >&2
  exit 1
fi

if [ -z "$APPLE_PRIVATE_KEY_P8" ]; then
  echo "Variavel obrigatoria ausente: APPLE_PRIVATE_KEY_P8" >&2
  exit 1
fi

if [ -z "$APPLE_CLIENT_ID" ]; then
  echo "Variavel obrigatoria ausente: APPLE_CLIENT_ID, KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_ID ou KEYCLOAK_IDP_APPLE_CLIENT_ID" >&2
  exit 1
fi

if [ ! -f "$APPLE_PRIVATE_KEY_P8" ]; then
  echo "Arquivo da chave Apple nao encontrado: $APPLE_PRIVATE_KEY_P8" >&2
  exit 1
fi

base64url() {
  openssl base64 -e -A | tr '+/' '-_' | tr -d '='
}

agora="$(date +%s)"
expira_em="$((agora + APPLE_CLIENT_SECRET_TTL_SECONDS))"

cabecalho="$(printf '{"alg":"ES256","kid":"%s"}' "$APPLE_KEY_ID" | base64url)"
payload="$(printf '{"iss":"%s","iat":%s,"exp":%s,"aud":"https://appleid.apple.com","sub":"%s"}' \
  "$APPLE_TEAM_ID" \
  "$agora" \
  "$expira_em" \
  "$APPLE_CLIENT_ID" | base64url)"

assinatura="$(printf '%s.%s' "$cabecalho" "$payload" \
  | openssl dgst -binary -sha256 -sign "$APPLE_PRIVATE_KEY_P8" \
  | base64url)"

jwt="$(printf '%s.%s.%s' "$cabecalho" "$payload" "$assinatura")"

if [ "$OUTPUT_ENV_LINE" = "true" ]; then
  printf 'KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_SECRET_JWT=%s\n' "$jwt"
else
  printf '%s\n' "$jwt"
fi
