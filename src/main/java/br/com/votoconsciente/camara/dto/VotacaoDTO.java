package br.com.votoconsciente.camara.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * DTO para votações nominais de um deputado.
 * Endpoint: GET /deputados/{id}/votacoes
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VotacaoDTO {

    private String id;
    private String uri;
    private String data;
    private String dataHoraRegistro;
    private Integer siglaOrgao;
    private String uriOrgao;
    private String uriEvento;
    private String proposicaoAutor;
    private String descricao;
    private String tipoVoto;
}
