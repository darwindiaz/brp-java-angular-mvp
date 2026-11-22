# BRP · Base del backend

Arranque provisional para desarrollo local con JDK 25 y Docker con Compose.

## PostgreSQL

Desde la raíz, crea `.env` una sola vez a partir de la plantilla. Usa una
contraseña local compuesta por letras y números para que Docker Compose y
Spring lean el mismo valor. En PowerShell puedes generarla sin mostrarla:

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    $brpPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    (Get-Content .env.example) -replace '^POSTGRES_PASSWORD=$', "POSTGRES_PASSWORD=$brpPassword" |
        Set-Content -LiteralPath .env -Encoding utf8NoBOM
    Remove-Variable brpPassword
}
docker compose up -d postgres
docker compose ps
```

Espera a que PostgreSQL aparezca como `healthy`. El puerto local 55432
solo se publica en `127.0.0.1` y se puede cambiar con `POSTGRES_PORT`.
El volumen conserva los datos al detener o recrear el contenedor. Cambiar
las credenciales del archivo después de inicializar el volumen no cambia
las credenciales de la base ya inicializada.

## Backend y pruebas iniciales

Desde la raíz del repositorio:

```powershell
Set-Location backend
.\gradlew.bat clean build
.\gradlew.bat bootRun --args="--spring.profiles.active=dev"
```

En Linux y macOS usa `./gradlew`. Ejecuta el Wrapper desde `backend` para
que el perfil `dev` encuentre `../.env`. Las variables de entorno, si
están presentes, tienen prioridad. Las pruebas usan PostgreSQL local y
comprueban conexión, salud y acceso restringido.

En otra terminal:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

La respuesta esperada es HTTP 200 con `status: UP`. El endpoint no expone
detalles de infraestructura.

## Hallazgos, limitaciones y mejoras futuras

La auditoría documental obtuvo 82/100. Quedan por cerrar la matriz por
operación y módulo, la serialización del último administrador, el contrato
de fallos y reintentos de adjuntos, la precisión monetaria y residual, la
referencia y normalización de la ingesta, y la aceptación completa de
Angular y del README de entrega. Consulta el [plan](plans/implementation-plan.md)
y los [supuestos](docs/assumptions.md). Los requisitos explícitos siguen
pendientes hasta que se implementen y verifiquen.
