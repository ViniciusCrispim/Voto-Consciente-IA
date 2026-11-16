package br.com.votoconsciente.camara.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * DTO para listagem de deputados da API da Câmara.
 * Endpoint: GET /deputados
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeputadoResumoDTO {

    private Long id;
    private String uri;
    private String nome;
    private String siglaPartido;
    private String uriPartido;
    private String siglaUf;
    private Integer idLegislatura;
    private String urlFoto;
    private String email;
}
