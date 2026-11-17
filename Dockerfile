# ==============================================================================
# ESTÁGIO 1: Build (Compilação com Maven e JDK 21)
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copia primeiro os arquivos de configuração do Maven para aproveitar o cache de camadas (Layer Caching)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Garante permissão de execução no wrapper e baixa dependências em cache
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copia o código-fonte e gera o pacote .jar executável (pulando testes no build de imagem)
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ==============================================================================
# ESTÁGIO 2: Runtime (Imagem enxuta apenas com JRE 21 e usuário não-root)
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Boas práticas de segurança (DevSecOps): cria um usuário sem privilégios de root
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copia apenas o artefato .jar compilado no estágio anterior
COPY --from=builder /app/target/*.jar app.jar

# Porta padrão da API Spring Boot
EXPOSE 8080

# Configurações otimizadas da JVM para ambientes em containers
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
