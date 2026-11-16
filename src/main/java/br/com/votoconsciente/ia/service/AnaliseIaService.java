package br.com.votoconsciente.ia.service;

import br.com.votoconsciente.camara.client.CamaraApiClient;
import br.com.votoconsciente.camara.dto.DespesaDTO;
import br.com.votoconsciente.camara.dto.ProposicaoDTO;
import br.com.votoconsciente.camara.dto.VotoParlamentarDTO;
import br.com.votoconsciente.deputado.dto.DeputadoResponseDTO;
import br.com.votoconsciente.deputado.service.DeputadoService;
import br.com.votoconsciente.ia.client.GeminiApiClient;
import br.com.votoconsciente.ia.dto.AnaliseIaResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Serviço responsável por orquestrar a coleta de dados de um parlamentar
 * e submetê-los ao modelo de Inteligência Artificial para análise analítica de perfil.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnaliseIaService {

    private final DeputadoService deputadoService;
    private final CamaraApiClient camaraApiClient;
    private final GeminiApiClient geminiApiClient;
    private final ObjectMapper objectMapper;

    /**
     * Gera ou recupera do cache o relatório analítico de IA para o deputado especificado.
     */
    @Cacheable(value = "analise-ia", key = "#camaraId")
    public Optional<AnaliseIaResponseDTO> analisarDeputado(Long camaraId) {
        log.info("Iniciando processo de análise por IA para o deputado ID={}", camaraId);

        Optional<DeputadoResponseDTO> depOpt = deputadoService.buscarPorCamaraId(camaraId);
        if (depOpt.isEmpty()) {
            log.warn("Deputado {} não encontrado para análise de IA", camaraId);
            return Optional.empty();
        }

        DeputadoResponseDTO dep = depOpt.get();

        // 1. Coleta os dados complementares mais recentes
        List<DespesaDTO> despesas = camaraApiClient.buscarDespesas(camaraId, null, null);
        List<ProposicaoDTO> proposicoes = camaraApiClient.buscarProposicoes(camaraId, null);
        List<VotoParlamentarDTO> votos = camaraApiClient.buscarVotosPorDeputado(camaraId, 15);

        // 2. Sumariza os dados para não estourar a janela de contexto e economizar tokens
        Map<String, Object> resumoDados = montarResumoParaIa(dep, despesas, proposicoes, votos);

        try {
            String jsonContexto = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(resumoDados);
            AnaliseIaResponseDTO analise = geminiApiClient.analisarDadosParlamentar(
                    dep.getCamaraId(),
                    dep.getNome(),
                    dep.getSiglaPartido(),
                    dep.getSiglaUf(),
                    jsonContexto
            );
            return Optional.of(analise);
        } catch (Exception e) {
            log.error("Falha ao serializar contexto para IA: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Object> montarResumoParaIa(
            DeputadoResponseDTO dep,
            List<DespesaDTO> despesas,
            List<ProposicaoDTO> proposicoes,
            List<VotoParlamentarDTO> votos
    ) {
        Map<String, Object> contexto = new LinkedHashMap<>();

        // Perfil básico
        contexto.put("parlamentar", Map.of(
                "nome", dep.getNome(),
                "partido", dep.getSiglaPartido() != null ? dep.getSiglaPartido() : "Sem Partido",
                "uf", dep.getSiglaUf() != null ? dep.getSiglaUf() : "",
                "situacao", dep.getSituacao() != null ? dep.getSituacao() : "Em exercício",
                "escolaridade", dep.getEscolaridade() != null ? dep.getEscolaridade() : "Não informada",
                "municipioNascimento", dep.getMunicipioNascimento() != null ? dep.getMunicipioNascimento() : ""
        ));

        // Sumário de Gastos (Cota Parlamentar)
        BigDecimal totalGastos = BigDecimal.ZERO;
        Map<String, BigDecimal> gastosPorTipo = new HashMap<>();

        for (DespesaDTO d : despesas) {
            BigDecimal valor = d.getValorDocumento() != null ? d.getValorDocumento().abs() : BigDecimal.ZERO;
            totalGastos = totalGastos.add(valor);

            String tipo = d.getTipoDespesa() != null ? d.getTipoDespesa() : "OUTROS";
            gastosPorTipo.put(tipo, gastosPorTipo.getOrDefault(tipo, BigDecimal.ZERO).add(valor));
        }

        contexto.put("gastos_cota_parlamentar", Map.of(
                "total_registrado_recente_reais", totalGastos,
                "quantidade_notas_analisadas", despesas.size(),
                "principais_categorias_gastos", gastosPorTipo
        ));

        // Sumário de Proposições (Projetos de Lei)
        List<Map<String, Object>> listaProposicoes = proposicoes.stream()
                .limit(10)
                .map(p -> Map.<String, Object>of(
                        "tipo", p.getSiglaTipo() != null ? p.getSiglaTipo() : "",
                        "numero", p.getNumero() != null ? p.getNumero() : 0,
                        "ano", p.getAno() != null ? p.getAno() : 0,
                        "ementa", p.getEmenta() != null ? p.getEmenta() : ""
                ))
                .toList();

        contexto.put("ultimos_projetos_de_lei", listaProposicoes);

        // Sumário de Votações Nominais
        List<Map<String, Object>> listaVotos = votos.stream()
                .limit(10)
                .map(v -> Map.<String, Object>of(
                        "votacao", v.getTituloVotacao() != null ? v.getTituloVotacao() : "",
                        "posicionamento", v.getTipoVoto() != null ? v.getTipoVoto() : "",
                        "data", v.getDataHora() != null ? v.getDataHora() : ""
                ))
                .toList();

        contexto.put("amostra_votacoes_nominais", listaVotos);

        return contexto;
    }
}
