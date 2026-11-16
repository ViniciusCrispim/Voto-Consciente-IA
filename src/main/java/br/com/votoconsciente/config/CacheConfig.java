package br.com.votoconsciente.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    /**
     * Caches disponíveis:
     * - "deputados"        : lista de deputados (TTL 60 min)
     * - "deputado-detalhe" : detalhes de um deputado (TTL 60 min)
     * - "votacoes"         : votações de um deputado (TTL 120 min)
     * - "despesas"         : despesas de um deputado (TTL 120 min)
     * - "proposicoes"      : proposições (TTL 120 min)
     * - "partidos"         : lista de partidos (TTL 24h)
     * - "candidatos-tse"   : candidatos TSE 2026 (TTL 6h)
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                "deputados",
                "deputado-detalhe",
                "votacoes",
                "despesas",
                "proposicoes",
                "partidos",
                "candidatos-tse"
        );
        manager.setCaffeine(defaultCaffeine());
        return manager;
    }

    private Caffeine<Object, Object> defaultCaffeine() {
        return Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .maximumSize(1000)
                .recordStats();
    }
}
