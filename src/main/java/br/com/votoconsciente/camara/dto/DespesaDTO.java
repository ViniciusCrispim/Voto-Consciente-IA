package br.com.votoconsciente.camara.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO para despesas com cota parlamentar.
 * Endpoint: GET /deputados/{id}/despesas
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DespesaDTO {

    private Integer ano;
    private Integer mes;
    private String tipoDespesa;
    private String codDocumento;
    private String tipoDocumento;
    private String codTipoDocumento;
    private String dataDocumento;
    private String numDocumento;
    private BigDecimal valorDocumento;
    private String urlDocumento;
    private String nomeFornecedor;
    private String cnpjCpfFornecedor;
    private BigDecimal valorLiquido;
    private BigDecimal valorGlosa;
    private Integer numRessarcimento;
    private Long codLote;
    private Integer parcela;
}
