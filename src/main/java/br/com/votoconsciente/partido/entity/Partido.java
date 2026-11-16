package br.com.votoconsciente.partido.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Representa um partido político brasileiro.
 */
@Entity
@Table(name = "partidos", indexes = {
        @Index(name = "idx_partido_sigla", columnList = "sigla")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Partido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(unique = true, nullable = false)
    private String sigla;

    private Integer numero;

    private String urlLogo;
    private String urlWebsite;
    private String facebook;
}
