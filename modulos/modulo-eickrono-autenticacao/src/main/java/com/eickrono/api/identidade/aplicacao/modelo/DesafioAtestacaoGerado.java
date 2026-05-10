package com.eickrono.api.identidade.aplicacao.modelo;

import com.eickrono.api.identidade.dominio.modelo.OperacaoAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.PlataformaAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.ProvedorAtestacaoApp;
import java.time.OffsetDateTime;

public record DesafioAtestacaoGerado(
        String identificadorDesafio,
        String desafioBase64,
        OffsetDateTime expiraEm,
        OperacaoAtestacaoApp operacao,
        PlataformaAtestacaoApp plataforma,
        ProvedorAtestacaoApp provedorEsperado,
        String numeroProjetoNuvemAndroid
) {
}
