package br.com.votoconsciente.ia.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO de saída contendo a resposta em linguagem natural gerada pela IA com base nos dados oficiais.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resposta do Assistente Cívico de IA fundamentada nos dados oficiais da Câmara")
public class PerguntaResponseDTO {

    @Schema(description = "ID do deputado na Câmara", example = "220645")
    private Long camaraId;

    @Schema(description = "Nome do parlamentar", example = "Erika Hilton")
    private String nomeDeputado;

    @Schema(description = "Partido político", example = "PSOL")
    private String partido;

    @Schema(description = "Estado (UF)", example = "SP")
    private String uf;

    @Schema(description = "Pergunta original enviada pelo usuário")
    private String pergunta;

    @Schema(description = "Resposta em linguagem natural, clara e fundamentada nos dados oficiais")
    private String resposta;

    @Schema(description = "Módulos acionados pelo roteador semântico (ex: PERFIL, DESPESAS, PROPOSICOES, VOTACOES)")
    private List<String> modulosAcionados;

    @Schema(description = "Fontes e registros oficiais utilizados para fundamentar a resposta")
    private List<String> fontesConsultadas;

    @Schema(description = "Data e hora da consulta")
    private String dataHora;

    @Schema(description = "Modelo de IA utilizado", example = "gemini-2.5-flash")
    private String modeloUtilizado;

    @Schema(description = "Indica se a resposta foi gerada pela LLM ou pelo motor analítico local de contingência")
    private boolean modoDemonstracao;
}
