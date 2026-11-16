package br.com.votoconsciente.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${apis.camara.base-url}")
    private String camaraBaseUrl;

    @Value("${apis.camara.timeout-seconds}")
    private int camaraTimeout;

    @Value("${apis.tse.base-url}")
    private String tseBaseUrl;

    @Value("${apis.tse.timeout-seconds}")
    private int tseTimeout;

    @Value("${apis.senado.base-url}")
    private String senadoBaseUrl;

    @Value("${apis.senado.timeout-seconds}")
    private int senadoTimeout;

    @Value("${apis.transparencia.base-url}")
    private String transparenciaBaseUrl;

    @Value("${apis.transparencia.api-key:}")
    private String transparenciaApiKey;

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    /**
     * Buffer de 16MB para respostas grandes das APIs externas.
     */
    private ExchangeStrategies largeBufferStrategies() {
        return ExchangeStrategies.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    private HttpClient buildHttpClient(int timeoutSeconds) {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutSeconds * 1000)
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS)));
    }

    @Bean("camaraWebClient")
    public WebClient camaraWebClient() {
        return WebClient.builder()
                .baseUrl(camaraBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient(camaraTimeout)))
                .exchangeStrategies(largeBufferStrategies())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean("tseWebClient")
    public WebClient tseWebClient() {
        return WebClient.builder()
                .baseUrl(tseBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient(tseTimeout)))
                .exchangeStrategies(largeBufferStrategies())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean("senadoWebClient")
    public WebClient senadoWebClient() {
        return WebClient.builder()
                .baseUrl(senadoBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient(senadoTimeout)))
                .exchangeStrategies(largeBufferStrategies())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean("transparenciaWebClient")
    public WebClient transparenciaWebClient() {
        return WebClient.builder()
                .baseUrl(transparenciaBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient(senadoTimeout)))
                .exchangeStrategies(largeBufferStrategies())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("chave-api-dados", transparenciaApiKey)
                .build();
    }
}
