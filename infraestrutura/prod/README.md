# Estrutura de Produção

Esta pasta deve conter os artefatos de infraestrutura usados em produção:

- `terraform/`: módulos e configurações IaC para AWS.  
- `cloudflare/`: scripts e templates de configuração Cloudflare (WAF, mTLS Origin Pull, Rate Limits).  
- `pipeline/`: definições de pipelines CI/CD (build, segurança, deploy e smoke tests).

> Os arquivos sensíveis (segredos, estados de Terraform etc.) **não** devem ser versionados neste repositório.
