package br.com.votoconsciente.ia.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Resposta estruturada da análise de inteligência artificial sobre o parlamentar.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Relatório de Análise Política gerado por IA com cruzamento de dados públicos")
public class AnaliseIaResponseDTO {

    @Schema(description = "Identificador do deputado na Câmara", example = "204379")
    private Long camaraId;

    @Schema(description = "Nome parlamentar", example = "Acácio Favacho")
    private String nome;

    @Schema(description = "Partido político", example = "MDB")
    private String partido;

    @Schema(description = "Estado (UF)", example = "AP")
    private String uf;

    @Schema(description = "Resumo executivo do perfil e postura parlamentar em linguagem acessível")
    private String resumoPerfil;

    @Schema(description = "Principais áreas temáticas de atuação legislativa identificadas nos projetos de lei")
    private List<String> principaisBandeiras;

    @Schema(description = "Diagnóstico qualitativo e quantitativo sobre o uso da cota parlamentar (CEAP)")
    private String analiseGastos;

    @Schema(description = "Avaliação de coerência entre os temas defendidos, projetos apresentados e votações nominais")
    private String coerenciaAtuacao;

    @Schema(description = "Destaques positivos na atuação parlamentar observados nos dados")
    private List<String> pontosFortes;

    @Schema(description = "Pontos de atenção, contradições ou áreas de alerta para o eleitor monitorar")
    private List<String> pontosAtencao;

    @Schema(description = "Score de atividade parlamentar calculado (0 a 100)", example = "85")
    private Integer scoreAtividade;

    @Schema(description = "Data e hora em que a análise foi gerada")
    private String dataAnalise;

    @Schema(description = "Modelo de IA utilizado para o processamento", example = "gemini-1.5-flash")
    private String modeloUtilizado;

    @Schema(description = "Indica se foi gerado via API real da LLM ou em modo demonstração local", example = "false")
    private boolean modoDemonstracao;

    @Schema(description = "Orientações sobre a metodologia e fonte dos dados")
    private String notaMetodologia;
}
