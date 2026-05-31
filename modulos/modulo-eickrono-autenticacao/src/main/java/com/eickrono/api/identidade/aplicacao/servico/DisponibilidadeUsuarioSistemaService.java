package com.eickrono.api.identidade.aplicacao.servico;

import java.util.Optional;
import java.util.Locale;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DisponibilidadeUsuarioSistemaService {

    private static final String SISTEMA_PUBLICO_ATUAL = "eickrono-thimisu-app";
    private static final String SISTEMA_PUBLICO_LEGADO = "app-flutter-publico";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DisponibilidadeUsuarioSistemaService(final NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate e obrigatorio");
    }

    public boolean identificadorPublicoSistemaDisponivel(final String identificadorPublicoSistema,
                                                         final String sistemaSolicitante) {
        String identificadorPublicoSistemaNormalizado = Objects.requireNonNull(
                        identificadorPublicoSistema,
                        "identificadorPublicoSistema e obrigatorio")
                .trim()
                .toLowerCase(Locale.ROOT);
        String sistemaNormalizado = Objects.requireNonNull(sistemaSolicitante, "sistemaSolicitante e obrigatorio")
                .trim();
        if (identificadorPublicoSistemaNormalizado.isBlank() || sistemaNormalizado.isBlank()) {
            return false;
        }
        String codigoCliente = resolverCodigoClienteEcossistema(sistemaNormalizado);
        if (identificadorPublicoSistemaJaVinculadoAoCliente(identificadorPublicoSistemaNormalizado, codigoCliente)) {
            return false;
        }
        return !identificadorPublicoSistemaJaReservadoEmCadastro(
                identificadorPublicoSistemaNormalizado,
                codigoCliente
        );
    }

    public boolean usuarioDisponivel(final String usuario, final String sistemaSolicitante) {
        return identificadorPublicoSistemaDisponivel(usuario, sistemaSolicitante);
    }

    private boolean identificadorPublicoSistemaJaVinculadoAoCliente(
            final String identificadorPublicoSistemaNormalizado,
            final String codigoCliente) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("identificadorPublicoSistema", identificadorPublicoSistemaNormalizado)
                .addValue("codigoCliente", codigoCliente);
        return Optional.ofNullable(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM autenticacao.usuarios_clientes_ecossistema vinculo
                    JOIN catalogo.clientes_ecossistema cliente
                      ON cliente.id = vinculo.cliente_ecossistema_id
                    WHERE vinculo.identificador_publico_cliente IS NOT NULL
                      AND LOWER(vinculo.identificador_publico_cliente) = :identificadorPublicoSistema
                      AND LOWER(cliente.codigo) = LOWER(:codigoCliente)
                      AND COALESCE(vinculo.status_vinculo, '') <> 'REVOGADO'
                )
                """, params, Boolean.class)).orElse(false);
    }

    private boolean identificadorPublicoSistemaJaReservadoEmCadastro(
            final String identificadorPublicoSistemaNormalizado,
            final String codigoCliente) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("identificadorPublicoSistema", identificadorPublicoSistemaNormalizado)
                .addValue("codigoCliente", codigoCliente);
        return Optional.ofNullable(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM autenticacao.cadastros_conta cadastro
                    JOIN catalogo.clientes_ecossistema cliente
                      ON cliente.id = cadastro.cliente_ecossistema_id
                    WHERE cadastro.identificador_publico_cliente IS NOT NULL
                      AND LOWER(cadastro.identificador_publico_cliente) = :identificadorPublicoSistema
                      AND LOWER(cliente.codigo) = LOWER(:codigoCliente)
                )
                """, params, Boolean.class)).orElse(false);
    }

    private String resolverCodigoClienteEcossistema(final String sistemaSolicitanteNormalizado) {
        if (SISTEMA_PUBLICO_LEGADO.equalsIgnoreCase(sistemaSolicitanteNormalizado)) {
            return SISTEMA_PUBLICO_ATUAL;
        }
        return sistemaSolicitanteNormalizado;
    }
}
