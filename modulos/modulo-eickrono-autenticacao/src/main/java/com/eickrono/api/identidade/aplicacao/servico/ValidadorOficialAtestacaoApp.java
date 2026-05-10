package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.ComprovanteAtestacaoAppEntrada;
import com.eickrono.api.identidade.aplicacao.modelo.ValidacaoLocalAtestacaoAppResultado;
import com.eickrono.api.identidade.aplicacao.modelo.ValidacaoOficialAtestacaoAppResultado;
import com.eickrono.api.identidade.dominio.modelo.PlataformaAtestacaoApp;

public interface ValidadorOficialAtestacaoApp {

    boolean suporta(PlataformaAtestacaoApp plataforma);

    ValidacaoOficialAtestacaoAppResultado validar(
            ComprovanteAtestacaoAppEntrada comprovante,
            ValidacaoLocalAtestacaoAppResultado validacaoLocal
    );
}
