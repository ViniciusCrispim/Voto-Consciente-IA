package br.com.votoconsciente.ia.service;

import br.com.votoconsciente.camara.client.CamaraApiClient;
import br.com.votoconsciente.camara.dto.DespesaDTO;
import br.com.votoconsciente.camara.dto.ProposicaoDTO;
import br.com.votoconsciente.camara.dto.VotoParlamentarDTO;
import br.com.votoconsciente.deputado.dto.DeputadoResponseDTO;
import br.com.votoconsciente.deputado.service.DeputadoService;
import br.com.votoconsciente.ia.client.GeminiApiClient;
import br.com.votoconsciente.ia.dto.AnaliseIaResponseDTO;
import br.com.votoconsciente.ia.dto.PerguntaResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço responsável por orquestrar a coleta de dados de um parlamentar,
 * realizar o roteamento semântico de perguntas e submetê-los ao modelo de IA.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnaliseIaService {

    private final DeputadoService deputadoService;
    private final CamaraApiClient camaraApiClient;
    private final GeminiApiClient geminiApiClient;
    private final ObjectMapper objectMapper;

    private static final Pattern ANO_PATTERN = Pattern.compile("\\b(201[9]|202[0-9])\\b");

    private static final List<String> TERMOS_DESPESAS = List.of(
            "gasto", "gastou", "gastar", "despesa", "cota", "ceap", "dinheiro", "verba",
            "reembolso", "nota", "combust", "passagem", "viagem", "avião", "aereo", "aéreo",
            "divulga", "fornecedor", "valor", "quanto", "custo", "aluguel", "locação", "consultoria"
    );

    private static final List<String> TERMOS_PROPOSICOES = List.of(
            "projeto", "lei", "pl", "pec", "propos", "autoria", "autor", "apresentou",
            "criou", "tema", "bandeira", "pauta", "trabalh", "6x1", "jornada", "saúde",
            "educação", "segurança", "imposto", "direito", "mulher", "lgbt", "ambiente", "fez", "atuação"
    );

    private static final List<String> TERMOS_VOTACOES = List.of(
            "voto", "votou", "votar", "votaç", "contra", "favor", "aprov", "rejeit",
            "plenário", "plenario", "posição", "posicionamento", "absten", "obstru"
    );

    /**
     * Gera ou recupera do cache o relatório analítico de IA para o deputado especificado.
     */
    @Cacheable(value = "analise-ia", key = "#camaraId", unless = "#result == null || #result.isEmpty()")
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
        List<VotoParlamentarDTO> votos = camaraApiClient.buscarVotosPorDeputado(camaraId, 12);

        // 2. Sumariza os dados para o contexto da IA
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

    /**
     * Responde a uma pergunta aberta em linguagem natural sobre o parlamentar
     * utilizando Roteamento Semântico + RAG (Retrieval-Augmented Generation).
     */
    public Optional<PerguntaResponseDTO> responderPergunta(Long camaraId, String pergunta) {
        log.info("Processando pergunta para deputado {}: '{}'", camaraId, pergunta);

        Optional<DeputadoResponseDTO> depOpt = deputadoService.buscarPorCamaraId(camaraId);
        if (depOpt.isEmpty()) {
            log.warn("Deputado {} não encontrado para responder pergunta", camaraId);
            return Optional.empty();
        }

        DeputadoResponseDTO dep = depOpt.get();
        String perguntaLower = pergunta != null ? pergunta.toLowerCase(Locale.ROOT) : "";

        // 1. Extrai ano se mencionado na pergunta (ex: "em 2024")
        Integer anoFiltro = extrairAnoDaPergunta(perguntaLower);

        // 2. Roteamento Semântico: identifica quais módulos precisam ser consultados
        boolean precisaDespesas = contemAlgumTermo(perguntaLower, TERMOS_DESPESAS);
        boolean precisaProposicoes = contemAlgumTermo(perguntaLower, TERMOS_PROPOSICOES);
        boolean precisaVotacoes = contemAlgumTermo(perguntaLower, TERMOS_VOTACOES);

        // Se a pergunta for genérica (não bateu em nenhum termo específico), aciona todos os módulos
        if (!precisaDespesas && !precisaProposicoes && !precisaVotacoes) {
            precisaDespesas = true;
            precisaProposicoes = true;
            precisaVotacoes = true;
        }

        List<String> modulosAcionados = new ArrayList<>();
        List<String> fontesConsultadas = new ArrayList<>();
        modulosAcionados.add("PERFIL_CADASTRO");
        fontesConsultadas.add(String.format("Perfil Oficial da Câmara: %s (%s-%s) - Legislatura %s",
                dep.getNome(), dep.getSiglaPartido(), dep.getSiglaUf(), dep.getIdLegislatura()));

        List<DespesaDTO> despesas = List.of();
        List<ProposicaoDTO> proposicoes = List.of();
        List<VotoParlamentarDTO> votos = List.of();

        if (precisaDespesas) {
            modulosAcionados.add("DESPESAS_CEAP");
            despesas = camaraApiClient.buscarDespesas(camaraId, anoFiltro, null);
            fontesConsultadas.add(String.format("Cota Parlamentar (CEAP): %d notas fiscais consultadas%s",
                    despesas.size(), anoFiltro != null ? " (Ano " + anoFiltro + ")" : ""));
        }

        if (precisaProposicoes) {
            modulosAcionados.add("PROPOSICOES_LEGISLATIVAS");
            proposicoes = camaraApiClient.buscarProposicoes(camaraId, anoFiltro);
            fontesConsultadas.add(String.format("Proposições e Projetos de Lei: %d matérias de autoria consultadas",
                    proposicoes.size()));
        }

        if (precisaVotacoes) {
            modulosAcionados.add("VOTACOES_PLENARIO");
            votos = camaraApiClient.buscarVotosPorDeputado(camaraId, 15);
            fontesConsultadas.add(String.format("Votações Nominais Recentes da Câmara: %d registros verificados",
                    votos.size()));
        }

        // 3. Monta o contexto direcionado e a síntese local baseada nos dados reais
        Map<String, Object> contexto = montarResumoParaIa(dep, despesas, proposicoes, votos);
        String respostaLocal = construirRespostaNaturalComDadosReais(
                dep, pergunta, despesas, proposicoes, votos, precisaDespesas, precisaProposicoes, precisaVotacoes
        );

        try {
            String jsonContexto = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(contexto);
            PerguntaResponseDTO response = geminiApiClient.responderPerguntaParlamentar(
                    dep.getCamaraId(),
                    dep.getNome(),
                    dep.getSiglaPartido(),
                    dep.getSiglaUf(),
                    pergunta,
                    modulosAcionados,
                    fontesConsultadas,
                    jsonContexto,
                    respostaLocal
            );
            return Optional.of(response);
        } catch (Exception e) {
            log.error("Erro ao processar pergunta sobre deputado {}: {}", camaraId, e.getMessage());
            return Optional.empty();
        }
    }

    private Integer extrairAnoDaPergunta(String texto) {
        Matcher matcher = ANO_PATTERN.matcher(texto);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private boolean contemAlgumTermo(String texto, List<String> termos) {
        for (String termo : termos) {
            if (texto.contains(termo)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Constrói uma resposta em linguagem natural diretamente a partir dos dados reais coletados da Câmara,
     * usada caso a API externa da LLM não esteja acessível.
     */
    private String construirRespostaNaturalComDadosReais(
            DeputadoResponseDTO dep,
            String pergunta,
            List<DespesaDTO> despesas,
            List<ProposicaoDTO> proposicoes,
            List<VotoParlamentarDTO> votos,
            boolean incluiDespesas,
            boolean incluiProposicoes,
            boolean incluiVotacoes
    ) {
        NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("Com base nos dados oficiais da Câmara dos Deputados sobre %s (%s-%s): ",
                dep.getNome(), dep.getSiglaPartido(), dep.getSiglaUf()));

        if (incluiDespesas) {
            BigDecimal total = BigDecimal.ZERO;
            Map<String, BigDecimal> porCategoria = new HashMap<>();
            for (DespesaDTO d : despesas) {
                BigDecimal val = d.getValorDocumento() != null ? d.getValorDocumento().abs() : BigDecimal.ZERO;
                total = total.add(val);
                String cat = d.getTipoDespesa() != null ? d.getTipoDespesa() : "Outros";
                porCategoria.put(cat, porCategoria.getOrDefault(cat, BigDecimal.ZERO).add(val));
            }

            List<Map.Entry<String, BigDecimal>> maiores = porCategoria.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(3)
                    .toList();

            sb.append(String.format("\n\n• **Gastos da Cota Parlamentar (CEAP):** Nas %d notas fiscais mais recentes analisadas, o total registrado é de %s. ",
                    despesas.size(), moeda.format(total)));

            if (!maiores.isEmpty()) {
                sb.append("As principais categorias de despesa são: ");
                List<String> descCat = maiores.stream()
                        .map(e -> e.getKey() + " (" + moeda.format(e.getValue()) + ")")
                        .toList();
                sb.append(String.join(", ", descCat)).append(".");
            }
        }

        if (incluiProposicoes) {
            sb.append(String.format("\n\n• **Projetos e Proposições Legislativas:** Foram encontradas %d proposições recentes de autoria/coautoria. ",
                    proposicoes.size()));

            List<ProposicaoDTO> destaques = proposicoes.stream().limit(4).toList();
            if (!destaques.isEmpty()) {
                sb.append("Entre os destaques recentes estão: ");
                List<String> textos = destaques.stream()
                        .map(p -> String.format("%s %d/%d (%s)",
                                p.getSiglaTipo(), p.getNumero(), p.getAno(),
                                resumirTexto(p.getEmenta(), 110)))
                        .toList();
                sb.append(String.join("; ", textos)).append(".");
            }
        }

        if (incluiVotacoes) {
            if (!votos.isEmpty()) {
                sb.append(String.format("\n\n• **Posicionamento em Votações Nominais:** Nos últimos registros nominais verificados em Plenário, constam %d votos, incluindo: ",
                        votos.size()));
                List<String> listaVotos = votos.stream()
                        .limit(3)
                        .map(v -> String.format("voto '%s' em '%s' (%s)",
                                v.getTipoVoto(), resumirTexto(v.getTituloVotacao(), 80), v.getDataHora()))
                        .toList();
                sb.append(String.join("; ", listaVotos)).append(".");
            } else {
                sb.append("\n\n• **Votações Nominais:** Nas últimas votações simbólicas/nominais consultadas na amostragem recente, não houve registro de voto nominal individualizado para este ID.");
            }
        }

        if (dep.getGabinete() != null && dep.getGabinete().getTelefone() != null) {
            sb.append(String.format("\n\n• **Contato Institucional:** E-mail %s | Gabinete %s, Prédio %s, Telefone %s.",
                    dep.getEmail(), dep.getGabinete().getSala(), dep.getGabinete().getPredio(), dep.getGabinete().getTelefone()));
        }

        return sb.toString();
    }

    private String resumirTexto(String texto, int max) {
        if (texto == null) return "";
        String limpo = texto.replaceAll("\\s+", " ").trim();
        return limpo.length() <= max ? limpo : limpo.substring(0, max) + "...";
    }

    private Map<String, Object> montarResumoParaIa(
            DeputadoResponseDTO dep,
            List<DespesaDTO> despesas,
            List<ProposicaoDTO> proposicoes,
            List<VotoParlamentarDTO> votos
    ) {
        Map<String, Object> contexto = new LinkedHashMap<>();

        contexto.put("parlamentar", Map.of(
                "nome", dep.getNome() != null ? dep.getNome() : "",
                "partido", dep.getSiglaPartido() != null ? dep.getSiglaPartido() : "Sem Partido",
                "uf", dep.getSiglaUf() != null ? dep.getSiglaUf() : "",
                "email", dep.getEmail() != null ? dep.getEmail() : "",
                "situacao", dep.getSituacao() != null ? dep.getSituacao() : "Em exercício",
                "escolaridade", dep.getEscolaridade() != null ? dep.getEscolaridade() : "Não informada",
                "municipioNascimento", dep.getMunicipioNascimento() != null ? dep.getMunicipioNascimento() : ""
        ));

        BigDecimal totalGastos = BigDecimal.ZERO;
        Map<String, BigDecimal> gastosPorTipo = new HashMap<>();

        for (DespesaDTO d : despesas) {
            BigDecimal valor = d.getValorDocumento() != null ? d.getValorDocumento().abs() : BigDecimal.ZERO;
            totalGastos = totalGastos.add(valor);

            String tipo = d.getTipoDespesa() != null ? d.getTipoDespesa() : "OUTROS";
            gastosPorTipo.put(tipo, gastosPorTipo.getOrDefault(tipo, BigDecimal.ZERO).add(valor));
        }

        List<Map<String, Object>> notasRecentes = despesas.stream()
                .limit(10)
                .map(d -> Map.<String, Object>of(
                        "data", d.getDataDocumento() != null ? d.getDataDocumento() : "",
                        "tipo", d.getTipoDespesa() != null ? d.getTipoDespesa() : "",
                        "fornecedor", d.getNomeFornecedor() != null ? d.getNomeFornecedor() : "",
                        "valor", d.getValorDocumento() != null ? d.getValorDocumento().abs() : BigDecimal.ZERO
                ))
                .toList();

        contexto.put("gastos_cota_parlamentar", Map.of(
                "total_registrado_amostra_reais", totalGastos,
                "quantidade_notas_analisadas", despesas.size(),
                "totais_por_categoria", gastosPorTipo,
                "ultimas_notas_fiscais", notasRecentes
        ));

        List<Map<String, Object>> listaProposicoes = proposicoes.stream()
                .limit(15)
                .map(p -> Map.<String, Object>of(
                        "tipo", p.getSiglaTipo() != null ? p.getSiglaTipo() : "",
                        "numero", p.getNumero() != null ? p.getNumero() : 0,
                        "ano", p.getAno() != null ? p.getAno() : 0,
                        "ementa", p.getEmenta() != null ? p.getEmenta() : ""
                ))
                .toList();

        contexto.put("ultimos_projetos_de_lei", listaProposicoes);

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
