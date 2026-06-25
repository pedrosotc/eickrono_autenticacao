SELECT 'CREATE DATABASE eickrono_dev'
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_database
    WHERE datname = 'eickrono_dev'
)\gexec

SELECT 'CREATE DATABASE eickrono_identidade'
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_database
    WHERE datname = 'eickrono_identidade'
)\gexec

SELECT 'CREATE DATABASE eickrono_autenticacao'
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_database
    WHERE datname = 'eickrono_autenticacao'
)\gexec

SELECT 'CREATE DATABASE eickrono_contas'
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_database
    WHERE datname = 'eickrono_contas'
)\gexec
