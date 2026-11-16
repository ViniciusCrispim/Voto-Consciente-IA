package br.com.votoconsciente.deputado.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO de resposta com os dados de um deputado.
 * Usado tanto na listagem quanto no detalhe.
 */
@Data
@Builder
public class DeputadoResponseDTO {

    private Long id;
    private Long camaraId;
    private String nome;
    private String nomeCivil;
    private String nomeEleitoral;
    private String siglaPartido;
    private String siglaUf;
    private String email;
    private String urlFoto;
    private String urlWebsite;
    private String sexo;
    private String dataNascimento;
    private String escolaridade;
    private String municipioNascimento;
    private String ufNascimento;
    private String situacao;
    private String condicaoEleitoral;
    private Integer idLegislatura;
    private GabineteDTO gabinete;
    private LocalDateTime ultimaAtualizacao;

    /** Indica se os dados vêm do cache local ou foram buscados ao vivo */
    private String fonte;

    @Data
    @Builder
    public static class GabineteDTO {
        private String nome;
        private String predio;
        private String sala;
        private String andar;
        private String telefone;
    }
}
