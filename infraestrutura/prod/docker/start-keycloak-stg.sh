#!/bin/sh

set -eu

exec /opt/keycloak/import-source/render-realms.sh \
  start \
  --http-enabled=true \
  --features="${KEYCLOAK_FEATURES:-instagram-broker:v1,token-exchange:v1,admin-fine-grained-authz:v1}" \
  --spi-theme-static-max-age=-1
