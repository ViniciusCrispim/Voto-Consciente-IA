package br.com.votoconsciente.ia.controller;

import br.com.votoconsciente.ia.dto.AnaliseIaResponseDTO;
import br.com.votoconsciente.ia.dto.PerguntaRequestDTO;
import br.com.votoconsciente.ia.dto.PerguntaResponseDTO;
import br.com.votoconsciente.ia.service.AnaliseIaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/deputados")
@RequiredArgsConstructor
@Tag(name = "Inteligência Artificial", description = "Análise automatizada e Assistente Cívico Conversacional (RAG) sobre parlamentares")
public class AnaliseIaController {

    private final AnaliseIaService analiseIaService;

    @GetMapping("/{camaraId}/analise-ia")
    @Operation(
            summary = "Gerar análise completa do parlamentar com Inteligência Artificial",
            description = """
                    Cruza dados oficiais do deputado (projetos de lei, notas fiscais da cota parlamentar e votos nominais)
                    e submete a um modelo de IA (Google Gemini) para gerar um relatório analítico e imparcial.
                    """
    )
    public ResponseEntity<AnaliseIaResponseDTO> obterAnaliseIa(
            @Parameter(description = "ID do deputado na Câmara dos Deputados (ex: 220645 - Erika Hilton)", example = "220645", required = true)
            @PathVariable Long camaraId
    ) {
        return analiseIaService.analisarDeputado(camaraId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{camaraId}/perguntar")
    @Operation(
            summary = "Fazer uma pergunta em linguagem natural sobre o parlamentar (RAG + Roteamento Semântico)",
            description = """
                    Permite ao cidadão conversar e tirar dúvidas específicas sobre o deputado em linguagem natural.
                    
                    **Como funciona (RAG Direcionado):**
                    1. O **Roteador Semântico** analisa sua pergunta e identifica quais módulos da Câmara precisam ser acionados (`DESPESAS_CEAP`, `PROPOSICOES_LEGISLATIVAS`, `VOTACOES_PLENARIO`).
                    2. Os dados oficiais em tempo real são coletados e injetados no contexto.
                    3. A IA responde de forma fluida e natural, fundamentada estritamente nos dados públicos e citando as fontes consultadas.
                    """
    )
    public ResponseEntity<PerguntaResponseDTO> perguntarPost(
            @Parameter(description = "ID do deputado na Câmara dos Deputados (ex: 220645 - Erika Hilton)", example = "220645", required = true)
            @PathVariable Long camaraId,
            @RequestBody PerguntaRequestDTO request
    ) {
        String pergunta = request != null && request.getPergunta() != null
                ? request.getPergunta()
                : "Qual é o resumo de atuação e gastos deste parlamentar?";

        return analiseIaService.responderPergunta(camaraId, pergunta)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{camaraId}/perguntar")
    @Operation(
            summary = "Fazer uma pergunta rápida via URL (GET) sobre o parlamentar",
            description = "Versão GET para facilitar testes rápidos direto no navegador ou no Swagger informando a pergunta como parâmetro."
    )
    public ResponseEntity<PerguntaResponseDTO> perguntarGet(
            @Parameter(description = "ID do deputado na Câmara dos Deputados (ex: 220645 - Erika Hilton)", example = "220645", required = true)
            @PathVariable Long camaraId,
            @Parameter(
                    description = "Sua pergunta em linguagem natural",
                    example = "Quanto a Erika Hilton gastou na cota parlamentar e quais são seus principais projetos?",
                    required = true
            )
            @RequestParam String pergunta
    ) {
        return analiseIaService.responderPergunta(camaraId, pergunta)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
