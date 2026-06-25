#!/usr/bin/env bash
set -euo pipefail

DIAS_VALIDOS=${1:-825}
SENHA_KEYSTORE=${MTLS_KEYSTORE_SENHA:-senhaBackchannelHml}
SENHA_TRUSTSTORE=${MTLS_TRUSTSTORE_SENHA:-senhaBackchannelHml}

DIR_ATUAL=$(cd "$(dirname "$0")" && pwd)
cd "${DIR_ATUAL}"

ARQUIVOS_GERADOS=(
  backchannel-ca.key
  backchannel-ca.crt
  backchannel-ca.srl
  backchannel-truststore.p12
  eickrono-autenticacao.key
  eickrono-autenticacao.csr
  eickrono-autenticacao.crt
  eickrono-autenticacao.p12
  api-identidade-eickrono.key
  api-identidade-eickrono.csr
  api-identidade-eickrono.crt
  api-identidade-eickrono.p12
  thimisu-backend.key
  thimisu-backend.csr
  thimisu-backend.crt
  thimisu-backend.p12
  eickrono-keycloak.key
  eickrono-keycloak.csr
  eickrono-keycloak.crt
  eickrono-keycloak.p12
)

rm -f "${ARQUIVOS_GERADOS[@]}"

openssl req -x509 -newkey rsa:4096 \
  -keyout backchannel-ca.key \
  -out backchannel-ca.crt \
  -days "${DIAS_VALIDOS}" \
  -nodes \
  -sha256 \
  -subj "/CN=eickrono-backchannel-hml-ca/O=Eickrono/OU=HML"

gerar_certificado() {
  local nome="$1"
  local common_name="$2"
  local san="$3"
  local eku="$4"
  local arquivo_tmp
  arquivo_tmp=$(mktemp)

  cat > "${arquivo_tmp}" <<EOF
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no

[req_distinguished_name]
CN = ${common_name}
O = Eickrono
OU = HML

[v3_req]
subjectAltName = ${san}
extendedKeyUsage = ${eku}
keyUsage = digitalSignature, keyEncipherment
basicConstraints = CA:FALSE
EOF

  openssl req -new -newkey rsa:4096 \
    -keyout "${nome}.key" \
    -out "${nome}.csr" \
    -nodes \
    -sha256 \
    -config "${arquivo_tmp}"

  openssl x509 -req \
    -in "${nome}.csr" \
    -CA backchannel-ca.crt \
    -CAkey backchannel-ca.key \
    -CAcreateserial \
    -out "${nome}.crt" \
    -days "${DIAS_VALIDOS}" \
    -sha256 \
    -extfile "${arquivo_tmp}" \
    -extensions v3_req

  openssl pkcs12 -export \
    -inkey "${nome}.key" \
    -in "${nome}.crt" \
    -certfile backchannel-ca.crt \
    -out "${nome}.p12" \
    -password pass:"${SENHA_KEYSTORE}"

  rm -f "${arquivo_tmp}"
}

gerar_certificado \
  "eickrono-autenticacao" \
  "eickrono-autenticacao" \
  "DNS:eickrono-autenticacao,DNS:auth-hml-interno,DNS:auth-hml-interno.hml.eickrono.internal,DNS:host.docker.internal,DNS:localhost,IP:127.0.0.1" \
  "serverAuth,clientAuth"

gerar_certificado \
  "api-identidade-eickrono" \
  "api-identidade-eickrono" \
  "DNS:api-identidade-eickrono,DNS:id-hml-interno,DNS:id-hml-interno.hml.eickrono.internal,DNS:host.docker.internal,DNS:localhost,IP:127.0.0.1" \
  "serverAuth,clientAuth"

gerar_certificado \
  "thimisu-backend" \
  "thimisu-backend" \
  "DNS:thimisu-backend,DNS:thimisu-backend-hml-interno,DNS:thimisu-backend-hml-interno.hml.eickrono.internal,DNS:host.docker.internal,DNS:localhost,IP:127.0.0.1" \
  "serverAuth,clientAuth"

gerar_certificado \
  "eickrono-keycloak" \
  "eickrono-keycloak" \
  "DNS:eickrono-keycloak,DNS:auth-hml-interno,DNS:auth-hml-interno.hml.eickrono.internal,DNS:host.docker.internal,DNS:localhost,IP:127.0.0.1" \
  "clientAuth"

keytool -importcert -noprompt \
  -alias eickrono-backchannel-ca \
  -file backchannel-ca.crt \
  -keystore backchannel-truststore.p12 \
  -storetype PKCS12 \
  -storepass "${SENHA_TRUSTSTORE}"

echo "Certificados e truststore do backchannel gerados em ${DIR_ATUAL}"
echo "Se a stack local ja estiver em execucao, recrie os containers que usam mTLS para recarregar keystore/truststore."
echo "Exemplos:"
echo "  cd /Users/thiago/Desenvolvedor/flutter/eickrono-autenticacao-servidor/infraestrutura/hml && docker compose up -d --force-recreate eickrono-autenticacao identidade-servidor eickrono-keycloak"
echo "  cd /Users/thiago/Desenvolvedor/flutter/eickrono-thimisu-backend/infraestrutura/hml && docker compose up -d --force-recreate thimisu-backend"
