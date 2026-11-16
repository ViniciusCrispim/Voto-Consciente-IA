package br.com.votoconsciente.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Voto Consciente IA — API")
                        .description("""
                                API REST para consulta de informações sobre políticos brasileiros.
                                
                                **Fontes integradas:**
                                - 🏛️ API de Dados Abertos da Câmara dos Deputados
                                - 🗳️ TSE DivulgaCand (candidatos eleições 2026)
                                - 🏗️ Portal de Dados Abertos do Senado Federal
                                - 💰 Portal da Transparência do Governo Federal
                                
                                Os dados são armazenados localmente com atualização automática em background.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Voto Consciente IA")
                                .url("https://github.com/voto-consciente-ia"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Desenvolvimento")
                ));
    }
}
