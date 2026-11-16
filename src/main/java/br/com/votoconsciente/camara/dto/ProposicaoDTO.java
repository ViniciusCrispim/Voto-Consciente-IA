package br.com.votoconsciente.camara.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * DTO para proposições (projetos de lei) da API da Câmara.
 * Endpoint: GET /proposicoes
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProposicaoDTO {

    private Long id;
    private String uri;
    private String siglaTipo;
    private String codTipo;
    private Integer numero;
    private Integer ano;
    private String ementa;
}
