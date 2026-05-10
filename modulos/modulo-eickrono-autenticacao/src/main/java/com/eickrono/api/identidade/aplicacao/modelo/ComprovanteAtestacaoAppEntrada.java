package com.eickrono.api.identidade.aplicacao.modelo;

import com.eickrono.api.identidade.dominio.modelo.PlataformaAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.ProvedorAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.TipoComprovanteAtestacaoApp;
import java.time.OffsetDateTime;

public record ComprovanteAtestacaoAppEntrada(
        PlataformaAtestacaoApp plataforma,
        ProvedorAtestacaoApp provedor,
        TipoComprovanteAtestacaoApp tipoComprovante,
        String identificadorDesafio,
        String desafioBase64,
        String conteudoComprovante,
        OffsetDateTime geradoEm,
        String chaveId
) {
}
