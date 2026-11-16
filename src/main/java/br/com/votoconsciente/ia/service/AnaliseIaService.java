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
import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço responsável por orquestrar a coleta de dados de um parlamentar,
 * realizar o roteamento semântico de perguntas, auditar termos específicos
 * contra alucinações e submetê-los ao modelo de IA.
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
            "divulga", "fornecedor", "valor", "quanto", "custo", "aluguel", "locação", "consultoria",
            "comprou", "pagou", "pagamento", "fatura", "recibo"
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

    private static final List<String> TERMOS_PATRIMONIO_VIDA_PRIVADA = List.of(
            "imovel", "imoveis", "casa", "apartamento", "mansao", "fazenda", "terreno",
            "patrimonio", "bens", "fortuna", "riqueza", "miami", "exterior", "carro particular",
            "estado civil", "casado", "namorad", "familia", "filho", "religiao"
    );

    /**
     * Palavras genéricas de estrutura de pergunta que não representam um tema específico de filtro.
     */
    private static final Set<String> STOPWORDS_PERGUNTA = new HashSet<>(List.of(
            "qual", "quais", "quanto", "quantos", "quantas", "como", "onde", "quando", "porque",
            "quem", "sobre", "para", "pelo", "pela", "pelos", "pelas", "com", "sem", "nos", "nas",
            "dos", "das", "uma", "umas", "uns", "esse", "essa", "este", "esta", "isso", "isto",
            "dele", "dela", "deles", "delas", "seu", "sua", "seus", "suas", "tem", "teve", "tiveram",
            "foi", "foram", "ser", "estao", "sao", "deputado", "deputada", "parlamentar",
            "politico", "politica", "candidato", "candidata", "camara", "federal", "brasil", "brasileiro",
            "gasto", "gastos", "gastou", "gastar", "despesa", "despesas", "cota", "ceap", "verba",
            "dinheiro", "valor", "valores", "nota", "notas", "fiscal", "fiscais", "reembolso",
            "projeto", "projetos", "lei", "leis", "proposicao", "proposicoes", "proposta", "propostas",
            "voto", "votos", "votou", "votacao", "votacoes", "plenario", "atuacao", "mandato",
            "principal", "principais", "recente", "recentes", "ultimo", "ultimos", "ultima", "ultimas",
            "ano", "mes", "meses", "anos", "mais", "menos", "muito", "pouco", "tudo", "todos", "todas",
            "fez", "fazer", "apresentou", "criou", "total", "resumo", "geral", "fale", "diga", "mostre"
    ));

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

        List<DespesaDTO> despesas = camaraApiClient.buscarDespesas(camaraId, null, null);
        List<ProposicaoDTO> proposicoes = camaraApiClient.buscarProposicoes(camaraId, null);
        List<VotoParlamentarDTO> votos = camaraApiClient.buscarVotosPorDeputado(camaraId, 12);

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
     * utilizando Roteamento Semântico + Auditoria de Termos Específicos + RAG.
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
        String perguntaNormalizada = normalizarTexto(perguntaLower);

        Integer anoFiltro = extrairAnoDaPergunta(perguntaLower);

        boolean precisaDespesas = contemAlgumTermo(perguntaLower, TERMOS_DESPESAS)
                || contemAlgumTermo(perguntaNormalizada, TERMOS_DESPESAS);
        boolean precisaProposicoes = contemAlgumTermo(perguntaLower, TERMOS_PROPOSICOES)
                || contemAlgumTermo(perguntaNormalizada, TERMOS_PROPOSICOES);
        boolean precisaVotacoes = contemAlgumTermo(perguntaLower, TERMOS_VOTACOES)
                || contemAlgumTermo(perguntaNormalizada, TERMOS_VOTACOES);
        boolean mencionaPatrimonioOuPrivado = contemAlgumTermo(perguntaNormalizada, TERMOS_PATRIMONIO_VIDA_PRIVADA);

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
            fontesConsultadas.add(String.format("Cota Parlamentar (CEAP): %d notas fiscais auditadas%s",
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

        // Extrai palavras-chave específicas do assunto perguntado (ex: "miami", "cirurgia", "plastica", "seguranca", "imposto")
        List<String> termosAssunto = extrairTermosAssuntoEspecifico(pergunta, dep.getNome());

        Map<String, Object> contexto = montarResumoParaIa(dep, despesas, proposicoes, votos);
        contexto.put("termos_especificos_buscados_pelo_cidadao", termosAssunto);

        String respostaLocal = construirRespostaDirecionadaComAuditoria(
                dep,
                pergunta,
                termosAssunto,
                mencionaPatrimonioOuPrivado,
                despesas,
                proposicoes,
                votos,
                precisaDespesas,
                precisaProposicoes,
                precisaVotacoes
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

    /**
     * Extrai termos substantivos específicos da pergunta do usuário para auditar se existem nos registros da Câmara.
     */
    private List<String> extrairTermosAssuntoEspecifico(String pergunta, String nomeDeputado) {
        if (pergunta == null || pergunta.isBlank()) return List.of();

        Set<String> nomesParlamentar = new HashSet<>();
        if (nomeDeputado != null) {
            for (String p : normalizarTexto(nomeDeputado).split("\\s+")) {
                if (p.length() >= 2) nomesParlamentar.add(p);
            }
        }

        String limpo = normalizarTexto(pergunta).replaceAll("[^a-z0-9\\s]", " ");
        List<String> termos = new ArrayList<>();
        for (String token : limpo.split("\\s+")) {
            if (token.length() >= 4
                    && !STOPWORDS_PERGUNTA.contains(token)
                    && !nomesParlamentar.contains(token)
                    && !token.matches("20[0-9]{2}")) {
                if (!termos.contains(token)) {
                    termos.add(token);
                }
            }
        }
        return termos;
    }

    private String normalizarTexto(String input) {
        if (input == null) return "";
        String nfd = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
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
     * Constrói uma resposta direta à pergunta do usuário, auditando se os termos específicos
     * mencionados na pergunta realmente existem nas notas fiscais, projetos de lei ou votações.
     */
    private String construirRespostaDirecionadaComAuditoria(
            DeputadoResponseDTO dep,
            String pergunta,
            List<String> termosAssunto,
            boolean mencionaPatrimonioOuPrivado,
            List<DespesaDTO> despesas,
            List<ProposicaoDTO> proposicoes,
            List<VotoParlamentarDTO> votos,
            boolean incluiDespesas,
            boolean incluiProposicoes,
            boolean incluiVotacoes
    ) {
        NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        StringBuilder sb = new StringBuilder();

        // Filtra registros que batem especificamente com os termos da pergunta (se houver termos específicos)
        List<DespesaDTO> despesasRelacionadas = new ArrayList<>();
        List<ProposicaoDTO> proposicoesRelacionadas = new ArrayList<>();
        List<VotoParlamentarDTO> votosRelacionados = new ArrayList<>();

        if (!termosAssunto.isEmpty()) {
            for (DespesaDTO d : despesas) {
                String alvo = normalizarTexto((d.getTipoDespesa() != null ? d.getTipoDespesa() : "") + " "
                        + (d.getNomeFornecedor() != null ? d.getNomeFornecedor() : ""));
                if (contemQualquerRadical(alvo, termosAssunto)) {
                    despesasRelacionadas.add(d);
                }
            }

            for (ProposicaoDTO p : proposicoes) {
                String alvo = normalizarTexto((p.getSiglaTipo() != null ? p.getSiglaTipo() : "") + " "
                        + (p.getEmenta() != null ? p.getEmenta() : ""));
                if (contemQualquerRadical(alvo, termosAssunto)) {
                    proposicoesRelacionadas.add(p);
                }
            }

            for (VotoParlamentarDTO v : votos) {
                String alvo = normalizarTexto(v.getTituloVotacao());
                if (contemQualquerRadical(alvo, termosAssunto)) {
                    votosRelacionados.add(v);
                }
            }
        }

        boolean temTermosEspecificos = !termosAssunto.isEmpty();
        boolean encontrouAlgumaCorrespondencia = !despesasRelacionadas.isEmpty()
                || !proposicoesRelacionadas.isEmpty()
                || !votosRelacionados.isEmpty();

        // CASO 1: O usuário perguntou sobre temas específicos (ex.: "imóveis em Miami", "cirurgia plástica")
        // e NÃO há nenhum registro correspondente nos dados oficiais da Câmara!
        if (temTermosEspecificos && !encontrouAlgumaCorrespondencia) {
            sb.append(String.format(
                    "⚠️ **Resposta Direta:** Não consta **nenhum registro** nos dados oficiais da Câmara dos Deputados sobre %s (%s-%s) relacionado aos termos perguntados (%s).",
                    dep.getNome(), dep.getSiglaPartido(), dep.getSiglaUf(),
                    String.join(", ", termosAssunto.stream().map(t -> "'" + t + "'").toList())
            ));

            if (mencionaPatrimonioOuPrivado) {
                sb.append("\n\n• **Patrimônio Pessoal e Imóveis:** A API de Dados Abertos da Câmara dos Deputados não armazena informações sobre patrimônio particular, bens ou imóveis de parlamentares no Brasil ou no exterior (a declaração de bens é apresentada exclusivamente ao Tribunal Superior Eleitoral — TSE no ato de registro de candidatura).");
            }

            if (incluiDespesas) {
                BigDecimal totalGeral = somarDespesas(despesas);
                sb.append(String.format(
                        "\n\n• **Auditoria da Cota Parlamentar (CEAP):** Foram verificadas as %d notas fiscais mais recentes do mandato (que somam %s) e o valor gasto com os itens questionados (%s) é de **R$ 0,00 (zero notas fiscais)**. Cabe ressaltar que o Ato da Mesa nº 43/2009 da Câmara veda o uso da Cota Parlamentar para despesas de caráter pessoal, estético ou médico.",
                        despesas.size(), moeda.format(totalGeral),
                        String.join("/", termosAssunto)
                ));

                List<Map.Entry<String, BigDecimal>> maiores = topCategoriasDespesa(despesas, 3);
                if (!maiores.isEmpty()) {
                    List<String> descCat = maiores.stream()
                            .map(e -> e.getKey() + " (" + moeda.format(e.getValue()) + ")")
                            .toList();
                    sb.append(" As despesas reais registradas na cota concentram-se em: ")
                            .append(String.join(", ", descCat)).append(".");
                }
            }

            if (incluiProposicoes && !mencionaPatrimonioOuPrivado && !incluiDespesas) {
                sb.append(String.format(
                        "\n\n• **Auditoria de Projetos de Lei:** Nas %d proposições recentes consultadas de autoria/coautoria da parlamentar, nenhuma trata de %s.",
                        proposicoes.size(), String.join(" ou ", termosAssunto)
                ));
            }

            return sb.toString();
        }

        // CASO 2: O usuário perguntou sobre um tema específico E encontramos registros correspondentes!
        if (temTermosEspecificos && encontrouAlgumaCorrespondencia) {
            sb.append(String.format(
                    "Em resposta à sua pergunta sobre **%s** nos registros oficiais de %s (%s-%s):",
                    String.join(", ", termosAssunto), dep.getNome(), dep.getSiglaPartido(), dep.getSiglaUf()
            ));

            if (!despesasRelacionadas.isEmpty()) {
                BigDecimal totalFiltrado = somarDespesas(despesasRelacionadas);
                sb.append(String.format(
                        "\n\n• **Gastos Encontrados na Cota Parlamentar:** Foram localizadas **%d notas fiscais** correspondentes, somando **%s**. Exemplos de lançamentos: ",
                        despesasRelacionadas.size(), moeda.format(totalFiltrado)
                ));
                List<String> exemplos = despesasRelacionadas.stream()
                        .limit(4)
                        .map(d -> String.format("%s — %s (%s em %s)",
                                d.getTipoDespesa(),
                                d.getNomeFornecedor(),
                                moeda.format(d.getValorDocumento() != null ? d.getValorDocumento().abs() : BigDecimal.ZERO),
                                d.getDataDocumento() != null && d.getDataDocumento().length() >= 10
                                        ? d.getDataDocumento().substring(0, 10) : "data s/ reg."))
                        .toList();
                sb.append(String.join("; ", exemplos)).append(".");
            } else if (incluiDespesas) {
                sb.append(String.format(
                        "\n\n• **Gastos na Cota Parlamentar:** Nas %d notas fiscais consultadas, não há despesas registradas especificamente sob esse termo (R$ 0,00).",
                        despesas.size()
                ));
            }

            if (!proposicoesRelacionadas.isEmpty()) {
                sb.append(String.format(
                        "\n\n• **Projetos e Proposições Relacionados (%d encontrados):** ",
                        proposicoesRelacionadas.size()
                ));
                List<String> textos = proposicoesRelacionadas.stream()
                        .limit(5)
                        .map(p -> String.format("%s %d/%d (%s)",
                                p.getSiglaTipo(), p.getNumero(), p.getAno(),
                                resumirTexto(p.getEmenta(), 120)))
                        .toList();
                sb.append(String.join("; ", textos)).append(".");
            }

            if (!votosRelacionados.isEmpty()) {
                sb.append(String.format(
                        "\n\n• **Votações Relacionadas (%d encontradas):** ",
                        votosRelacionados.size()
                ));
                List<String> listaVotos = votosRelacionados.stream()
                        .limit(4)
                        .map(v -> String.format("voto '%s' em '%s' (%s)",
                                v.getTipoVoto(), resumirTexto(v.getTituloVotacao(), 90), v.getDataHora()))
                        .toList();
                sb.append(String.join("; ", listaVotos)).append(".");
            }

            return sb.toString();
        }

        // CASO 3: Pergunta aberta / geral (ex.: "Quais são os principais gastos e projetos da deputada?")
        sb.append(String.format("Com base nos dados oficiais da Câmara dos Deputados sobre %s (%s-%s):",
                dep.getNome(), dep.getSiglaPartido(), dep.getSiglaUf()));

        if (incluiDespesas) {
            BigDecimal total = somarDespesas(despesas);
            List<Map.Entry<String, BigDecimal>> maiores = topCategoriasDespesa(despesas, 3);

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
            }
        }

        if (dep.getGabinete() != null && dep.getGabinete().getTelefone() != null) {
            sb.append(String.format("\n\n• **Contato Institucional:** Gabinete %s, Prédio %s, Telefone %s%s.",
                    dep.getGabinete().getSala(),
                    dep.getGabinete().getPredio(),
                    dep.getGabinete().getTelefone(),
                    dep.getEmail() != null ? " | E-mail: " + dep.getEmail() : ""));
        }

        return sb.toString();
    }

    private boolean contemQualquerRadical(String textoNormalizado, List<String> termos) {
        if (textoNormalizado == null || textoNormalizado.isEmpty()) return false;
        for (String termo : termos) {
            // Usa radical (primeiros 5 caracteres se palavra longa) para pegar singular/plural (ex: imovel / imoveis, passagem / passagens)
            String radical = termo.length() >= 6 ? termo.substring(0, termo.length() - 1) : termo;
            if (textoNormalizado.contains(radical)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal somarDespesas(List<DespesaDTO> despesas) {
        BigDecimal total = BigDecimal.ZERO;
        for (DespesaDTO d : despesas) {
            if (d.getValorDocumento() != null) {
                total = total.add(d.getValorDocumento().abs());
            }
        }
        return total;
    }

    private List<Map.Entry<String, BigDecimal>> topCategoriasDespesa(List<DespesaDTO> despesas, int limite) {
        Map<String, BigDecimal> porCategoria = new HashMap<>();
        for (DespesaDTO d : despesas) {
            BigDecimal val = d.getValorDocumento() != null ? d.getValorDocumento().abs() : BigDecimal.ZERO;
            String cat = d.getTipoDespesa() != null ? d.getTipoDespesa() : "Outros";
            porCategoria.put(cat, porCategoria.getOrDefault(cat, BigDecimal.ZERO).add(val));
        }
        return porCategoria.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(limite)
                .toList();
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

        BigDecimal totalGastos = somarDespesas(despesas);
        Map<String, BigDecimal> gastosPorTipo = new HashMap<>();
        Map<String, BigDecimal> gastosPorFornecedor = new HashMap<>();

        for (DespesaDTO d : despesas) {
            BigDecimal valor = d.getValorDocumento() != null ? d.getValorDocumento().abs() : BigDecimal.ZERO;
            String tipo = d.getTipoDespesa() != null ? d.getTipoDespesa() : "OUTROS";
            gastosPorTipo.put(tipo, gastosPorTipo.getOrDefault(tipo, BigDecimal.ZERO).add(valor));

            String fornecedor = d.getNomeFornecedor() != null ? d.getNomeFornecedor() : "NÃO INFORMADO";
            if (d.getCnpjCpfFornecedor() != null && !d.getCnpjCpfFornecedor().isBlank()) {
                fornecedor = fornecedor + " (CNPJ/CPF: " + d.getCnpjCpfFornecedor() + ")";
            }
            gastosPorFornecedor.put(fornecedor, gastosPorFornecedor.getOrDefault(fornecedor, BigDecimal.ZERO).add(valor));
        }

        // Top 10 notas fiscais mais caras (maior gasto individual)
        List<Map<String, Object>> maioresNotasIndividuais = despesas.stream()
                .sorted((a, b) -> {
                    BigDecimal va = a.getValorDocumento() != null ? a.getValorDocumento().abs() : BigDecimal.ZERO;
                    BigDecimal vb = b.getValorDocumento() != null ? b.getValorDocumento().abs() : BigDecimal.ZERO;
                    return vb.compareTo(va);
                })
                .limit(10)
                .map(d -> {
                    Map<String, Object> nota = new LinkedHashMap<>();
                    nota.put("valor_reais", d.getValorDocumento() != null ? d.getValorDocumento().abs() : BigDecimal.ZERO);
                    nota.put("tipo_despesa", d.getTipoDespesa() != null ? d.getTipoDespesa() : "");
                    nota.put("fornecedor", d.getNomeFornecedor() != null ? d.getNomeFornecedor() : "");
                    nota.put("cnpj_cpf_fornecedor", d.getCnpjCpfFornecedor() != null ? d.getCnpjCpfFornecedor() : "");
                    nota.put("data_documento", d.getDataDocumento() != null ? d.getDataDocumento() : "");
                    nota.put("numero_documento", d.getNumDocumento() != null ? d.getNumDocumento() : "");
                    nota.put("url_comprovante", d.getUrlDocumento() != null ? d.getUrlDocumento() : "");
                    return nota;
                })
                .toList();

        // Top 10 fornecedores que mais receberam verba no acumulado
        List<Map<String, Object>> maioresFornecedores = gastosPorFornecedor.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .map(e -> Map.<String, Object>of(
                        "fornecedor", e.getKey(),
                        "total_recebido_reais", e.getValue()
                ))
                .toList();

        List<Map<String, Object>> notasRecentes = despesas.stream()
                .limit(10)
                .map(d -> Map.<String, Object>of(
                        "data", d.getDataDocumento() != null ? d.getDataDocumento() : "",
                        "tipo", d.getTipoDespesa() != null ? d.getTipoDespesa() : "",
                        "fornecedor", d.getNomeFornecedor() != null ? d.getNomeFornecedor() : "",
                        "valor", d.getValorDocumento() != null ? d.getValorDocumento().abs() : BigDecimal.ZERO
                ))
                .toList();

        Map<String, Object> blocoGastos = new LinkedHashMap<>();
        blocoGastos.put("total_geral_reais", totalGastos);
        blocoGastos.put("quantidade_notas_analisadas", despesas.size());
        blocoGastos.put("maiores_notas_fiscais_individuais_por_valor", maioresNotasIndividuais);
        blocoGastos.put("maiores_fornecedores_acumulado", maioresFornecedores);
        blocoGastos.put("totais_acumulados_por_categoria", gastosPorTipo);
        blocoGastos.put("ultimas_notas_cronologicas", notasRecentes);

        contexto.put("gastos_cota_parlamentar", blocoGastos);

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
