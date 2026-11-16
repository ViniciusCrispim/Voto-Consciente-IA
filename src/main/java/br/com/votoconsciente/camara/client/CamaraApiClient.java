package br.com.votoconsciente.camara.client;

import br.com.votoconsciente.camara.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Cliente HTTP para a API de Dados Abertos da Câmara dos Deputados.
 * Documentação: https://dadosabertos.camara.leg.br/api/v2/api-docs
 */
@Slf4j
@Component
public class CamaraApiClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public CamaraApiClient(@Qualifier("camaraWebClient") WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Lista todos os deputados de uma legislatura.
     */
    public List<DeputadoResumoDTO> listarDeputados(Integer idLegislatura, int pagina, int itensPorPagina) {
        log.debug("Buscando deputados na Câmara: legislatura={}, pagina={}", idLegislatura, pagina);
        try {
            String body = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/deputados")
                            .queryParam("idLegislatura", idLegislatura)
                            .queryParam("pagina", pagina)
                            .queryParam("itens", itensPorPagina)
                            .queryParam("ordem", "ASC")
                            .queryParam("ordenarPor", "nome")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (body == null) return List.of();
            ApiWrapper<DeputadoResumoDTO> response = objectMapper.readValue(
                    body, new TypeReference<ApiWrapper<DeputadoResumoDTO>>() {});

            return response != null && response.getDados() != null ? response.getDados() : List.of();
        } catch (Exception e) {
            log.error("Erro ao listar deputados na Câmara: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Busca o detalhe completo de um deputado pelo ID.
     */
    public DeputadoDetalheDTO buscarDeputadoPorId(Long id) {
        log.debug("Buscando detalhe do deputado {} na Câmara", id);
        try {
            String body = webClient.get()
                    .uri("/deputados/{id}", id)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = body != null ? objectMapper.readTree(body) : null;
            if (root != null && root.has("dados")) {
                return objectMapper.treeToValue(root.get("dados"), DeputadoDetalheDTO.class);
            }
            return null;
        } catch (Exception e) {
            log.error("Erro ao buscar deputado {}: {}", id, e.getMessage());
            return null;
        }
    }

    /**
     * Busca as despesas com cota parlamentar de um deputado.
     */
    public List<DespesaDTO> buscarDespesas(Long id, Integer ano, Integer mes) {
        log.debug("Buscando despesas do deputado {} (ano={}, mes={})", id, ano, mes);
        try {
            String body = webClient.get()
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
                    .bodyToMono(String.class)
                    .block();

            if (body == null) return List.of();
            ApiWrapper<DespesaDTO> response = objectMapper.readValue(
                    body, new TypeReference<ApiWrapper<DespesaDTO>>() {});

            return response != null && response.getDados() != null ? response.getDados() : List.of();
        } catch (Exception e) {
            log.error("Erro ao buscar despesas do deputado {}: {}", id, e.getMessage());
            return List.of();
        }
    }

    /**
     * Lista as votações mais recentes da Câmara dos Deputados.
     */
    public List<VotacaoDTO> listarVotacoesRecentes(int itens) {
        log.debug("Buscando {} votações mais recentes da Câmara", itens);
        try {
            String body = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/votacoes")
                            .queryParam("itens", itens)
                            .queryParam("ordem", "DESC")
                            .queryParam("ordenarPor", "dataHoraRegistro")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (body == null) return List.of();
            ApiWrapper<VotacaoDTO> response = objectMapper.readValue(
                    body, new TypeReference<ApiWrapper<VotacaoDTO>>() {});

            return response != null && response.getDados() != null ? response.getDados() : List.of();
        } catch (Exception e) {
            log.error("Erro ao listar votações da Câmara: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Busca os votos nominais de uma votação específica.
     */
    public List<VotoRegistroDTO> buscarVotosDeVotacao(String idVotacao) {
        log.debug("Buscando votos nominais da votação {}", idVotacao);
        try {
            String body = webClient.get()
                    .uri("/votacoes/{id}/votos", idVotacao)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (body == null) return List.of();
            ApiWrapper<VotoRegistroDTO> response = objectMapper.readValue(
                    body, new TypeReference<ApiWrapper<VotoRegistroDTO>>() {});

            return response != null && response.getDados() != null ? response.getDados() : List.of();
        } catch (Exception e) {
            log.debug("Votação {} não possui votos nominais registrados: {}", idVotacao, e.getMessage());
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
            String body = webClient.get()
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
                    .bodyToMono(String.class)
                    .block();

            if (body == null) return List.of();
            ApiWrapper<ProposicaoDTO> response = objectMapper.readValue(
                    body, new TypeReference<ApiWrapper<ProposicaoDTO>>() {});

            return response != null && response.getDados() != null ? response.getDados() : List.of();
        } catch (Exception e) {
            log.error("Erro ao buscar proposições do deputado {}: {}", idDeputado, e.getMessage());
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
