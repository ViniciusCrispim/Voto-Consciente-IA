package br.com.votoconsciente.sync.repository;

import br.com.votoconsciente.sync.entity.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    /** Busca o último sync de uma fonte específica */
    Optional<SyncLog> findTopByFonteOrderByIniciadoEmDesc(String fonte);

    /** Lista os últimos N syncs de todas as fontes */
    List<SyncLog> findTop20ByOrderByIniciadoEmDesc();
}
