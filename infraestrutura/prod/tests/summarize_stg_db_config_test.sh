#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_UNDER_TEST="${ROOT_DIR}/ecs/summarize_stg_db_config.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "$haystack" != *"$needle"* ]]; then
    fail "nao encontrou trecho esperado: ${needle}"
  fi
}

assert_equals() {
  local expected="$1"
  local actual="$2"
  if [ "$expected" != "$actual" ]; then
    fail "esperado '${expected}', obtido '${actual}'"
  fi
}

test_default_summary_contains_all_services() {
  local output
  output="$("$SCRIPT_UNDER_TEST")"
  assert_contains "$output" "SERVICE"
  assert_contains "$output" "auth"
  assert_contains "$output" "identidade"
  assert_contains "$output" "thimisu-backend"
  assert_contains "$output" "eickrono_identidade_stg"
  assert_contains "$output" "eickrono_thimisu_stg"
}

test_missing_db_overrides_file_fails() {
  local output
  set +e
  output="$("$SCRIPT_UNDER_TEST" --db-overrides-file inexistente.env 2>&1)"
  local status=$?
  set -e
  assert_equals "2" "$status"
  assert_contains "$output" "arquivo de overrides de banco nao encontrado"
}

test_summary_accepts_db_overrides_file() {
  local tmpdir overrides_file output
  tmpdir="$(mktemp -d)"
  overrides_file="${tmpdir}/db.env"

  cat >"$overrides_file" <<'EOF'
export AUTH_KC_DB_HOST=auth-db-interno.stg.eickrono.internal
export AUTH_KC_DB_NAME=keycloak_stg_v2
export AUTH_KC_DB_USERNAME=keycloak_app
export AUTH_KC_DB_PASSWORD_SECRET_ARN=arn:aws:secretsmanager:sa-east-1:531708494702:secret:/eickrono/stg/auth/db-password-XYZ
export IDENTIDADE_DB_HOST=identidade-db-interno.stg.eickrono.internal
export IDENTIDADE_DB_PORT=5433
export IDENTIDADE_DB_NAME=eickrono_identidade_stg_v2
export IDENTIDADE_DB_USERNAME=identidade_app
export IDENTIDADE_DB_PASSWORD_SECRET_ARN=arn:aws:secretsmanager:sa-east-1:531708494702:secret:/eickrono/stg/identidade/db-password-XYZ
export THIMISU_DB_HOST=thimisu-db-interno.stg.eickrono.internal
export THIMISU_DB_PORT=5434
export THIMISU_DB_NAME=eickrono_thimisu_stg_v2
export THIMISU_DB_USERNAME=thimisu_app
export THIMISU_DB_PASSWORD_SECRET_ARN=arn:aws:secretsmanager:sa-east-1:531708494702:secret:/eickrono/stg/thimisu/db-password-XYZ
EOF

  output="$("$SCRIPT_UNDER_TEST" --db-overrides-file "$overrides_file")"
  assert_contains "$output" "auth-db-interno.stg.eickrono.internal"
  assert_contains "$output" "keycloak_stg_v2"
  assert_contains "$output" "identidade-db-interno.stg.eickrono.internal"
  assert_contains "$output" "5433"
  assert_contains "$output" "eickrono_identidade_stg_v2"
  assert_contains "$output" "thimisu-db-interno.stg.eickrono.internal"
  assert_contains "$output" "5434"
  assert_contains "$output" "eickrono_thimisu_stg_v2"
  assert_contains "$output" "arn:aws:secretsmanager:sa-east-1:531708494702:secret:/eickrono/stg/thimisu/db-password-XYZ"

  rm -rf "$tmpdir"
}

main() {
  test_default_summary_contains_all_services
  test_missing_db_overrides_file_fails
  test_summary_accepts_db_overrides_file
  echo "ok"
}

main "$@"
