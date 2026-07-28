FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 neuroflow

COPY --from=build /workspace/target/*.jar /app/app.jar
RUN chown -R neuroflow:neuroflow /app

USER neuroflow
EXPOSE 8080

HEALTHCHECK --interval=20s --timeout=5s --retries=12 --start-period=60s \
  CMD curl -fsS http://localhost:8080/api/v1/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
