package br.com.votoconsciente.camara.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * DTO para detalhe de um deputado da API da Câmara.
 * Endpoint: GET /deputados/{id}
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeputadoDetalheDTO {

    private Long id;
    private String uri;
    private String nomeCivil;
    private String cpf;
    private String sexo;
    private String urlWebsite;
    private List<String> redeSocial;
    private String dataNascimento;
    private String dataFalecimento;
    private String ufNascimento;
    private String municipioNascimento;
    private String escolaridade;
    private UltimoStatusDTO ultimoStatus;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UltimoStatusDTO {
        private Long id;
        private String nome;
        private String nomeEleitoral;
        private String siglaPartido;
        private String uriPartido;
        private String siglaUf;
        private Integer idLegislatura;
        private String urlFoto;
        private String email;
        private String data;
        private String situacao;
        private String condicaoEleitoral;
        private String descricaoStatus;
        private GabineteDTO gabinete;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GabineteDTO {
        private String nome;
        private String predio;
        private String sala;
        private String andar;
        private String telefone;
        private String email;
    }
}
