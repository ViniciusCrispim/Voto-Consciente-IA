package br.com.votoconsciente.deputado.repository;

import br.com.votoconsciente.deputado.entity.Deputado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeputadoRepository extends JpaRepository<Deputado, Long> {

    Optional<Deputado> findByCamaraId(Long camaraId);

    List<Deputado> findBySiglaUfIgnoreCase(String siglaUf);

    List<Deputado> findBySiglaPartidoIgnoreCase(String siglaPartido);

    List<Deputado> findBySiglaUfIgnoreCaseAndSiglaPartidoIgnoreCase(String siglaUf, String siglaPartido);

    @Query("SELECT d FROM Deputado d WHERE LOWER(d.nome) LIKE LOWER(CONCAT('%', :nome, '%')) " +
           "OR LOWER(d.nomeEleitoral) LIKE LOWER(CONCAT('%', :nome, '%'))")
    List<Deputado> buscarPorNome(@Param("nome") String nome);

    @Query("SELECT d FROM Deputado d WHERE d.idLegislatura = :legislatura")
    List<Deputado> findByLegislatura(@Param("legislatura") Integer legislatura);

    boolean existsByCamaraId(Long camaraId);
}
