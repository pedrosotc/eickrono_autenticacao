#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_UNDER_TEST="${ROOT_DIR}/ecs/rollout_stg_service.sh"

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

test_missing_required_args() {
  local output
  set +e
  output="$("$SCRIPT_UNDER_TEST" 2>&1)"
  local status=$?
  set -e
  assert_equals "2" "$status"
  assert_contains "$output" "uso: rollout_stg_service.sh"
}

test_invalid_service() {
  local output
  set +e
  output="$("$SCRIPT_UNDER_TEST" --service invalido --image exemplo:tag 2>&1)"
  local status=$?
  set -e
  assert_equals "2" "$status"
  assert_contains "$output" "service invalido: invalido"
}

test_dry_run_renders_identidade_image_and_commands() {
  local tmpdir output rendered image rendered_db_url rendered_db_user rendered_db_secret disponibilidade_url token_url perfil_url
  tmpdir="$(mktemp -d)"
  rendered="${tmpdir}/identidade.rendered.json"

  output="$("$SCRIPT_UNDER_TEST" \
    --service identidade \
    --image 531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-identidade-servidor:stg-test-001 \
    --render-output "$rendered" \
    --dry-run)"

  image="$(jq -r '.containerDefinitions[0].image' "$rendered")"
  rendered_db_url="$(jq -r '.containerDefinitions[0].environment[] | select(.name == "SPRING_DATASOURCE_URL") | .value' "$rendered")"
  rendered_db_user="$(jq -r '.containerDefinitions[0].environment[] | select(.name == "SPRING_DATASOURCE_USERNAME") | .value' "$rendered")"
  rendered_db_secret="$(jq -r '.containerDefinitions[0].secrets[] | select(.name == "SPRING_DATASOURCE_PASSWORD") | .valueFrom' "$rendered")"
  disponibilidade_url="$(jq -r '.containerDefinitions[0].environment[] | select(.name == "INTEGRACAO_PERFIL_DISPONIBILIDADE_URL_BASE") | .value' "$rendered")"
  token_url="$(jq -r '.containerDefinitions[0].environment[] | select(.name == "INTEGRACAO_PERFIL_JWT_INTERNO_URL_BASE") | .value' "$rendered")"
  perfil_url="$(jq -r '.containerDefinitions[0].environment[] | select(.name == "INTEGRACAO_PERFIL_URL_BASE") | .value' "$rendered")"
  assert_equals "531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-identidade-servidor:stg-test-001" "$image"
  assert_equals "jdbc:postgresql://eickrono-stg-postgres.cdu8yi4qkl16.sa-east-1.rds.amazonaws.com:5432/eickrono_identidade_stg" "$rendered_db_url"
  assert_equals "eickrono_admin" "$rendered_db_user"
  assert_equals "arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-7df15f56-c831-40b7-be42-ebd935108b06-22Dwvf:password::" "$rendered_db_secret"
  assert_equals "http://auth-stg-interno.stg.eickrono.internal:8080" "$disponibilidade_url"
  assert_equals "http://auth-stg-interno.stg.eickrono.internal:8080" "$token_url"
  assert_equals "https://thimisu-backend-stg-interno.stg.eickrono.internal:8443" "$perfil_url"
  assert_contains "$output" "SERVICE_KEY=identidade"
  assert_contains "$output" "TASK_FAMILY=identidade-stg"
  assert_contains "$output" "CONTAINER_NAME=identidade"
  assert_contains "$output" "ECS_SERVICE=identidade-stg"
  assert_contains "$output" "RENDERED_IMAGE=531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-identidade-servidor:stg-test-001"
  assert_contains "$output" "IDENTIDADE_DB_URL=jdbc:postgresql://eickrono-stg-postgres.cdu8yi4qkl16.sa-east-1.rds.amazonaws.com:5432/eickrono_identidade_stg"
  assert_contains "$output" "IDENTIDADE_DB_PASSWORD_SECRET_ARN=arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-7df15f56-c831-40b7-be42-ebd935108b06-22Dwvf:password::"
  assert_contains "$output" "aws ecs register-task-definition"
  assert_contains "$output" "aws ecs update-service"
  assert_contains "$output" "aws ecs wait services-stable"

  rm -rf "$tmpdir"
}

test_dry_run_no_wait_skips_wait_command() {
  local output
  output="$("$SCRIPT_UNDER_TEST" \
    --service auth \
    --image 531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-autenticacao-servidor:stg-test-002 \
    --no-wait \
    --dry-run)"

  assert_contains "$output" "WAIT_FOR_STABLE=false"
  if [[ "$output" == *"aws ecs wait services-stable"* ]]; then
    fail "nao deveria conter wait quando --no-wait for informado"
  fi
}

test_dry_run_allows_db_override_by_service() {
  local tmpdir output rendered rendered_db_url rendered_db_user rendered_db_secret
  tmpdir="$(mktemp -d)"
  rendered="${tmpdir}/thimisu.rendered.json"

  output="$(
    THIMISU_DB_HOST=thimisu-db-interno.stg.eickrono.internal \
    THIMISU_DB_PORT=5433 \
    THIMISU_DB_NAME=eickrono_thimisu_stg_v2 \
    THIMISU_DB_USERNAME=thimisu_app \
    THIMISU_DB_PASSWORD_SECRET_ARN=arn:aws:secretsmanager:sa-east-1:531708494702:secret:/eickrono/stg/thimisu/db-password-XYZ \
    "$SCRIPT_UNDER_TEST" \
      --service thimisu-backend \
      --image 531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-thimisu-backend:stg-test-003 \
      --render-output "$rendered" \
      --dry-run
  )"

  rendered_db_url="$(jq -r '.containerDefinitions[0].environment[] | select(.name == "SPRING_DATASOURCE_URL") | .value' "$rendered")"
  rendered_db_user="$(jq -r '.containerDefinitions[0].environment[] | select(.name == "SPRING_DATASOURCE_USERNAME") | .value' "$rendered")"
  rendered_db_secret="$(jq -r '.containerDefinitions[0].secrets[] | select(.name == "SPRING_DATASOURCE_PASSWORD") | .valueFrom' "$rendered")"
  assert_equals "jdbc:postgresql://thimisu-db-interno.stg.eickrono.internal:5433/eickrono_thimisu_stg_v2" "$rendered_db_url"
  assert_equals "thimisu_app" "$rendered_db_user"
  assert_equals "arn:aws:secretsmanager:sa-east-1:531708494702:secret:/eickrono/stg/thimisu/db-password-XYZ" "$rendered_db_secret"
  assert_contains "$output" "THIMISU_DB_URL=jdbc:postgresql://thimisu-db-interno.stg.eickrono.internal:5433/eickrono_thimisu_stg_v2"
  assert_contains "$output" "THIMISU_DB_PASSWORD_SECRET_ARN=arn:aws:secretsmanager:sa-east-1:531708494702:secret:/eickrono/stg/thimisu/db-password-XYZ"

  rm -rf "$tmpdir"
}

test_dry_run_accepts_db_overrides_file() {
  local tmpdir output rendered rendered_db_url rendered_db_user rendered_db_secret overrides_file
  tmpdir="$(mktemp -d)"
  rendered="${tmpdir}/auth.rendered.json"
  overrides_file="${tmpdir}/db.env"

  cat >"$overrides_file" <<'EOF'
export AUTH_KC_DB_HOST=auth-db-interno.stg.eickrono.internal
export AUTH_KC_DB_NAME=keycloak_stg_v2
export AUTH_KC_DB_USERNAME=keycloak_app
export AUTH_KC_DB_PASSWORD_SECRET_ARN=arn:aws:secretsmanager:sa-east-1:531708494702:secret:/eickrono/stg/auth/db-password-XYZ
EOF

  output="$("$SCRIPT_UNDER_TEST" \
    --service auth \
    --image 531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-autenticacao-servidor:stg-test-004 \
    --db-overrides-file "$overrides_file" \
    --render-output "$rendered" \
    --dry-run)"

  rendered_db_url="$(jq -r '.containerDefinitions[0].environment[] | select(.name == "KC_DB_URL_HOST") | .value' "$rendered")"
  rendered_db_user="$(jq -r '.containerDefinitions[0].environment[] | select(.name == "KC_DB_USERNAME") | .value' "$rendered")"
  rendered_db_secret="$(jq -r '.containerDefinitions[0].secrets[] | select(.name == "KC_DB_PASSWORD") | .valueFrom' "$rendered")"
  assert_equals "auth-db-interno.stg.eickrono.internal" "$rendered_db_url"
  assert_equals "keycloak_app" "$rendered_db_user"
  assert_equals "arn:aws:secretsmanager:sa-east-1:531708494702:secret:/eickrono/stg/auth/db-password-XYZ" "$rendered_db_secret"
  assert_contains "$output" "DB_OVERRIDES_FILE=${overrides_file}"
  assert_contains "$output" "AUTH_KC_DB_HOST=auth-db-interno.stg.eickrono.internal"
  assert_contains "$output" "AUTH_KC_DB_PASSWORD_SECRET_ARN=arn:aws:secretsmanager:sa-east-1:531708494702:secret:/eickrono/stg/auth/db-password-XYZ"

  rm -rf "$tmpdir"
}

main() {
  test_missing_required_args
  test_invalid_service
  test_dry_run_renders_identidade_image_and_commands
  test_dry_run_no_wait_skips_wait_command
  test_dry_run_allows_db_override_by_service
  test_dry_run_accepts_db_overrides_file
  echo "ok"
}

main "$@"
