package com.eickrono.api.identidade.apresentacao.dto.atestacao;

import com.eickrono.api.identidade.aplicacao.modelo.DesafioAtestacaoGerado;
import com.eickrono.api.identidade.dominio.modelo.OperacaoAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.PlataformaAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.ProvedorAtestacaoApp;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DesafioAtestacaoApiResposta(
        String identificadorDesafio,
        String desafioBase64,
        OffsetDateTime expiraEm,
        OperacaoAtestacaoApp operacao,
        PlataformaAtestacaoApp plataforma,
        ProvedorAtestacaoApp provedorEsperado,
        String numeroProjetoNuvemAndroid
) {

    public static DesafioAtestacaoApiResposta de(final DesafioAtestacaoGerado desafio) {
        return new DesafioAtestacaoApiResposta(
                desafio.identificadorDesafio(),
                desafio.desafioBase64(),
                desafio.expiraEm(),
                desafio.operacao(),
                desafio.plataforma(),
                desafio.provedorEsperado(),
                desafio.numeroProjetoNuvemAndroid()
        );
    }
}
