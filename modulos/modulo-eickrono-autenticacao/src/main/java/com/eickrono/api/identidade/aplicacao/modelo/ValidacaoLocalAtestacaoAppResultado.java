package com.eickrono.api.identidade.aplicacao.modelo;

import com.eickrono.api.identidade.dominio.modelo.OperacaoAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.PlataformaAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.ProvedorAtestacaoApp;
import java.time.OffsetDateTime;

public record ValidacaoLocalAtestacaoAppResultado(
        String identificadorDesafio,
        OperacaoAtestacaoApp operacao,
        PlataformaAtestacaoApp plataforma,
        ProvedorAtestacaoApp provedorEsperado,
        OffsetDateTime criadoEm,
        OffsetDateTime expiraEm,
        OffsetDateTime geradoEm,
        String chaveId
) {
}
