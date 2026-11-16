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
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente para integração com a API do Google Gemini.
 * Prioriza os modelos mais recentes e rápidos (gemini-3.8-flash / gemini-flash-lite-latest),
 * memoriza o modelo que respondeu com sucesso e descarta automaticamente modelos depreciados (404).
 */
@Slf4j
@Component
public class GeminiApiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-3.8-flash}")
    private String configuredModel;

    private String cachedRegistryKey;
    private boolean registryChecked = false;
    private List<String> cachedAvailableModels = null;

    /** Guarda o último modelo que respondeu com sucesso para ser usado imediatamente nas próximas chamadas */
    private volatile String ultimoModeloComSucesso = null;

    /** Modelos que retornaram 404 (descontinuados) para nunca mais serem tentados */
    private final Set<String> modelosDescontinuados = ConcurrentHashMap.newKeySet();

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GeminiApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private synchronized String resolverApiKey() {
        if (apiKey != null && !apiKey.trim().isEmpty() && !apiKey.contains("SUA_CHAVE")) {
            return apiKey.trim();
        }
        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey.trim();
        }
        if (!registryChecked) {
            registryChecked = true;
            cachedRegistryKey = lerChaveDoRegistroWindows();
        }
        return cachedRegistryKey;
    }

    private String lerChaveDoRegistroWindows() {
        try {
            Process process = new ProcessBuilder("reg", "query", "HKCU\\Environment", "/v", "GEMINI_API_KEY")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("GEMINI_API_KEY") && line.contains("REG_SZ")) {
                        String[] parts = line.trim().split("\\s{2,}");
                        if (parts.length >= 3) {
                            String valor = parts[parts.length - 1].trim();
                            if (!valor.isEmpty()) {
                                log.info("Chave GEMINI_API_KEY carregada automaticamente do registro do usuário Windows.");
                                return valor;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Não foi possível consultar HKCU\\Environment: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Retorna a lista ordenada de modelos candidatos, colocando no topo:
     * 1. O último modelo que já funcionou com sucesso;
     * 2. Os modelos mais modernos e rápidos (gemini-3.8-flash, gemini-flash-lite-latest, gemini-3.7-flash);
     * Excluindo qualquer modelo que já tenha retornado 404.
     */
    private synchronized List<String> obterModelosCandidatos(String key) {
        Set<String> ordenados = new LinkedHashSet<>();

        // 1. Se já temos um modelo que funcionou antes, ele vai em primeiríssimo lugar!
        if (ultimoModeloComSucesso != null && !modelosDescontinuados.contains(ultimoModeloComSucesso)) {
            ordenados.add(ultimoModeloComSucesso);
        }

        // 2. Modelos prioritários atuais recomendados pelo Google AI Studio
        ordenados.add("gemini-3.8-flash");
        ordenados.add("gemini-flash-lite-latest");
        ordenados.add("gemini-3.7-flash");
        ordenados.add("gemini-3.6-flash");
        ordenados.add("gemini-3.5-flash");
        ordenados.add("gemini-3.5-flash-lite");

        if (configuredModel != null && !configuredModel.isBlank()) {
            ordenados.add(configuredModel.trim());
        }

        // 3. Complementa com a descoberta dinâmica da conta (se ainda não consultou)
        if (cachedAvailableModels == null) {
            cachedAvailableModels = descobrirModelosDaConta(key);
        }
        for (String m : cachedAvailableModels) {
            if (!m.contains("image") && !m.contains("transcribe") && !m.contains("robotics")
                    && !m.contains("computer-use") && !m.contains("2.5-flash")) {
                ordenados.add(m);
            }
        }

        // Remove qualquer modelo que já tenha dado 404
        ordenados.removeAll(modelosDescontinuados);
        return new ArrayList<>(ordenados);
    }

    private List<String> descobrirModelosDaConta(String key) {
        List<String> descobertos = new ArrayList<>();
        try {
            URI listModelsUri = URI.create(BASE_URL + "/models?key=" + key);
            String body = webClient.get()
                    .uri(listModelsUri)
                    .header("x-goog-api-key", key)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (body != null) {
                JsonNode root = objectMapper.readTree(body);
                JsonNode modelsNode = root.path("models");
                if (modelsNode.isArray()) {
                    for (JsonNode m : modelsNode) {
                        String name = m.path("name").asText("");
                        JsonNode methods = m.path("supportedGenerationMethods");
                        boolean supportsGenerate = false;
                        if (methods.isArray()) {
                            for (JsonNode method : methods) {
                                if ("generateContent".equals(method.asText())) {
                                    supportsGenerate = true;
                                    break;
                                }
                            }
                        }
                        if (supportsGenerate && name.contains("gemini")) {
                            String shortName = name.startsWith("models/") ? name.substring("models/".length()) : name;
                            descobertos.add(shortName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Não foi possível listar modelos dinamicamente: {}", e.getMessage());
        }
        return descobertos;
    }

    /**
     * Chama o Gemini com timeout curto por tentativa (20s), memorizando o modelo vencedor.
     */
    private ResultadoGemini chamarGeminiComFallbackDeModelo(String promptTexto, boolean jsonEstruturado, String key) {
        Map<String, Object> generationConfig = jsonEstruturado
                ? Map.of("temperature", 0.2, "responseMimeType", "application/json")
                : Map.of("temperature", 0.2);

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

        List<String> candidatos = obterModelosCandidatos(key);

        for (String modeloAtual : candidatos) {
            try {
                log.info("Consultando Google Gemini com o modelo '{}'...", modeloAtual);
                URI targetUri = URI.create(BASE_URL + "/models/" + modeloAtual + ":generateContent?key=" + key);

                String responseBody = webClient.post()
                        .uri(targetUri)
                        .header("x-goog-api-key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestJson)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(20))
                        .block();

                if (responseBody != null) {
                    JsonNode root = objectMapper.readTree(responseBody);
                    JsonNode candidates = root.path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
                        this.ultimoModeloComSucesso = modeloAtual;
                        log.info("Resposta gerada com sucesso pelo modelo Google Gemini '{}'!", modeloAtual);
                        return new ResultadoGemini(modeloAtual, text);
                    }
                }
            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                log.warn("Modelo '{}' retornou HTTP {}: {}", modeloAtual, e.getStatusCode(), e.getResponseBodyAsString());
                if (status == 404) {
                    // Descarta definitivamente modelos descontinuados para não perder tempo nas próximas chamadas
                    modelosDescontinuados.add(modeloAtual);
                } else if (status == 400 || status == 403) {
                    break;
                }
            } catch (Exception e) {
                log.warn("Tentativa com modelo '{}' excedeu tempo ou falhou ({}). Tentando próximo modelo...",
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
                    
                    DIRETRIZES OBRIGATÓRIAS DE NÃO-ALUCINAÇÃO (GUARDRAILS):
                    1. Responda DIRETAMENTE ao que o cidadão perguntou logo na primeira frase.
                    2. Baseie-se ESTRITAMENTE nos dados oficiais da Câmara dos Deputados fornecidos abaixo.
                    3. Se o cidadão perguntar sobre bens pessoais, imóveis, vida privada ou gastos que NÃO constam nas notas fiscais da Cota Parlamentar fornecidas, afirme explicitamente que NÃO HÁ REGISTRO desse item nos dados oficiais da Câmara (R$ 0,00 nas notas auditadas) e, em seguida, apresente quais são os gastos ou projetos reais registrados.
                    4. Cite números exatos (valores em R$, quantidade de notas fiscais, números de projetos de lei PL/PEC e votos nominais) sempre que estiverem presentes nos dados.
                    5. Nunca invente fatos, valores ou votações. Mantenha tom 100%% apartidário e jornalístico.
                    
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
                .modeloUtilizado("Motor Analítico Local Voto-Consciente (Auditoria Direta dos Dados Oficiais)")
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
                        "MODO DEMONSTRAÇÃO ATIVO: Verifique no console do Spring Boot se a chave GEMINI_API_KEY " +
                        "é uma chave válida do Google AI Studio."
                )
                .build();
    }

    private record ResultadoGemini(String modelo, String texto) {}
}
