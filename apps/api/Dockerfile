# syntax=docker/dockerfile:1.7
# ---- build ------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Resolve dependencies in their own layer so source edits do not re-download them.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q -ntp dependency:go-offline

COPY src src
RUN ./mvnw -B -ntp -DskipTests package \
 && cp target/civix-api.jar /workspace/app.jar

# ---- runtime ----------------------------------------------------------------
# Alpine variant: ships busybox wget for the HEALTHCHECK (no curl needed).
FROM eclipse-temurin:25-jre-alpine AS runtime

RUN addgroup -S civix && adduser -S -G civix -H -s /sbin/nologin civix
WORKDIR /app
COPY --from=build --chown=civix:civix /workspace/app.jar /app/app.jar

USER civix:civix
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness >/dev/null 2>&1 || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
