package br.com.votoconsciente.sync.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registra o histórico de sincronizações com as APIs externas.
 */
@Entity
@Table(name = "sync_logs")
@Getter
@Setter
@NoArgsConstructor
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nome da fonte sincronizada (CAMARA, TSE, SENADO, TRANSPARENCIA) */
    @Column(nullable = false)
    private String fonte;

    @Column(nullable = false)
    private LocalDateTime iniciadoEm;

    private LocalDateTime finalizadoEm;

    /** Quantidade de registros importados/atualizados */
    private Integer registrosProcessados;

    /** Status final: SUCESSO, ERRO, EM_ANDAMENTO */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusSync status;

    /** Mensagem de erro em caso de falha */
    @Column(length = 2000)
    private String mensagemErro;

    public SyncLog(String fonte) {
        this.fonte = fonte;
        this.iniciadoEm = LocalDateTime.now();
        this.status = StatusSync.EM_ANDAMENTO;
        this.registrosProcessados = 0;
    }

    public void finalizar(int registros) {
        this.finalizadoEm = LocalDateTime.now();
        this.status = StatusSync.SUCESSO;
        this.registrosProcessados = registros;
    }

    public void falhar(String mensagem) {
        this.finalizadoEm = LocalDateTime.now();
        this.status = StatusSync.ERRO;
        this.mensagemErro = mensagem;
    }

    public enum StatusSync {
        EM_ANDAMENTO, SUCESSO, ERRO
    }
}
