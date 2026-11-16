package br.com.votoconsciente.camara.service;

import br.com.votoconsciente.camara.client.CamaraApiClient;
import br.com.votoconsciente.camara.dto.DeputadoDetalheDTO;
import br.com.votoconsciente.camara.dto.DeputadoResumoDTO;
import br.com.votoconsciente.deputado.entity.Deputado;
import br.com.votoconsciente.deputado.repository.DeputadoRepository;
import br.com.votoconsciente.sync.entity.SyncLog;
import br.com.votoconsciente.sync.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Serviço responsável por sincronizar os dados da Câmara dos Deputados
 * com o banco de dados local.
 *
 * Executa um sync completo toda noite à meia-noite.
 * Pode ser disparado manualmente via endpoint admin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CamaraSyncService {

    private final CamaraApiClient camaraApiClient;
    private final DeputadoRepository deputadoRepository;
    private final SyncLogRepository syncLogRepository;

    @Value("${sync.legislatura-atual:57}")
    private Integer legislaturaAtual;

    private static final String FONTE = "CAMARA";
    private static final int ITENS_POR_PAGINA = 100;

    /**
     * Sincronização agendada: todo dia à meia-noite.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void sincronizarAgendado() {
        log.info("Iniciando sincronização agendada da Câmara dos Deputados...");
        sincronizarDeputados();
    }

    /**
     * Realiza a sincronização completa da lista de deputados.
     * Pode ser chamado manualmente ou pelo agendador.
     */
    @Transactional
    public SyncLog sincronizarDeputados() {
        SyncLog syncLog = new SyncLog(FONTE);
        syncLogRepository.save(syncLog);

        int totalProcessados = 0;
        int pagina = 1;

        try {
            while (true) {
                List<DeputadoResumoDTO> pagina_dados = camaraApiClient.listarDeputados(
                        legislaturaAtual, pagina, ITENS_POR_PAGINA);

                if (pagina_dados.isEmpty()) {
                    break;
                }

                for (DeputadoResumoDTO dto : pagina_dados) {
                    salvarOuAtualizarDeputado(dto);
                    totalProcessados++;
                }

                log.debug("Página {} sincronizada: {} deputados", pagina, pagina_dados.size());

                if (pagina_dados.size() < ITENS_POR_PAGINA) {
                    break; // Última página
                }
                pagina++;
            }

            syncLog.finalizar(totalProcessados);
            log.info("Sincronização da Câmara concluída: {} deputados processados", totalProcessados);

        } catch (Exception e) {
            log.error("Erro durante sincronização da Câmara: {}", e.getMessage(), e);
            syncLog.falhar(e.getMessage());
        }

        return syncLogRepository.save(syncLog);
    }

    /**
     * Sincroniza e salva os detalhes de um deputado imediatamente (síncrono).
     * Usado quando o parlamentar ainda não existe no banco e uma consulta pontual é solicitada.
     */
    @Transactional
    public Deputado sincronizarOuAtualizarDetalheDeputado(Long camaraId) {
        log.info("Buscando e persistindo deputado {} diretamente da API da Câmara...", camaraId);
        try {
            DeputadoDetalheDTO detalhe = camaraApiClient.buscarDeputadoPorId(camaraId);
            if (detalhe == null) {
                log.warn("Deputado {} não encontrado na API da Câmara", camaraId);
                return null;
            }

            Deputado dep = deputadoRepository.findByCamaraId(camaraId)
                    .orElse(new Deputado());

            dep.setCamaraId(camaraId);
            if (detalhe.getUltimoStatus() != null) {
                var status = detalhe.getUltimoStatus();
                dep.setNome(status.getNome());
                dep.setSiglaPartido(status.getSiglaPartido());
                dep.setSiglaUf(status.getSiglaUf());
                dep.setEmail(status.getEmail());
                dep.setUrlFoto(status.getUrlFoto());
                dep.setIdLegislatura(status.getIdLegislatura());
            }

            enrichDeputadoComDetalhes(dep, detalhe);
            dep.setUltimaAtualizacao(LocalDateTime.now());
            dep.setDetalhesCarregados(true);

            return deputadoRepository.save(dep);
        } catch (Exception e) {
            log.error("Erro ao sincronizar deputado {}: {}", camaraId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Atualiza os detalhes de um deputado específico de forma assíncrona em background.
     */
    @Async
    @Transactional
    public void atualizarDetalheDeputadoAsync(Long camaraId) {
        sincronizarOuAtualizarDetalheDeputado(camaraId);
    }

    private void salvarOuAtualizarDeputado(DeputadoResumoDTO dto) {
        Deputado deputado = deputadoRepository.findByCamaraId(dto.getId())
                .orElse(new Deputado());

        deputado.setCamaraId(dto.getId());
        deputado.setNome(dto.getNome());
        deputado.setSiglaPartido(dto.getSiglaPartido());
        deputado.setSiglaUf(dto.getSiglaUf());
        deputado.setEmail(dto.getEmail());
        deputado.setUrlFoto(dto.getUrlFoto());
        deputado.setIdLegislatura(dto.getIdLegislatura());
        deputado.setUltimaAtualizacao(LocalDateTime.now());

        deputadoRepository.save(deputado);
    }

    private void enrichDeputadoComDetalhes(Deputado dep, DeputadoDetalheDTO dto) {
        dep.setNomeCivil(dto.getNomeCivil());
        dep.setCpf(dto.getCpf());
        dep.setSexo(dto.getSexo());
        dep.setUrlWebsite(dto.getUrlWebsite());
        dep.setDataNascimento(dto.getDataNascimento());
        dep.setEscolaridade(dto.getEscolaridade());
        dep.setMunicipioNascimento(dto.getMunicipioNascimento());
        dep.setUfNascimento(dto.getUfNascimento());

        if (dto.getUltimoStatus() != null) {
            var status = dto.getUltimoStatus();
            dep.setNomeEleitoral(status.getNomeEleitoral());
            dep.setSituacao(status.getSituacao());
            dep.setCondicaoEleitoral(status.getCondicaoEleitoral());

            if (status.getGabinete() != null) {
                var gab = status.getGabinete();
                dep.setGabineteNome(gab.getNome());
                dep.setGabinetePredio(gab.getPredio());
                dep.setGabineteSala(gab.getSala());
                dep.setGabineteAndar(gab.getAndar());
                dep.setGabineteTelefone(gab.getTelefone());
            }
        }
    }
}
