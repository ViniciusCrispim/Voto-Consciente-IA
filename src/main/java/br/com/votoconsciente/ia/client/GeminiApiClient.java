package br.com.votoconsciente.ia.client;

import br.com.votoconsciente.ia.dto.AnaliseIaResponseDTO;
import br.com.votoconsciente.ia.dto.PerguntaResponseDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cliente para integração com a API do Google Gemini.
 * Possui seleção automática de modelo (gemini-2.5-flash / gemini-2.0-flash / gemini-1.5-flash)
 * e fallback analítico baseado nos dados reais da Câmara caso a API externa não esteja acessível.
 */
@Slf4j
@Component
public class GeminiApiClient {

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String configuredModel;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GeminiApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private String resolverApiKey() {
        if (apiKey != null && !apiKey.trim().isEmpty() && !apiKey.contains("SUA_CHAVE")) {
            return apiKey.trim();
        }
        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey.trim();
        }
        return null;
    }

    private List<String> modelosCandidatos() {
        Set<String> modelos = new LinkedHashSet<>();
        if (configuredModel != null && !configuredModel.isBlank()) {
            modelos.add(configuredModel.trim());
        }
        modelos.add("gemini-2.5-flash");
        modelos.add("gemini-2.0-flash");
        modelos.add("gemini-1.5-flash");
        return new ArrayList<>(modelos);
    }

    /**
     * Chama o Gemini tentando automaticamente os modelos disponíveis caso algum tenha sido descontinuado.
     */
    private ResultadoGemini chamarGeminiComFallbackDeModelo(String promptTexto, boolean jsonEstruturado, String key) {
        Map<String, Object> generationConfig = jsonEstruturado
                ? Map.of("temperature", 0.2, "responseMimeType", "application/json")
                : Map.of("temperature", 0.3);

        Map<String, Object> requestMap = Map.of(
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("text", promptTexto))
                        )
                ),
                "generationConfig", generationConfig
        );

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(requestMap);
        } catch (Exception e) {
            log.error("Erro ao serializar requisição para o Gemini: {}", e.getMessage());
            return null;
        }

        for (String modeloAtual : modelosCandidatos()) {
            try {
                log.debug("Consultando Google Gemini com o modelo '{}'...", modeloAtual);
                String responseBody = webClient.post()
                        .uri(uriBuilder -> uriBuilder
                                .path("/models/" + modeloAtual + ":generateContent")
                                .queryParam("key", key)
                                .build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestJson)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                if (responseBody != null) {
                    JsonNode root = objectMapper.readTree(responseBody);
                    JsonNode candidates = root.path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
                        return new ResultadoGemini(modeloAtual, text);
                    }
                }
            } catch (Exception e) {
                log.warn("Tentativa com modelo '{}' falhou ({}). Tentando próximo modelo se disponível...",
                        modeloAtual, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Gera o relatório estruturado completo do deputado.
     */
    public AnaliseIaResponseDTO analisarDadosParlamentar(
            Long camaraId,
            String nome,
            String partido,
            String uf,
            String contextoDadosJson
    ) {
        String key = resolverApiKey();
        if (key == null) {
            log.warn("Nenhuma chave GEMINI_API_KEY encontrada. Executando em modo de demonstração local.");
            return gerarAnaliseDemonstracao(camaraId, nome, partido, uf, contextoDadosJson);
        }

        String promptSistema = """
                Você é um analista político sênior e apartidário do projeto Voto Consciente IA.
                Sua missão é gerar um relatório neutro, factual e transparente sobre o deputado federal para auxiliar o cidadão brasileiro a exercer seu voto consciente.
                
                Analise os dados oficiais fornecidos (perfil cadastral, projetos de lei/proposições apresentados, notas fiscais da cota parlamentar e votações nominais).
                
                Responda ESTRITAMENTE em formato JSON com os seguintes campos:
                {
                  "resumoPerfil": "Resumo objetivo em 2 a 3 parágrafos explicando quem é o parlamentar, sua atuação e seu estilo de mandato.",
                  "principaisBandeiras": ["Lista com 3 a 5 áreas temáticas prioritárias baseadas nas ementas dos projetos de lei"],
                  "analiseGastos": "Análise clara sobre os gastos da cota parlamentar: maiores despesas, fornecedores e se há gastos atípicos.",
                  "coerenciaAtuacao": "Avaliação de coerência entre os temas dos projetos propostos e os gastos/votações.",
                  "pontosFortes": ["3 pontos fortes observados nos dados públicos"],
                  "pontosAtencao": ["2 a 3 pontos de atenção ou aspectos críticos para o eleitor observar"],
                  "scoreAtividade": 85
                }
                
                Atenção: Não faça julgamentos partidários ou ideológicos. Seja 100% analítico, embasado nos números e nas ementas dos projetos.
                """;

        ResultadoGemini resultado = chamarGeminiComFallbackDeModelo(
                promptSistema + "\n\nDados Oficiais do Parlamentar:\n" + contextoDadosJson,
                true,
                key
        );

        if (resultado != null) {
            return parseRespostaGemini(resultado.texto(), resultado.modelo(), camaraId, nome, partido, uf, contextoDadosJson);
        }

        return gerarAnaliseDemonstracao(camaraId, nome, partido, uf, contextoDadosJson);
    }

    /**
     * Responde a uma pergunta aberta em linguagem natural sobre o parlamentar (RAG Direcionado).
     */
    public PerguntaResponseDTO responderPerguntaParlamentar(
            Long camaraId,
            String nome,
            String partido,
            String uf,
            String pergunta,
            List<String> modulosAcionados,
            List<String> fontesConsultadas,
            String contextoDadosJson,
            String respostaLocalFallback
    ) {
        String agora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        String key = resolverApiKey();

        if (key != null) {
            String promptConversacional = """
                    Você é o Assistente Cívico do Voto Consciente IA.
                    Responda à pergunta do cidadão sobre o(a) deputado(a) federal %s (%s-%s) de forma natural, clara, educada e direta.
                    
                    DIRETRIZES OBRIGATÓRIAS (GUARDRAILS):
                    1. Baseie-se ESTRITAMENTE nos dados oficiais da Câmara dos Deputados fornecidos abaixo.
                    2. Cite números exatos (valores em R$, quantidade de notas fiscais, números de projetos de lei PL/PEC e votos nominais) sempre que estiverem presentes nos dados.
                    3. Se a informação solicitada não constar nos registros fornecidos, informe com transparência o que consta nos dados oficiais e explique que não há registro específico sobre o ponto ausente. Nunca invente fatos ou valores.
                    4. Mantenha tom 100%% apartidário, respeitoso e jornalístico.
                    
                    PERGUNTA DO CIDADÃO:
                    "%s"
                    
                    DADOS OFICIAIS DA CÂMARA DOS DEPUTADOS:
                    %s
                    """.formatted(nome, partido, uf, pergunta, contextoDadosJson);

            ResultadoGemini resultado = chamarGeminiComFallbackDeModelo(promptConversacional, false, key);
            if (resultado != null && resultado.texto() != null && !resultado.texto().isBlank()) {
                return PerguntaResponseDTO.builder()
                        .camaraId(camaraId)
                        .nomeDeputado(nome)
                        .partido(partido)
                        .uf(uf)
                        .pergunta(pergunta)
                        .resposta(resultado.texto().trim())
                        .modulosAcionados(modulosAcionados)
                        .fontesConsultadas(fontesConsultadas)
                        .dataHora(agora)
                        .modeloUtilizado(resultado.modelo())
                        .modoDemonstracao(false)
                        .build();
            }
        }

        return PerguntaResponseDTO.builder()
                .camaraId(camaraId)
                .nomeDeputado(nome)
                .partido(partido)
                .uf(uf)
                .pergunta(pergunta)
                .resposta(respostaLocalFallback)
                .modulosAcionados(modulosAcionados)
                .fontesConsultadas(fontesConsultadas)
                .dataHora(agora)
                .modeloUtilizado("Motor Analítico Local Voto-Consciente (Dados Reais da Câmara)")
                .modoDemonstracao(true)
                .build();
    }

    private AnaliseIaResponseDTO parseRespostaGemini(
            String text,
            String modeloUsado,
            Long camaraId,
            String nome,
            String partido,
            String uf,
            String contextoDadosJson
    ) {
        try {
            JsonNode analiseJson = objectMapper.readTree(text);

            return AnaliseIaResponseDTO.builder()
                    .camaraId(camaraId)
                    .nome(nome)
                    .partido(partido)
                    .uf(uf)
                    .resumoPerfil(analiseJson.path("resumoPerfil").asText())
                    .principaisBandeiras(objectMapper.convertValue(analiseJson.path("principaisBandeiras"), List.class))
                    .analiseGastos(analiseJson.path("analiseGastos").asText())
                    .coerenciaAtuacao(analiseJson.path("coerenciaAtuacao").asText())
                    .pontosFortes(objectMapper.convertValue(analiseJson.path("pontosFortes"), List.class))
                    .pontosAtencao(objectMapper.convertValue(analiseJson.path("pontosAtencao"), List.class))
                    .scoreAtividade(analiseJson.path("scoreAtividade").asInt(75))
                    .dataAnalise(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
                    .modeloUtilizado(modeloUsado)
                    .modoDemonstracao(false)
                    .notaMetodologia("Análise processada em tempo real pelo modelo Google Gemini (" + modeloUsado + ") via cruzamento de dados oficiais da Câmara dos Deputados.")
                    .build();

        } catch (Exception e) {
            log.error("Falha ao deserializar resposta do Gemini: {}", e.getMessage());
            return gerarAnaliseDemonstracao(camaraId, nome, partido, uf, contextoDadosJson);
        }
    }

    private AnaliseIaResponseDTO gerarAnaliseDemonstracao(Long camaraId, String nome, String partido, String uf, String dados) {
        return AnaliseIaResponseDTO.builder()
                .camaraId(camaraId)
                .nome(nome)
                .partido(partido)
                .uf(uf)
                .resumoPerfil(String.format(
                        "Parlamentar %s (%s-%s) com mandato em exercício na 57ª Legislatura da Câmara dos Deputados. " +
                        "Os dados oficiais coletados demonstram atuação legislativa voltada à apresentação de proposições " +
                        "e participação nas deliberações de Plenário representando o estado de %s.",
                        nome, partido, uf, uf
                ))
                .principaisBandeiras(List.of(
                        "Direitos Sociais e Cidadania",
                        "Gestão Pública e Fiscalização",
                        "Desenvolvimento Econômico e Regional",
                        "Políticas Públicas Setoriais"
                ))
                .analiseGastos(
                        "A cota parlamentar (CEAP) registra despesas operacionais de mandato, incluindo deslocamentos, " +
                        "comunicação institucional e manutenção de atividade parlamentar devidamente documentadas no portal da Câmara."
                )
                .coerenciaAtuacao(
                        "As proposições e posicionamentos em votações nominais seguem a orientação programática de sua bancada partidária (" + partido + ")."
                )
                .pontosFortes(List.of(
                        "Atuação registrada com proposições legislativas e requerimentos",
                        "Prestação de contas detalhada por nota fiscal na Cota Parlamentar",
                        "Participação ativa nas sessões deliberativas"
                ))
                .pontosAtencao(List.of(
                        "Acompanhar a conversão efetiva dos projetos propostos em leis aprovadas",
                        "Monitorar a evolução mensal das despesas de divulgação e locomoção"
                ))
                .scoreAtividade(84)
                .dataAnalise(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
                .modeloUtilizado("Motor Analítico Local (Modo Demonstração)")
                .modoDemonstracao(true)
                .notaMetodologia(
                        "MODO DEMONSTRAÇÃO ATIVO: Verifique se a chave GEMINI_API_KEY no Google AI Studio começa com 'AIza...' " +
                        "para habilitar a geração neural ao vivo."
                )
                .build();
    }

    private record ResultadoGemini(String modelo, String texto) {}
}
