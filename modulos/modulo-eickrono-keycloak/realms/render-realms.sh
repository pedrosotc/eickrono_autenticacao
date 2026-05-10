#!/bin/sh

set -eu

SOURCE_DIR="${SOURCE_DIR:-/opt/keycloak/import-source}"
TARGET_DIR="${TARGET_DIR:-/opt/keycloak/data/import}"
KEYCLOAK_BIN="${KEYCLOAK_BIN:-/opt/keycloak/bin/kc.sh}"

escape_sed_replacement() {
  printf '%s' "$1" | sed -e 's/[\\/&|]/\\&/g'
}

replace_placeholder() {
  arquivo="$1"
  chave="$2"
  chave_legada="${3:-}"
  valor="$(printenv "$chave" || true)"

  if [ -z "$valor" ] && [ -n "$chave_legada" ]; then
    valor="$(printenv "$chave_legada" || true)"
  fi

  if [ -z "$valor" ]; then
    if [ -n "$chave_legada" ]; then
      echo "Variavel obrigatoria ausente para renderizar realms: $chave ou $chave_legada" >&2
    else
      echo "Variavel obrigatoria ausente para renderizar realms: $chave" >&2
    fi
    exit 1
  fi

  valor_escapado="$(escape_sed_replacement "$valor")"
  arquivo_tmp="$(mktemp "${TARGET_DIR%/}/render.XXXXXX")"
  sed "s|\${$chave}|$valor_escapado|g" "$arquivo" >"$arquivo_tmp"
  mv "$arquivo_tmp" "$arquivo"
}

replace_placeholder_ou_padrao() {
  arquivo="$1"
  chave="$2"
  valor_padrao="$3"
  chave_legada="${4:-}"
  valor="$(printenv "$chave" || true)"

  if [ -z "$valor" ] && [ -n "$chave_legada" ]; then
    valor="$(printenv "$chave_legada" || true)"
  fi

  if [ -z "$valor" ]; then
    valor="$valor_padrao"
  fi

  valor_escapado="$(escape_sed_replacement "$valor")"
  arquivo_tmp="$(mktemp "${TARGET_DIR%/}/render.XXXXXX")"
  sed "s|\${$chave}|$valor_escapado|g" "$arquivo" >"$arquivo_tmp"
  mv "$arquivo_tmp" "$arquivo"
}

mkdir -p "$TARGET_DIR"
find "$TARGET_DIR" -maxdepth 1 -type f -name '*-realm.json' -delete

REALM_FILES_PATTERN="${KEYCLOAK_IMPORT_REALM_FILE:-*-realm.json}"
REALM_TARGET_FILE="${KEYCLOAK_IMPORT_REALM_TARGET_FILE:-}"

for origem in "$SOURCE_DIR"/$REALM_FILES_PATTERN; do
  [ -e "$origem" ] || {
    echo "Nenhum arquivo de realm encontrado em $SOURCE_DIR com o padrao $REALM_FILES_PATTERN" >&2
    exit 1
  }
  if [ -n "$REALM_TARGET_FILE" ]; then
    destino="$TARGET_DIR/$REALM_TARGET_FILE"
  else
    destino="$TARGET_DIR/$(basename "$origem")"
  fi
  cp "$origem" "$destino"

  replace_placeholder "$destino" "KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_ID" "KEYCLOAK_IDP_GOOGLE_CLIENT_ID"
  replace_placeholder "$destino" "KEYCLOAK_IDP_THIMISU_GOOGLE_CLIENT_SECRET" "KEYCLOAK_IDP_GOOGLE_CLIENT_SECRET"
  replace_placeholder "$destino" "KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_ID" "KEYCLOAK_IDP_APPLE_CLIENT_ID"
  replace_placeholder "$destino" "KEYCLOAK_IDP_THIMISU_APPLE_CLIENT_SECRET_JWT" "KEYCLOAK_IDP_APPLE_CLIENT_SECRET_JWT"
  replace_placeholder "$destino" "KEYCLOAK_IDP_THIMISU_FACEBOOK_APP_ID" "KEYCLOAK_IDP_FACEBOOK_CLIENT_ID"
  replace_placeholder "$destino" "KEYCLOAK_IDP_THIMISU_FACEBOOK_APP_SECRET" "KEYCLOAK_IDP_FACEBOOK_CLIENT_SECRET"
  replace_placeholder "$destino" "KEYCLOAK_IDP_THIMISU_LINKEDIN_CLIENT_ID" "KEYCLOAK_IDP_LINKEDIN_CLIENT_ID"
  replace_placeholder "$destino" "KEYCLOAK_IDP_THIMISU_LINKEDIN_CLIENT_SECRET" "KEYCLOAK_IDP_LINKEDIN_CLIENT_SECRET"
  replace_placeholder "$destino" "KEYCLOAK_IDP_THIMISU_INSTAGRAM_APP_ID" "KEYCLOAK_IDP_INSTAGRAM_CLIENT_ID"
  replace_placeholder "$destino" "KEYCLOAK_IDP_THIMISU_INSTAGRAM_APP_SECRET" "KEYCLOAK_IDP_INSTAGRAM_CLIENT_SECRET"
  replace_placeholder_ou_padrao "$destino" "KEYCLOAK_IDP_THIMISU_X_CLIENT_ID" "trocar-x-client-id" "KEYCLOAK_IDP_X_CLIENT_ID"
  replace_placeholder_ou_padrao "$destino" "KEYCLOAK_IDP_THIMISU_X_CLIENT_SECRET" "trocar-x-client-secret" "KEYCLOAK_IDP_X_CLIENT_SECRET"
  replace_placeholder "$destino" "KEYCLOAK_CLIENT_THIMISU_BACKEND_SECRET"
  replace_placeholder "$destino" "KEYCLOAK_CLIENT_EICKRONO_AUTENTICACAO_INTERNO_SECRET"
  replace_placeholder "$destino" "KEYCLOAK_CLIENT_EICKRONO_KEYCLOAK_SECRET"
done

exec "$KEYCLOAK_BIN" "$@" --import-realm
