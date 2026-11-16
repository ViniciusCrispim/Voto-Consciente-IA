package br.com.votoconsciente.camara.client;

import br.com.votoconsciente.camara.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
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
     */
    public List<DespesaDTO> buscarDespesas(Long id, Integer ano, Integer mes) {
        log.debug("Buscando despesas do deputado {} (ano={}, mes={})", id, ano, mes);
        try {
            ApiWrapper<DespesaDTO> response = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/deputados/{id}/despesas")
                                .queryParam("itens", 100)
                                .queryParam("pagina", 1)
                                .queryParam("ordem", "DESC")
                                .queryParam("ordenarPor", "dataDocumento");
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
     * Lista as votações mais recentes da Câmara dos Deputados.
     */
    public List<VotacaoDTO> listarVotacoesRecentes(int itens) {
        log.debug("Buscando {} votações mais recentes da Câmara", itens);
        try {
            ApiWrapper<VotacaoDTO> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/votacoes")
                            .queryParam("itens", itens)
                            .queryParam("ordem", "DESC")
                            .queryParam("ordenarPor", "dataHoraRegistro")
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ApiWrapper<VotacaoDTO>>() {})
                    .block();

            return response != null && response.getDados() != null ? response.getDados() : List.of();
        } catch (WebClientResponseException e) {
            log.error("Erro ao listar votações da Câmara: status={}", e.getStatusCode());
            return List.of();
        }
    }

    /**
     * Busca os votos nominais de uma votação específica.
     */
    public List<VotoRegistroDTO> buscarVotosDeVotacao(String idVotacao) {
        log.debug("Buscando votos nominais da votação {}", idVotacao);
        try {
            ApiWrapper<VotoRegistroDTO> response = webClient.get()
                    .uri("/votacoes/{id}/votos", idVotacao)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ApiWrapper<VotoRegistroDTO>>() {})
                    .block();

            return response != null && response.getDados() != null ? response.getDados() : List.of();
        } catch (WebClientResponseException e) {
            // Muitas votações são simbólicas e não possuem votos nominais
            log.debug("Votação {} não possui votos nominais registrados: status={}", idVotacao, e.getStatusCode());
            return List.of();
        }
    }

    /**
     * Busca o histórico de posicionamento do deputado nas votações nominais recentes.
     */
    public List<VotoParlamentarDTO> buscarVotosPorDeputado(Long camaraId, int limiteVotacoes) {
        log.debug("Cruzando votos nominais recentes para o deputado {}", camaraId);
        List<VotoParlamentarDTO> votosDoDeputado = new ArrayList<>();
        List<VotacaoDTO> votacoes = listarVotacoesRecentes(limiteVotacoes);

        for (VotacaoDTO votacao : votacoes) {
            List<VotoRegistroDTO> votos = buscarVotosDeVotacao(votacao.getId());
            for (VotoRegistroDTO reg : votos) {
                if (reg.getDeputado_() != null && camaraId.equals(reg.getDeputado_().getId())) {
                    votosDoDeputado.add(VotoParlamentarDTO.builder()
                            .idVotacao(votacao.getId())
                            .tituloVotacao(votacao.getDescricao())
                            .dataHora(votacao.getDataHoraRegistro() != null ? votacao.getDataHoraRegistro() : votacao.getData())
                            .tipoVoto(reg.getTipoVoto())
                            .aprovacaoGeral(votacao.getAprovacao())
                            .siglaOrgao(votacao.getSiglaOrgao())
                            .build());
                    break;
                }
            }
        }

        return votosDoDeputado;
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
