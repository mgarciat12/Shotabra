# syntax=docker/dockerfile:1

# ---------- Build: Maven + JDK 25 ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/shotabra.jar extract --destination /build/app

# ---------- Runtime: JRE 25 ligero, usuario no root ----------
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S shotabra && adduser -S -G shotabra shotabra
WORKDIR /app
COPY --from=build --chown=shotabra:shotabra /build/app/ ./
USER shotabra

ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${SERVER_PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/shotabra.jar"]
