package br.com.votoconsciente.deputado.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Representa um Deputado Federal com dados vindos da API da Câmara dos Deputados.
 * Os dados são sincronizados periodicamente em background.
 */
@Entity
@Table(name = "deputados", indexes = {
        @Index(name = "idx_deputado_camara_id", columnList = "camaraId"),
        @Index(name = "idx_deputado_nome", columnList = "nome"),
        @Index(name = "idx_deputado_uf", columnList = "siglaUf"),
        @Index(name = "idx_deputado_partido", columnList = "siglaPartido")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deputado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID original na API da Câmara dos Deputados */
    @Column(unique = true, nullable = false)
    private Long camaraId;

    @Column(nullable = false)
    private String nome;

    private String nomeCivil;
    private String nomeEleitoral;
    private String siglaPartido;
    private String siglaUf;
    private String email;
    private String urlFoto;
    private String urlWebsite;
    private String cpf;
    private String sexo;
    private String dataNascimento;
    private String escolaridade;
    private String municipioNascimento;
    private String ufNascimento;
    private String situacao;
    private String condicaoEleitoral;
    private Integer idLegislatura;

    // Gabinete
    private String gabineteNome;
    private String gabinetePredio;
    private String gabineteSala;
    private String gabineteAndar;
    private String gabineteTelefone;

    /** Data/hora da última sincronização com a API da Câmara */
    private LocalDateTime ultimaAtualizacao;

    /** Indica se os dados detalhados já foram carregados */
    @Column(nullable = false)
    @Builder.Default
    private boolean detalhesCarregados = false;

    public boolean estaDesatualizado(int ttlHoras) {
        if (ultimaAtualizacao == null) return true;
        return ultimaAtualizacao.isBefore(LocalDateTime.now().minusHours(ttlHoras));
    }
}
