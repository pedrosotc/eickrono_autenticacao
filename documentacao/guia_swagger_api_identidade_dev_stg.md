# Guia de Acesso ao Swagger da API Autenticacao em Dev e STG

Este guia resume como abrir o Swagger/OpenAPI do runtime
`eickrono-autenticacao` dentro do repositório
`eickrono-autenticacao-servidor`.

Nota:

- `eickrono-autenticacao` é o nome operacional atual do serviço e do
  container;
- o módulo físico que gera essa aplicação dentro deste repositório é
  `modulos/modulo-eickrono-autenticacao`;
- isso não redefine a arquitetura final aprovada, em que a borda pública do
  app deve convergir para `autenticacao`.

## 1. Dev local

### 1.1 Swagger UI

- `http://127.0.0.1:8081/swagger-ui/index.html`

### 1.2 OpenAPI JSON

- `http://127.0.0.1:8081/v3/api-docs`

### 1.3 Credenciais

- em `dev`, o Swagger fica liberado para uso local;
- nao exige `Basic Auth`.

## 2. STG

### 2.1 Swagger UI

- `http://localhost:18081/swagger-ui/index.html`

### 2.2 OpenAPI JSON

- `http://localhost:18081/v3/api-docs`

### 2.3 Credenciais

- usuario: `swagger`
- senha: `Sw9@Qm2!Tx7#Lp4$Vz8Kr`

## 3. Resumo rapido

| Ambiente | Swagger UI | OpenAPI JSON | Credencial |
| --- | --- | --- | --- |
| `dev` via compose | `http://127.0.0.1:8081/swagger-ui/index.html` | `http://127.0.0.1:8081/v3/api-docs` | nao exige |
| `stg` via compose | `http://localhost:18081/swagger-ui/index.html` | `http://localhost:18081/v3/api-docs` | `swagger / Sw9@Qm2!Tx7#Lp4$Vz8Kr` |
