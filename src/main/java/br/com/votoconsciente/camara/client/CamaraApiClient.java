package br.com.votoconsciente.camara.client;

import br.com.votoconsciente.camara.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP para a API de Dados Abertos da Câmara dos Deputados.
 * Documentação: https://dadosabertos.camara.leg.br/api/v2/api-docs
 */
@Slf4j
@Component
public class CamaraApiClient {

    private final WebClient webClient;

    public CamaraApiClient(@Qualifier("camaraWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Lista todos os deputados de uma legislatura.
     *
     * @param idLegislatura número da legislatura (ex: 57 para 2023-2027)
     * @param pagina        número da página (começa em 1)
     * @param itensPorPagina máximo de 100
     */
    public List<DeputadoResumoDTO> listarDeputados(Integer idLegislatura, int pagina, int itensPorPagina) {
        log.debug("Buscando deputados na Câmara: legislatura={}, pagina={}", idLegislatura, pagina);
        try {
            ApiWrapper<DeputadoResumoDTO> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/deputados")
                            .queryParam("idLegislatura", idLegislatura)
                            .queryParam("pagina", pagina)
                            .queryParam("itens", itensPorPagina)
                            .queryParam("ordem", "ASC")
                            .queryParam("ordenarPor", "nome")
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ApiWrapper<DeputadoResumoDTO>>() {})
                    .block();

            return response != null && response.getDados() != null ? response.getDados() : List.of();
        } catch (WebClientResponseException e) {
            log.error("Erro ao listar deputados na Câmara: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        }
    }

    /**
     * Busca o detalhe completo de um deputado pelo ID.
     */
    public DeputadoDetalheDTO buscarDeputadoPorId(Long id) {
        log.debug("Buscando detalhe do deputado {} na Câmara", id);
        try {
            Map<String, DeputadoDetalheDTO> response = webClient.get()
                    .uri("/deputados/{id}", id)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, DeputadoDetalheDTO>>() {})
                    .block();

            return response != null ? response.get("dados") : null;
        } catch (WebClientResponseException e) {
            log.error("Erro ao buscar deputado {}: status={}", id, e.getStatusCode());
            return null;
        }
    }

    /**
     * Busca as despesas com cota parlamentar de um deputado.
     *
     * @param id   ID do deputado
     * @param ano  Ano de referência (null = ano atual)
     * @param mes  Mês de referência (null = todos)
     */
    public List<DespesaDTO> buscarDespesas(Long id, Integer ano, Integer mes) {
        log.debug("Buscando despesas do deputado {} (ano={}, mes={})", id, ano, mes);
        try {
            ApiWrapper<DespesaDTO> response = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/deputados/{id}/despesas")
                                .queryParam("itens", 100)
                                .queryParam("pagina", 1);
                        if (ano != null) builder.queryParam("ano", ano);
                        if (mes != null) builder.queryParam("mes", mes);
                        return builder.build(id);
                    })
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ApiWrapper<DespesaDTO>>() {})
                    .block();

            return response != null && response.getDados() != null ? response.getDados() : List.of();
        } catch (WebClientResponseException e) {
            log.error("Erro ao buscar despesas do deputado {}: status={}", id, e.getStatusCode());
            return List.of();
        }
    }

    /**
     * Busca o histórico de votações nominais de um deputado.
     */
    public List<VotacaoDTO> buscarVotacoes(Long id) {
        log.debug("Buscando votações do deputado {}", id);
        try {
            ApiWrapper<VotacaoDTO> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/deputados/{id}/votacoes")
                            .queryParam("itens", 100)
                            .queryParam("pagina", 1)
                            .build(id))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ApiWrapper<VotacaoDTO>>() {})
                    .block();

            return response != null && response.getDados() != null ? response.getDados() : List.of();
        } catch (WebClientResponseException e) {
            log.error("Erro ao buscar votações do deputado {}: status={}", id, e.getStatusCode());
            return List.of();
        }
    }

    /**
     * Busca proposições (projetos de lei) de autoria de um deputado.
     */
    public List<ProposicaoDTO> buscarProposicoes(Long idDeputado, Integer ano) {
        log.debug("Buscando proposições do deputado {} (ano={})", idDeputado, ano);
        try {
            ApiWrapper<ProposicaoDTO> response = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/proposicoes")
                                .queryParam("idDeputadoAutor", idDeputado)
                                .queryParam("itens", 100)
                                .queryParam("pagina", 1)
                                .queryParam("ordem", "DESC")
                                .queryParam("ordenarPor", "id");
                        if (ano != null) builder.queryParam("ano", ano);
                        return builder.build();
                    })
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ApiWrapper<ProposicaoDTO>>() {})
                    .block();

            return response != null && response.getDados() != null ? response.getDados() : List.of();
        } catch (WebClientResponseException e) {
            log.error("Erro ao buscar proposições do deputado {}: status={}", idDeputado, e.getStatusCode());
            return List.of();
        }
    }

    /**
     * Testa conectividade com a API da Câmara.
     */
    public boolean testarConectividade() {
        try {
            webClient.get()
                    .uri("/deputados?itens=1")
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return true;
        } catch (Exception e) {
            log.warn("API da Câmara indisponível: {}", e.getMessage());
            return false;
        }
    }
}
