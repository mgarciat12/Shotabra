#!/usr/bin/env sh
# Ejecuta Maven dentro de un contenedor con JDK 25 (no hace falta tener Java 25 instalado).
# Monta el socket de Docker para que los tests con Testcontainers puedan arrancar PostgreSQL.
#   ./scripts/mvn.sh verify
#   ./scripts/mvn.sh -Dtest=GameServiceTest test
set -e
cd "$(dirname "$0")/.."
TTY_FLAG=""
[ -t 1 ] && TTY_FLAG="-t"
exec docker run --rm $TTY_FLAG \
  -v "$PWD":/workspace -w /workspace \
  -v shotabra-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  --add-host=host.docker.internal:host-gateway \
  maven:3.9-eclipse-temurin-25 mvn -B "$@"
