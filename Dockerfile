# syntax=docker/dockerfile:1.7

FROM maven:3.9.14-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:17-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app247 \
    && useradd --uid 10001 --gid app247 --no-create-home \
        --home-dir /app --shell /usr/sbin/nologin app247 \
    && mkdir -p /app/uploads \
    && chown -R app247:app247 /app

WORKDIR /app

COPY --from=build --chown=app247:app247 /workspace/target/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod

USER 10001:10001

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl --fail --silent --show-error http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
