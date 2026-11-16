package br.com.votoconsciente.deputado.service;

import br.com.votoconsciente.camara.client.CamaraApiClient;
import br.com.votoconsciente.camara.dto.DespesaDTO;
import br.com.votoconsciente.camara.dto.ProposicaoDTO;
import br.com.votoconsciente.camara.dto.VotacaoDTO;
import br.com.votoconsciente.camara.service.CamaraSyncService;
import br.com.votoconsciente.deputado.dto.DeputadoResponseDTO;
import br.com.votoconsciente.deputado.entity.Deputado;
import br.com.votoconsciente.deputado.repository.DeputadoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Serviço de negócio para Deputados.
 *
 * Implementa a estratégia híbrida:
 * 1. Busca no banco local primeiro
 * 2. Se dados desatualizados → retorna o que tem + dispara atualização async
 * 3. Se não existe → busca na API ao vivo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeputadoService {

    private final DeputadoRepository deputadoRepository;
    private final CamaraApiClient camaraApiClient;
    private final CamaraSyncService camaraSyncService;

    @Value("${sync.ttl-horas:24}")
    private int ttlHoras;

    // ─────────────────────────────────────────────────────────
    // Listagem e Busca
    // ─────────────────────────────────────────────────────────

    private void garantirBaseInicialCarregada() {
        if (deputadoRepository.count() == 0) {
            log.info("Banco local vazio — realizando sincronização inicial da bancada da Câmara...");
            camaraSyncService.sincronizarDeputados();
        }
    }

    @Cacheable(value = "deputados", key = "'todos'")
    public List<DeputadoResponseDTO> listarTodos() {
        garantirBaseInicialCarregada();
        return deputadoRepository.findAll().stream().map(this::toDTO).toList();
    }

    @Cacheable(value = "deputados", key = "#uf")
    public List<DeputadoResponseDTO> listarPorUf(String uf) {
        garantirBaseInicialCarregada();
        return deputadoRepository.findBySiglaUfIgnoreCase(uf)
                .stream().map(this::toDTO).toList();
    }

    @Cacheable(value = "deputados", key = "#partido")
    public List<DeputadoResponseDTO> listarPorPartido(String partido) {
        garantirBaseInicialCarregada();
        return deputadoRepository.findBySiglaPartidoIgnoreCase(partido)
                .stream().map(this::toDTO).toList();
    }

    @Cacheable(value = "deputados", key = "#uf + '-' + #partido")
    public List<DeputadoResponseDTO> listarPorUfEPartido(String uf, String partido) {
        garantirBaseInicialCarregada();
        return deputadoRepository.findBySiglaUfIgnoreCaseAndSiglaPartidoIgnoreCase(uf, partido)
                .stream().map(this::toDTO).toList();
    }

    public List<DeputadoResponseDTO> buscarPorNome(String nome) {
        garantirBaseInicialCarregada();
        return deputadoRepository.buscarPorNome(nome)
                .stream().map(this::toDTO).toList();
    }

    // ─────────────────────────────────────────────────────────
    // Detalhe (estratégia híbrida)
    // ─────────────────────────────────────────────────────────

    @Cacheable(value = "deputado-detalhe", key = "#camaraId")
    public Optional<DeputadoResponseDTO> buscarPorCamaraId(Long camaraId) {
        Optional<Deputado> local = deputadoRepository.findByCamaraId(camaraId);

        if (local.isPresent()) {
            Deputado dep = local.get();

            // Dados desatualizados → retorna o que tem + atualiza em background
            if (dep.estaDesatualizado(ttlHoras)) {
                log.debug("Deputado {} desatualizado — atualizando em background", camaraId);
                camaraSyncService.atualizarDetalheDeputadoAsync(camaraId);
            }

            return Optional.of(toDTO(dep));
        }

        // Não existe localmente → busca na API ao vivo da Câmara, salva no banco local e entrega imediatamente!
        log.info("Deputado {} não encontrado localmente — buscando diretamente na API da Câmara...", camaraId);
        Deputado salvo = camaraSyncService.sincronizarOuAtualizarDetalheDeputado(camaraId);
        if (salvo == null) {
            return Optional.empty();
        }

        return Optional.of(toDTO(salvo));
    }

    // ─────────────────────────────────────────────────────────
    // Dados enriquecidos (via API ao vivo com cache)
    // ─────────────────────────────────────────────────────────

    @Cacheable(value = "despesas", key = "#camaraId + '-' + #ano + '-' + #mes")
    public List<DespesaDTO> buscarDespesas(Long camaraId, Integer ano, Integer mes) {
        log.debug("Buscando despesas do deputado {} (ano={}, mes={})", camaraId, ano, mes);
        return camaraApiClient.buscarDespesas(camaraId, ano, mes);
    }

    @Cacheable(value = "votacoes", key = "#camaraId")
    public List<br.com.votoconsciente.camara.dto.VotoParlamentarDTO> buscarVotacoes(Long camaraId) {
        log.debug("Buscando histórico de votações nominais do deputado {}", camaraId);
        return camaraApiClient.buscarVotosPorDeputado(camaraId, 25);
    }

    @Cacheable(value = "proposicoes", key = "#camaraId + '-' + #ano")
    public List<ProposicaoDTO> buscarProposicoes(Long camaraId, Integer ano) {
        log.debug("Buscando proposições do deputado {} (ano={})", camaraId, ano);
        return camaraApiClient.buscarProposicoes(camaraId, ano);
    }

    // ─────────────────────────────────────────────────────────
    // Mapeamento
    // ─────────────────────────────────────────────────────────

    private DeputadoResponseDTO toDTO(Deputado dep) {
        DeputadoResponseDTO.GabineteDTO gabinete = null;
        if (dep.getGabineteNome() != null || dep.getGabineteSala() != null) {
            gabinete = DeputadoResponseDTO.GabineteDTO.builder()
                    .nome(dep.getGabineteNome())
                    .predio(dep.getGabinetePredio())
                    .sala(dep.getGabineteSala())
                    .andar(dep.getGabineteAndar())
                    .telefone(dep.getGabineteTelefone())
                    .build();
        }

        return DeputadoResponseDTO.builder()
                .id(dep.getId())
                .camaraId(dep.getCamaraId())
                .nome(dep.getNome())
                .nomeCivil(dep.getNomeCivil())
                .nomeEleitoral(dep.getNomeEleitoral())
                .siglaPartido(dep.getSiglaPartido())
                .siglaUf(dep.getSiglaUf())
                .email(dep.getEmail())
                .urlFoto(dep.getUrlFoto())
                .urlWebsite(dep.getUrlWebsite())
                .sexo(dep.getSexo())
                .dataNascimento(dep.getDataNascimento())
                .escolaridade(dep.getEscolaridade())
                .municipioNascimento(dep.getMunicipioNascimento())
                .ufNascimento(dep.getUfNascimento())
                .situacao(dep.getSituacao())
                .condicaoEleitoral(dep.getCondicaoEleitoral())
                .idLegislatura(dep.getIdLegislatura())
                .gabinete(gabinete)
                .ultimaAtualizacao(dep.getUltimaAtualizacao())
                .fonte("BANCO_LOCAL")
                .build();
    }
}
