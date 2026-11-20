#!/usr/bin/env sh
# Arranca PostgreSQL (docker compose) y la app con el perfil dev dentro de un contenedor JDK 25.
# La app queda en http://localhost:8080 y en http://<IP-de-tu-ordenador>:8080 para los móviles.
set -e
cd "$(dirname "$0")/.."
docker compose up -d db
exec docker run --rm -it \
  -v "$PWD":/workspace -w /workspace \
  -v shotabra-m2:/root/.m2 \
  --network shotabra_default \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/shotabra \
  maven:3.9-eclipse-temurin-25 mvn -B spring-boot:run
