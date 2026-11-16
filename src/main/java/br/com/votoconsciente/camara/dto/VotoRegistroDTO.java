package br.com.votoconsciente.camara.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Representa um registro individual de voto em GET /votacoes/{id}/votos
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VotoRegistroDTO {

    private String tipoVoto;
    private String dataRegistroVoto;
    private DeputadoResumoDTO deputado_;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeputadoSimplesDTO {
        private Long id;
        private String nome;
        private String siglaPartido;
        private String siglaUf;
        private String urlFoto;
    }
}
