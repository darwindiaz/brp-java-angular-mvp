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

## Backend de desarrollo

Desde la raíz del repositorio:

```powershell
Set-Location backend
.\gradlew.bat bootRun --args="--spring.profiles.active=dev"
```

En Linux y macOS usa `./gradlew`. Ejecuta el Wrapper desde `backend` para
que el perfil `dev` encuentre `../.env`. Las variables de entorno, si
están presentes, tienen prioridad.

En otra terminal:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

La respuesta esperada es HTTP 200 con `status: UP`. El endpoint no expone
detalles de infraestructura.

## Identidad y aprovisionamiento

Antes del primer arranque, genera el hash BCrypt de la contraseña inicial
fuera del repositorio. Por ejemplo, este comando de Docker pide la
contraseña de forma interactiva y muestra `admin:<hash>`:

```powershell
docker run --rm -it httpd:2.4-alpine htpasswd -nBC 12 admin
$env:BRP_BOOTSTRAP_ADMIN_HASH = Read-Host -MaskInput 'Hash BCrypt sin el prefijo admin:'
$env:BRP_JWT_SECRET_BASE64 = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Liquibase crea `admin` activo, le asigna `ADMINISTRACION` y registra el
evento inicial. `BRP_BOOTSTRAP_ADMIN_HASH` solo se requiere al aplicar el
changeset 005. Cambiar esa variable después no cambia la contraseña. Conserva
la clave JWT fuera del repositorio y reutiliza la misma en reinicios del
ambiente si quieres conservar la validez de los JWT emitidos.

### API de identidad

| Método y ruta | Operación |
|---|---|
| `POST /api/auth/login` | Recibe `login` y `password`. Devuelve `accessToken`, `tokenType` y `expiresIn`. |
| `GET /api/auth/me` | Identidad, rol y módulos vigentes. |
| `POST /api/admin/users` | Alta con `name`, `login`, `password` y `role`. |
| `GET /api/admin/users`, `GET /api/admin/users/{id}` | Consultar lista o detalle. |
| `PATCH /api/admin/users/{id}/role` | Cambiar rol con `{"role":"ANALISTA"}`. |
| `PATCH /api/admin/users/{id}/status` | Activar o desactivar con `{"active":false}`. |
| `GET /api/admin/modules`, `GET /api/admin/users/{id}/modules` | Consultar módulos disponibles o asignados. |
| `PUT /api/admin/users/{id}/modules/{module}`, `DELETE /api/admin/users/{id}/modules/{module}` | Asignar o retirar un módulo. |

```powershell
$brpPassword = Read-Host -MaskInput 'Contraseña de admin'
$brpLogin = Invoke-RestMethod -Method Post http://127.0.0.1:8080/api/auth/login `
    -ContentType 'application/json' -Body (@{login='admin'; password=$brpPassword} | ConvertTo-Json)
Remove-Variable brpPassword
$brpHeaders = @{ Authorization = "Bearer $($brpLogin.accessToken)" }
Invoke-RestMethod http://127.0.0.1:8080/api/auth/me -Headers $brpHeaders
Invoke-RestMethod http://127.0.0.1:8080/api/admin/modules -Headers $brpHeaders
```

El JWT dura 15 minutos. El servidor exige `iat` y `exp`, aplica un máximo de
900 segundos entre ambos y admite hasta cinco segundos de adelanto en
`iat` por desfase de reloj. Salud y login ignoran el encabezado Bearer.
Todas las operaciones administrativas exigen el
rol `ADMINISTRADOR` y el módulo `ADMINISTRACION` vigentes. Una restricción
de negocio, como intentar retirar el acceso administrativo propio, se
responde con `409`. Aplica HTTPS fuera del entorno local y no habilites
trazas de credenciales, parámetros SQL ni tokens.

El login devuelve `429` cuando se agota el cupo de la ventana para la IP
remota o para toda la instancia. Los intentos válidos en formato cuentan
independientemente de si la cuenta existe o la contraseña es correcta.

| Variable de entorno | Valor inicial |
|---|---|
| `BRP_IDENTITY_LOGIN_MAX_ATTEMPTS` | 10 intentos por IP |
| `BRP_IDENTITY_LOGIN_MAX_TOTAL_ATTEMPTS` | 120 intentos por instancia |
| `BRP_IDENTITY_LOGIN_WINDOW` | `PT1M`, un minuto |

El contador usa memoria local de la instancia, acotada por el límite
global. Se reinicia al vencer la ventana o reiniciar la aplicación. La
ventana es fija y permite ráfagas al consumir un cupo justo antes del
corte y el siguiente justo después.

La IP se obtiene de la conexión que reporta el servidor. La aplicación
no toma `X-Forwarded-For` directamente de la solicitud. Si el servidor
recibe la IP del proxy o balanceador, varios usuarios compartirán el
cupo. Antes de desplegar detrás de un proxy, configura una cadena de
proxies de confianza que sanee los encabezados reenviados, o aplica el
límite en el gateway. En despliegues multinodo se necesita un limitador
compartido o un gateway.

## Pruebas de integración

Desde la raíz, levanta el PostgreSQL exclusivo de pruebas con
`compose.test.yml`. Tiene su propio servicio, puerto local y volumen.
Configura las contraseñas la primera vez y vuelve a usar las mismas al
reiniciar con el volumen existente. Por ejemplo, en PowerShell:

```powershell
$env:BRP_TEST_JDBC_URL = 'jdbc:postgresql://127.0.0.1:55433/brp_test_catalog'
$env:BRP_TEST_DB_USER = 'brp_test_catalog'
$env:BRP_TEST_ADMIN_PASSWORD = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
$env:BRP_TEST_DB_PASSWORD = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
docker compose -f compose.test.yml up -d --wait postgres-test
```

Si cambias `BRP_TEST_POSTGRES_PORT`, ajusta también el puerto de
`BRP_TEST_JDBC_URL`. La URL debe identificar un solo servidor y omitir
parámetros JDBC. La contraseña administrativa solo se usa en Compose.

Configura además `BRP_BOOTSTRAP_ADMIN_HASH` y `BRP_JWT_SECRET_BASE64` con
valores exclusivos de testing, usando los comandos de aprovisionamiento.
Para las pruebas de login, asigna a `BRP_TEST_ADMIN_LOGIN_PASSWORD` la
contraseña que corresponde al hash de pruebas, sin guardarla en archivos.
Si reutilizas un volumen ya migrado, usa la contraseña con la que se sembró.

```powershell
$env:BRP_TEST_ADMIN_LOGIN_PASSWORD = Read-Host -MaskInput 'Contraseña admin de pruebas'
Set-Location backend
.\gradlew.bat clean build
```

Antes de inicializar los componentes de Spring, el perfil `test` valida las
credenciales, la propiedad de la base, los atributos y membresías del rol y
sus permisos efectivos sobre las demás bases del servidor. Rechaza JNDI
y registra un único `DataSource` para JPA y Liquibase. Las propiedades
genéricas de conexión no configuran ese `DataSource`. El `.env` solo se
consulta para excluir el nombre configurado para desarrollo.

Para detener el servidor de pruebas conservando su volumen, vuelve a la
raíz y ejecuta `docker compose -f compose.test.yml down`.

## Hallazgos, limitaciones y mejoras futuras

La auditoría documental obtuvo 82/100. Quedan por cerrar la matriz por
operación y módulo, la serialización del último administrador, el contrato
de fallos y reintentos de adjuntos, la precisión monetaria y residual, la
referencia y normalización de la ingesta, y la aceptación completa de
Angular y del README de entrega. Consulta el [plan](plans/implementation-plan.md)
y los [supuestos](docs/assumptions.md). Los requisitos explícitos siguen
pendientes hasta que se implementen y verifiquen.
