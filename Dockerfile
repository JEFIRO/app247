# Etapa de build
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copia os arquivos do projeto
COPY pom.xml .
COPY src ./src

# Gera o .jar
RUN mvn clean package -DskipTests

# Imagem final
FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copia o jar gerado
COPY --from=build /app/target/*.jar app.jar

# Porta padrão do Spring Boot
EXPOSE 8080

# Define o profile ativo
ENV SPRING_PROFILES_ACTIVE=dev

# Inicia a aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]