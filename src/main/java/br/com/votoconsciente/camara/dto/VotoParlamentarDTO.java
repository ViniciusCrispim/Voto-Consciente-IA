package br.com.votoconsciente.camara.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa o posicionamento de um deputado em uma votação específica.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VotoParlamentarDTO {

    private String idVotacao;
    private String dataHora;
    private String tituloVotacao;
    private String tipoVoto; // Ex: "Sim", "Não", "Abstenção", "Obstrução"
    private Integer aprovacaoGeral; // 1 = aprovado, 0 = rejeitado
    private String siglaOrgao;
}
