package br.com.votoconsciente.partido.repository;

import br.com.votoconsciente.partido.entity.Partido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PartidoRepository extends JpaRepository<Partido, Long> {

    Optional<Partido> findBySiglaIgnoreCase(String sigla);

    boolean existsBySigla(String sigla);
}
