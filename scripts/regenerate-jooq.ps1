# Regenera src/main/java/com/taskflow/jooq/generated a partir del esquema real de MySQL.
# Ver scripts/regenerate-jooq.sh para la explicacion completa del flujo; este script hace
# exactamente lo mismo pero con la CLI de docker invocada desde PowerShell.

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$Container = "taskflow-jooq-codegen-db"
$Port = 3310

Write-Host "Levantando MySQL temporal ($Container) en el puerto $Port..."
docker rm -f $Container 2>$null | Out-Null
docker run -d --name $Container -p "${Port}:3306" `
  -e MYSQL_ROOT_PASSWORD=codegen -e MYSQL_DATABASE=taskflow_codegen `
  mysql:8.4 | Out-Null

Write-Host "Esperando a que MySQL acepte conexiones..."
$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    docker exec $Container mysqladmin ping -uroot -pcodegen --silent 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $ready) {
    Write-Error "MySQL no respondio a tiempo."
    docker rm -f $Container 2>$null | Out-Null
    exit 1
}

Write-Host "Corriendo Flyway..."
mvn -q -P jooq-codegen "-Dcodegen.db.port=$Port" flyway:migrate
if ($LASTEXITCODE -ne 0) { docker rm -f $Container 2>$null | Out-Null; exit 1 }

Write-Host "Corriendo jOOQ codegen..."
mvn -q -P jooq-codegen "-Dcodegen.db.port=$Port" generate-sources
if ($LASTEXITCODE -ne 0) { docker rm -f $Container 2>$null | Out-Null; exit 1 }

Write-Host "Bajando el MySQL temporal..."
docker rm -f $Container 2>$null | Out-Null

Write-Host ""
Write-Host "Listo. Revisa el diff de src/main/java/com/taskflow/jooq/generated y commitealo si el"
Write-Host "esquema cambio de verdad (si no cambio nada, jOOQ no deberia tocar los archivos)."
