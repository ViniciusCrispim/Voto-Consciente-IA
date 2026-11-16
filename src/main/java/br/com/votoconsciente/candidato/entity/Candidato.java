package br.com.votoconsciente.candidato.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Representa um candidato nas eleições 2026.
 * Dados sincronizados a partir do TSE DivulgaCand.
 */
@Entity
@Table(name = "candidatos", indexes = {
        @Index(name = "idx_candidato_nome", columnList = "nome"),
        @Index(name = "idx_candidato_uf", columnList = "siglaUf"),
        @Index(name = "idx_candidato_partido", columnList = "siglaPartido"),
        @Index(name = "idx_candidato_cargo", columnList = "cargo")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID do candidato no sistema TSE */
    private String tseId;

    @Column(nullable = false)
    private String nome;

    private String nomeUrna;
    private String email;
    private String siglaPartido;
    private String nomePartido;
    private String siglaUf;
    private String municipio;
    private String urlFoto;
    private String cargo;
    private String descricaoSituacao;
    private String cpf;
    private String dataNascimento;
    private String grauInstrucao;
    private String genero;
    private String cor;
    private String ocupacao;
    private Integer numeroEleitoral;
    private Integer anoEleicao;

    private LocalDateTime ultimaAtualizacao;
}
