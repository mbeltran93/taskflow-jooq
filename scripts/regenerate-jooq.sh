#!/usr/bin/env bash
# Regenera src/main/java/com/taskflow/jooq/generated a partir del esquema real de MySQL.
#
# Flujo (ver README, seccion "Flujo de codegen de jOOQ"):
#   1. Levanta un MySQL descartable con la CLI de docker (no Testcontainers: en Windows +
#      Docker Desktop el cliente docker-java de Testcontainers no logra hablar con el pipe
#      del daemon, problema ya documentado en los repos hermanos de este portafolio; la CLI
#      de docker si funciona siempre).
#   2. Corre las migraciones de Flyway contra esa base (fuente de verdad del esquema).
#   3. Corre jooq-codegen-maven contra esa misma base ya migrada.
#   4. Tira el contenedor.
#
# Las clases quedan commiteadas en el repo: el build normal (mvn package / docker build) NO
# necesita Docker ni MySQL, compila directo con lo que ya esta generado. Correr este script
# solo hace falta cuando cambia el esquema (una migracion nueva en db/migration).
set -euo pipefail
cd "$(dirname "$0")/.."

CONTAINER=taskflow-jooq-codegen-db
PORT=3310

echo "Levantando MySQL temporal ($CONTAINER) en el puerto $PORT..."
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$CONTAINER" -p "${PORT}:3306" \
  -e MYSQL_ROOT_PASSWORD=codegen -e MYSQL_DATABASE=taskflow_codegen \
  mysql:8.4 >/dev/null

echo "Esperando a que MySQL acepte conexiones..."
tries=0
until docker exec "$CONTAINER" mysqladmin ping -uroot -pcodegen --silent >/dev/null 2>&1; do
  tries=$((tries + 1))
  if [ "$tries" -gt 60 ]; then
    echo "MySQL no respondio a tiempo." >&2
    docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
    exit 1
  fi
  sleep 2
done

echo "Corriendo Flyway..."
mvn -q -P jooq-codegen -Dcodegen.db.port="$PORT" flyway:migrate

echo "Corriendo jOOQ codegen..."
mvn -q -P jooq-codegen -Dcodegen.db.port="$PORT" generate-sources

echo "Bajando el MySQL temporal..."
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true

echo
echo "Listo. Revisa el diff de src/main/java/com/taskflow/jooq/generated y commitealo si el"
echo "esquema cambio de verdad (si no cambio nada, jOOQ no deberia tocar los archivos)."
