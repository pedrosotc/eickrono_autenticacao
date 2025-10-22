# Servidor de Autorização Eickrono

Esta pasta contém customizações do Keycloak/RH-SSO utilizadas pela Eickrono:

- `temas-login-ptbr`: temas de login totalmente em português.  
- `mapeamentos-atributos`: mapeamentos adicionais de claims e atributos.  
- `configuracoes-fapi`: políticas de PAR/JAR/JARM, mTLS e client policies.  
- `realms`: exports versionados dos realms `desenvolvimento`, `homologacao` e `producao`.  
- `scripts-spi`: código Java/JS para integrações via SPI.

O módulo gera um JAR com utilidades compartilhadas e serve como ponto de empacotamento para as customizações do servidor de autorização.
