package br.com.votoconsciente.camara.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * DTO para dados de uma votação na Câmara dos Deputados.
 * Endpoint: GET /votacoes e GET /votacoes/{id}
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VotacaoDTO {

    private String id;
    private String uri;
    private String data;
    private String dataHoraRegistro;
    private String siglaOrgao;
    private String uriOrgao;
    private String uriEvento;
    private String descricao;
    private Integer aprovacao;
}
