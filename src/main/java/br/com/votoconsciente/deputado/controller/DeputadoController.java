package br.com.votoconsciente.deputado.controller;

import br.com.votoconsciente.camara.dto.DespesaDTO;
import br.com.votoconsciente.camara.dto.ProposicaoDTO;
import br.com.votoconsciente.camara.dto.VotoParlamentarDTO;
import br.com.votoconsciente.deputado.dto.DeputadoResponseDTO;
import br.com.votoconsciente.deputado.service.DeputadoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/deputados")
@RequiredArgsConstructor
@Tag(name = "Deputados", description = "Consulta de Deputados Federais com dados da API da Câmara dos Deputados")
public class DeputadoController {

    private final DeputadoService deputadoService;

    @GetMapping
    @Operation(summary = "Listar deputados",
               description = "Retorna a lista de deputados federais. Filtre por UF e/ou partido.")
    public ResponseEntity<List<DeputadoResponseDTO>> listar(
            @Parameter(description = "Sigla do estado (ex: SP, RJ, MG)")
            @RequestParam(required = false) String uf,

            @Parameter(description = "Sigla do partido (ex: PT, PL, MDB)")
            @RequestParam(required = false) String partido
    ) {
        List<DeputadoResponseDTO> resultado;

        if (uf != null && partido != null) {
            resultado = deputadoService.listarPorUf(uf).stream()
                    .filter(d -> partido.equalsIgnoreCase(d.getSiglaPartido()))
                    .toList();
        } else if (uf != null) {
            resultado = deputadoService.listarPorUf(uf);
        } else if (partido != null) {
            resultado = deputadoService.listarPorPartido(partido);
        } else {
            resultado = deputadoService.listarTodos();
        }

        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/buscar")
    @Operation(summary = "Buscar deputado por nome",
               description = "Busca deputados cujo nome ou nome eleitoral contenha o termo informado.")
    public ResponseEntity<List<DeputadoResponseDTO>> buscarPorNome(
            @Parameter(description = "Nome ou parte do nome do deputado", required = true)
            @RequestParam String nome
    ) {
        return ResponseEntity.ok(deputadoService.buscarPorNome(nome));
    }

    @GetMapping("/{camaraId}")
    @Operation(summary = "Detalhe de um deputado",
               description = "Retorna o perfil completo de um deputado pelo ID da Câmara. " +
                             "Se os dados estiverem desatualizados, são atualizados em background.")
    public ResponseEntity<DeputadoResponseDTO> buscarPorId(
            @Parameter(description = "ID do deputado na API da Câmara dos Deputados", required = true)
            @PathVariable Long camaraId
    ) {
        return deputadoService.buscarPorCamaraId(camaraId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{camaraId}/despesas")
    @Operation(summary = "Despesas com cota parlamentar",
               description = "Retorna as despesas do deputado com a cota parlamentar (CEAP). " +
                             "Pode filtrar por ano e mês.")
    public ResponseEntity<List<DespesaDTO>> buscarDespesas(
            @PathVariable Long camaraId,

            @Parameter(description = "Ano de referência (ex: 2024)")
            @RequestParam(required = false) Integer ano,

            @Parameter(description = "Mês de referência (1-12)")
            @RequestParam(required = false) Integer mes
    ) {
        return ResponseEntity.ok(deputadoService.buscarDespesas(camaraId, ano, mes));
    }

    @GetMapping("/{camaraId}/votacoes")
    @Operation(summary = "Histórico de votações nominais",
               description = "Retorna como o deputado votou (Sim/Não/Abstenção/Obstrução) nas votações nominais recentes.")
    public ResponseEntity<List<VotoParlamentarDTO>> buscarVotacoes(
            @PathVariable Long camaraId
    ) {
        return ResponseEntity.ok(deputadoService.buscarVotacoes(camaraId));
    }

    @GetMapping("/{camaraId}/proposicoes")
    @Operation(summary = "Proposições de autoria",
               description = "Retorna os projetos de lei e outras proposições de autoria do deputado.")
    public ResponseEntity<List<ProposicaoDTO>> buscarProposicoes(
            @PathVariable Long camaraId,

            @Parameter(description = "Filtrar por ano de apresentação")
            @RequestParam(required = false) Integer ano
    ) {
        return ResponseEntity.ok(deputadoService.buscarProposicoes(camaraId, ano));
    }
}
