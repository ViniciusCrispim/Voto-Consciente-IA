package br.com.votoconsciente.ia.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de entrada para realizar uma pergunta em linguagem natural sobre um deputado.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = " Objeto contendo a pergunta do cidadão sobre o deputado federal")
public class PerguntaRequestDTO {

    @Schema(
            description = "Pergunta em linguagem natural sobre a atuação, gastos, projetos de lei ou votações do parlamentar",
            example = "Quanto essa parlamentar gastou na cota parlamentar recentemente e quais são seus principais projetos de lei?"
    )
    private String pergunta;
}
