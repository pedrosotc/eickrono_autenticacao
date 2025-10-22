# Guia de Operação em Produção

Este guia descreve processos para operar o ecossistema **Eickrono Autenticação** em produção na AWS, protegido por Cloudflare.

## Arquitetura em produção

- **EKS/ECS:** hospeda os contêineres das APIs e do Keycloak (cluster gerenciado).  
- **ALB/NLB:** balanceamento de carga com suporte a mTLS e roteamento por host/path.  
- **Cloudflare:** WAF, Rate Limiting, mTLS Origin Pull e caching seletivo de recursos estáticos.  
- **RDS PostgreSQL:** instâncias multi-AZ com backups automáticos, replicação e monitoramento de performance.  
- **Secrets Manager / Parameter Store:** armazenamento de segredos, certificados e configurações sensíveis.  
- **ACM / KMS / HSM:** gestão de certificados TLS e chaves de assinatura (JWKs).

## Pipeline CI/CD

1. **Build e testes:** `mvn verify` + cobertura de testes + validações de estilo.  
2. **SCA/SAST:** varredura de dependências e código (ex.: OWASP Dependency Check, SonarQube).  
3. **Scan de imagens:** análise de vulnerabilidades nos contêineres gerados.  
4. **Deploy automatizado:** aplicação de manifests/Helm charts em EKS/ECS e execução de Flyway.  
5. **Smoke tests:** validação de saúde, fluxo Authorization Code + PKCE e endpoints críticos.  
6. **Publicação OpenAPI:** upload automático dos artefatos JSON/YAML para armazenamento versionado (S3, artefatos de pipeline).

## Operações rotineiras

- **Rotação de segredos:** programar rotação de certificados TLS, JWKs e segredos de banco a cada 90 dias ou menos.  
- **Exportação de realms:** agendar exportação dos realms Keycloak e armazenar em S3 com versionamento.  
- **Monitoramento:** utilizar Prometheus/Grafana e OpenTelemetry Collector para métricas e traces; configurar alertas críticos.  
- **Auditoria:** revisar tabelas `auditoria_eventos` e `auditoria_acessos` das APIs periodicamente; arquivar registros em storage seguro.  
- **Gestão de incidentes:** seguir runbooks, acionar comunicação e registrar lições aprendidas no pós-incidente.

## Procedimentos de emergência

- **Disaster Recovery:** gatilho para restauração em região secundária (backup de RDS + reimplantação de Keycloak).  
- **Comprometimento de chave:** revogar certificado no ACM/KMS, gerar novo par e atualizar a configuração nos serviços e na Cloudflare.  
- **Falha de autenticação:** verificar integridade do JWK endpoint (`/.well-known/jwks.json`), sincronização de relógios (NTP) e logs de auditoria.  
- **Rejeição FAPI:** validar configuração de PAR/JAR/JARM e certificados mTLS dos clientes confidenciais.

## Segurança operacional

- **Princípio do menor privilégio:** usuários e papéis IAM restritos à necessidade.  
- **Políticas de acesso Cloudflare:** whitelist de IPs e autenticação de operadores.  
- **Logging mascarado:** garantir que dados sensíveis permaneçam protegidos; mascaramento implementado nas APIs e no Keycloak.  
- **Reviews periódicos:** executar o `checklist-seguranca-fapi.md` durante janelas de manutenção e antes de releases.
