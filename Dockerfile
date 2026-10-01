# --- Etapa de build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cachear dependencias primero
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Las clases de jOOQ ya estan commiteadas en src/main/java/com/taskflow/jooq/generated
# (ver README "Flujo de codegen de jOOQ"): este build NO necesita Docker-in-Docker ni MySQL
# para compilar, solo compila el codigo tal cual esta en el repo.
COPY src ./src
RUN mvn -q -B package -DskipTests

# --- Etapa de runtime ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S taskflow && adduser -S taskflow -G taskflow
USER taskflow

COPY --from=build /app/target/taskflow-jooq-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
