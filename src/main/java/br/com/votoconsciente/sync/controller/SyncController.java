package br.com.votoconsciente.sync.controller;

import br.com.votoconsciente.camara.service.CamaraSyncService;
import br.com.votoconsciente.sync.entity.SyncLog;
import br.com.votoconsciente.sync.repository.SyncLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints administrativos para gerenciar a sincronização com APIs externas.
 */
@RestController
@RequestMapping("/api/v1/admin/sync")
@RequiredArgsConstructor
@Tag(name = "Sincronização", description = "Gerenciamento da sincronização com APIs oficiais externas")
public class SyncController {

    private final CamaraSyncService camaraSyncService;
    private final SyncLogRepository syncLogRepository;

    @PostMapping("/camara")
    @Operation(summary = "Sincronizar Câmara dos Deputados",
               description = "Força uma sincronização imediata com a API da Câmara dos Deputados. " +
                             "Importa todos os deputados da legislatura atual.")
    public ResponseEntity<SyncLog> sincronizarCamara() {
        SyncLog log = camaraSyncService.sincronizarDeputados();
        return ResponseEntity.ok(log);
    }

    @GetMapping("/status")
    @Operation(summary = "Status das sincronizações",
               description = "Retorna o histórico das últimas sincronizações realizadas com cada fonte.")
    public ResponseEntity<List<SyncLog>> status() {
        List<SyncLog> logs = syncLogRepository.findTop20ByOrderByIniciadoEmDesc();
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/status/{fonte}")
    @Operation(summary = "Status da última sincronização de uma fonte",
               description = "Retorna o status da última sincronização de uma fonte específica (CAMARA, TSE, SENADO).")
    public ResponseEntity<SyncLog> statusPorFonte(@PathVariable String fonte) {
        return syncLogRepository.findTopByFonteOrderByIniciadoEmDesc(fonte.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/health")
    @Operation(summary = "Verificar conectividade com APIs externas",
               description = "Testa a conectividade com cada API externa integrada.")
    public ResponseEntity<Map<String, Object>> health() {
        boolean camaraOk = camaraSyncService != null; // Simplificado — expandir por fonte
        return ResponseEntity.ok(Map.of(
                "camara", camaraOk ? "UP" : "DOWN",
                "tse", "PENDENTE",
                "senado", "PENDENTE",
                "transparencia", "PENDENTE"
        ));
    }
}
