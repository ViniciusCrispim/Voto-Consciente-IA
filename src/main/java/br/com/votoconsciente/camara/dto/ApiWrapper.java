package br.com.votoconsciente.camara.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Wrapper genérico para respostas da API da Câmara dos Deputados.
 * A API sempre retorna { "dados": [...], "links": [...], "meta": {...} }
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiWrapper<T> {

    private List<T> dados;
    private List<LinkDTO> links;
    private MetaDTO meta;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LinkDTO {
        private String rel;
        private String href;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetaDTO {
        private Integer totalItens;
        private Integer totalPaginas;
    }
}
