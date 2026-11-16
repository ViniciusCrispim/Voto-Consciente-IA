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
                        .title("Voto Consciente IA — Plataforma de Auditoria e Inteligência Parlamentar")
                        .description("""
                                API REST em Java 21 e Spring Boot para auditoria e análise de atuação parlamentar na Câmara dos Deputados com Inteligência Artificial (Google Gemini + RAG Direcionado).
                                
                                **Recursos principais:**
                                - 🏛️ Integração com a API v2 de Dados Abertos da Câmara dos Deputados
                                - 💰 Auditoria da Cota Parlamentar (CEAP), notas fiscais e maiores fornecedores
                                - 📜 Consulta de Projetos de Lei (PL/PEC) e Votações Nominais em Plenário
                                - 🤖 Assistente Cívico Conversacional com Roteamento Semântico e Guardrails Anti-Alucinação
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Vinícius Crispim")
                                .url("https://github.com/ViniciusCrispim/Voto-Consciente-IA"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Servidor Local")
                ));
    }
}
