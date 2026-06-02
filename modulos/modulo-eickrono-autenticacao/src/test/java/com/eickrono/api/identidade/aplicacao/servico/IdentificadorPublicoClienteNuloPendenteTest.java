package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentificadorPublicoClienteNuloPendenteTest {

    @Test
    @DisplayName("backfill historico com vinculo multiapp nulo deve ter migracao corretiva posterior")
    void backfillHistoricoComVinculoMultiappNuloDeveTerMigracaoCorretivaPosterior()
            throws IOException {
        String migration = lerArquivo("src/main/resources/db/migration/"
                + "V19__backfill_usuarios_e_vinculos_multiapp.sql");
        String corretiva = lerArquivo("src/main/resources/db/migration/"
                + "V43__corrigir_identificador_publico_cliente_vinculos_multiapp.sql");

        assertThat(migration)
                .containsPattern("(?s)identificador_publico_cliente.*NULL");
        assertThat(corretiva)
                .contains("UPDATE autenticacao.usuarios_clientes_ecossistema vinculo")
                .contains("cadastro_legacy.usuario")
                .contains("vinculo.identificador_publico_cliente IS NULL")
                .contains("BTRIM(vinculo.identificador_publico_cliente) = ''");
    }

    @Test
    @DisplayName("definir avatar social nao deve passar pelo vinculo multiapp sem identificador publico")
    void definirAvatarSocialNaoDeveChamarVinculoProjetoSemIdentificadorPublicoCliente()
            throws IOException {
        String fonte = lerAvatarSocialProjetoJdbc();

        assertThat(fonte)
                .doesNotContainPattern("(?s)public void definirAvatarSocial.*assegurarVinculoProjeto\\("
                        + "\\s*usuarioId,\\s*Objects\\.requireNonNull\\(subRemoto");
    }

    @Test
    @DisplayName("definir avatar por URL nao deve passar pelo vinculo multiapp sem identificador publico")
    void definirAvatarUrlNaoDeveChamarVinculoProjetoSemIdentificadorPublicoCliente()
            throws IOException {
        String fonte = lerAvatarSocialProjetoJdbc();

        assertThat(fonte)
                .doesNotContainPattern("(?s)public void definirAvatarUrl.*assegurarVinculoProjeto\\("
                        + "\\s*usuarioId,\\s*Objects\\.requireNonNull\\(subRemoto");
    }

    @Test
    @DisplayName("limpar avatar preferido nao deve passar pelo vinculo multiapp sem identificador publico")
    void limparAvatarPreferidoNaoDeveChamarVinculoProjetoSemIdentificadorPublicoCliente()
            throws IOException {
        String fonte = lerAvatarSocialProjetoJdbc();

        assertThat(fonte)
                .doesNotContainPattern("(?s)public void limparAvatarPreferido.*assegurarVinculoProjeto\\("
                        + "\\s*usuarioId,\\s*Objects\\.requireNonNull\\(subRemoto");
    }

    private static String lerAvatarSocialProjetoJdbc() throws IOException {
        return lerArquivo("src/main/java/com/eickrono/api/identidade/aplicacao/servico/"
                + "AvatarSocialProjetoJdbc.java");
    }

    private static String lerArquivo(final String caminhoRelativoModulo) throws IOException {
        return Files.readString(Path.of(caminhoRelativoModulo));
    }
}
