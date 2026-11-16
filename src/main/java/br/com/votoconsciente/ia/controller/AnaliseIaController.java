package br.com.votoconsciente.ia.controller;

import br.com.votoconsciente.ia.dto.AnaliseIaResponseDTO;
import br.com.votoconsciente.ia.service.AnaliseIaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deputados")
@RequiredArgsConstructor
@Tag(name = "Inteligência Artificial", description = "Análise automatizada de coerência, perfil e gastos parlamentares com IA")
public class AnaliseIaController {

    private final AnaliseIaService analiseIaService;

    @GetMapping("/{camaraId}/analise-ia")
    @Operation(
            summary = "Gerar análise do parlamentar com Inteligência Artificial",
            description = """
                    Cruza dados oficiais do deputado (projetos de lei, notas fiscais da cota parlamentar e votos nominais)
                    e submete a um modelo de IA (Google Gemini) para gerar um relatório analítico e imparcial.
                    
                    **Campos gerados pela IA:**
                    - Resumo do perfil em linguagem simples
                    - Principais bandeiras legislativas (temas de maior atuação)
                    - Diagnóstico sobre o padrão de gastos com a cota parlamentar
                    - Análise de coerência entre discursos, projetos e votações
                    - Pontos fortes e pontos de atenção para o eleitor
                    - Score geral de atividade parlamentar (0 a 100)
                    """
    )
    public ResponseEntity<AnaliseIaResponseDTO> obterAnaliseIa(
            @Parameter(description = "ID do deputado na Câmara dos Deputados (ex: 220645 - Erika Hilton)", required = true)
            @PathVariable Long camaraId
    ) {
        return analiseIaService.analisarDeputado(camaraId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
