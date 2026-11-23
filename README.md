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

## Fundación Angular y Docker

La base usa Angular 20.3.35, Angular CLI local 20.3.43, Node.js 20.19.6, npm 10.8.2 y TypeScript 5.9. Angular 20.3 admite Node.js `^20.19.0` y TypeScript `>=5.8 <6.0`. El CLI local se instala con el proyecto y `npm start` lo usa automáticamente. [Matriz de compatibilidad de Angular](https://angular.dev/reference/versions)

Desde la raíz, inicia PostgreSQL y Spring Boot según los apartados anteriores. Con Spring Boot en `127.0.0.1:8080`, ejecuta en otra terminal PowerShell:

```powershell
Set-Location frontend
npm ci
npm start
```

Abre `http://127.0.0.1:4200/diagnostico`. El proxy de Angular reenvía `/api/*` a Spring Boot y traduce `/api/health` a `/actuator/health`. Desde `frontend/`, `npm run build -- --configuration production` genera la compilación optimizada en `dist/brp-frontend/browser/`. `src/app/core/` contiene infraestructura compartida, `src/app/features/` agrupa pantallas por funcionalidad y `src/environments/` define la configuración de compilación. Cada componente tiene su plantilla HTML y su hoja SCSS separadas.

Para ejecutar los tres servicios juntos, configura `.env` según las secciones de PostgreSQL e identidad. Compose también admite esas variables desde el entorno del proceso. Desde la raíz:

```powershell
docker compose -f docker-compose.yml -f compose.frontend.yml config --quiet
docker compose -f docker-compose.yml -f compose.frontend.yml up -d --build
```

Abre `http://127.0.0.1:8088/diagnostico`. `BRP_FRONTEND_PORT` permite cambiar ese puerto. Nginx sirve la compilación estática y reenvía `/api` a `backend:8080`. Spring Boot se conecta a `postgres:5432` a través de la red interna de Compose. Las llamadas del navegador usan el mismo origen en ambos modos. El overlay mantiene los volúmenes de PostgreSQL y adjuntos. Si PostgreSQL ya está activo en modo local, detén ese servicio con `docker compose stop` antes de cambiar de modo.

El puerto del overlay queda en loopback para la demo. Si se despliega detrás de Nginx, el limitador actual de inicio de sesión puede agrupar usuarios bajo la IP del proxy. Véanse las consideraciones de despliegue en [Identidad y aprovisionamiento](#identidad-y-aprovisionamiento).

### Sesión y navegación Angular

La raíz abre `/inicio` con una sesión válida y `/login` cuando hace falta identificarse. Angular conserva el JWT en `sessionStorage` y valida el usuario activo con `GET /api/auth/me` al iniciar sesión y recargar la pestaña. La sesión se limpia al cerrar sesión, expirar el token o recibir un `401` de una solicitud autenticada. Un `403` conserva la sesión.

El menú y las rutas aplican las combinaciones de rol y módulo del [contrato de autenticación](docs/frontend-auth-contract.md). Las rutas de los módulos futuros indican que la interfaz funcional sigue pendiente. `/diagnostico` sigue disponible para comprobar conectividad.

### Radicación desde Angular

Inicia sesión como radicador con el módulo `RADICACION` y abre **Radicación**. Selecciona un bien del catálogo vigente, registra entre uno y cinco oferentes y añade entre uno y seis pagos por oferente. Puedes introducir decimales con coma o punto. El formulario valida los importes y participaciones sin redondearlos.

**Consultar descuento** obtiene la vista previa del servidor. **Radicar oferta** envía los oferentes y pagos. La confirmación muestra el número de radicación y los valores definitivos devueltos por el backend. Si el BRP deja de estar disponible, actualiza el catálogo y selecciona un bien vigente.

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

### API de radicación

Usa el JWT de un usuario activo con rol `RADICADOR` y módulo
`RADICACION`. Envía el token como `Authorization: Bearer <token>`.

| Método y ruta | Operación |
|---|---|
| `GET /api/offers/catalog` | Lista BRP disponibles para seleccionar al radicar, ordenados por código. Cada elemento incluye código, tipo, descripción, ubicación y precio de referencia vigente. |
| `POST /api/offers` | Recibe `brpCode` y `bidders`. Devuelve `201` con el número de radicación y el resumen calculado por el servidor. |
| `POST /api/offers/discount-preview` | Recibe `brpCode` y `total`. Devuelve precio de referencia, importe, descuento e indicador de sobreprecio sin guardar una oferta. |
| `GET /api/offers/mine` | Devuelve resúmenes de las ofertas propias, de la más reciente por número de radicación a la más antigua. |
| `GET /api/offers/mine/{number}` | Devuelve el resumen, los oferentes y sus pagos. Una oferta inexistente o ajena devuelve `404`. |

El catálogo de selección usa `RADICACION` y muestra solo bienes cuya
última versión está `DISPONIBLE`. La pestaña de catálogo de seguimiento
usa `GET /api/follow-up/catalog` y exige `SEGUIMIENTO`.

Ejemplo de cuerpo para radicar:

```json
{
  "brpCode": "BRP-0001",
  "bidders": [
    {
      "identificationType": "CC",
      "identificationNumber": "123",
      "name": "Oferente de prueba",
      "percentage": 100.0000,
      "payments": [
        {"method": "CONTADO", "date": "2026-12-01", "amount": 160000000.00}
      ]
    }
  ]
}
```

`contact` y `lender` son opcionales. La financiadora solo aplica a un
pago `PRESTAMO`. El servidor determina el radicador, el precio de referencia,
el total, el descuento y el estado. El alta y la vista previa devuelven
`409` si el BRP vigente no está disponible con un precio válido. El alta
vuelve a consultar el catálogo aunque antes se haya pedido una vista previa.

### API de análisis y aprobación

| Método y ruta | Permiso y operación |
|---|---|
| `GET /api/offers/analysis` | `ANALISTA` con módulo `ANALISIS`. Devuelve los radicados que puede tomar y las ofertas en proceso que tiene asignadas. |
| `POST /api/offers/analysis/{number}/{action}` | Mismo permiso. Acciones `take`, `escalate` y `reject`. Solo el analista asignado puede escalar o rechazar una oferta en proceso. |
| `GET /api/offers/approval` | `JEFE` con módulo `APROBACION`. Devuelve las ofertas escaladas. |
| `POST /api/offers/approval/{number}/{action}` | Mismo permiso. Acciones `approve`, `reject` y `return`. La devolución conserva el analista asignado. |

Envía el JWT en el encabezado `Authorization` y la versión de la bandeja en
el cuerpo del comando, por ejemplo `{"version":0}`. La respuesta `200`
contiene el resumen de la oferta, el identificador del analista, las fechas
del flujo y la versión resultante. Si otra operación cambió la versión o
el estado impide la acción, el servidor devuelve `409`. Ante un conflicto,
actualiza la bandeja y revisa el estado antes de volver a intentar la acción.

Para probar el recorrido, radica una oferta, tómala desde la bandeja de
análisis y escálala. Desde aprobación, devuélvela y comprueba que aparece
en la bandeja del mismo analista. Escálala de nuevo y apruébala con un jefe
distinto del analista asignado.

### API de adjuntos

| Método y ruta | Operación |
|---|---|
| `POST /api/offers/{number}/attachments` | Recibe una o más partes `files` en `multipart/form-data`. Devuelve `201` y los metadatos. |
| `GET /api/offers/{number}/attachments` | Lista identificador, nombre, tipo, tamaño en bytes y fecha de carga. |
| `GET /api/offers/{number}/attachments/{attachmentId}` | Descarga el contenido como archivo. Valida que el adjunto pertenezca a la oferta de la URL. |

Los tres endpoints usan JWT y autorización vigente sobre la oferta. El
radicador con `RADICACION` accede a sus ofertas. El analista con `ANALISIS`
accede a las que tiene asignadas. El jefe con `APROBACION` accede a las
escaladas. El administrador con `SEGUIMIENTO` accede al conjunto de ofertas.
Para un radicador, una oferta ajena y una inexistente producen `404`.

Se aceptan PDF, JPG, JPEG y PNG. El servidor valida extensión, MIME
declarado, firma inicial y tamaño antes de guardar el lote. Devuelve
`400` por archivo inválido y `413` si se excede un límite de tamaño.

| Variable | Valor inicial |
|---|---|
| `BRP_ATTACHMENTS_DIRECTORY` | `./storage/offer-attachments`, relativo al directorio de ejecución |
| `BRP_ATTACHMENTS_MAX_FILE_SIZE` | `10MB` (10 MiB por archivo) |
| `BRP_ATTACHMENTS_MAX_REQUEST_SIZE` | `50MB` (50 MiB por solicitud, incluida la envoltura multipart) |

Configura un directorio privado con permisos restringidos al proceso y
fuera de cualquier raíz pública. Cada archivo se guarda con su UUID. El
UUID es también la referencia relativa conservada en PostgreSQL. Los
endpoints entregan metadatos y contenido, y la carpeta permanece privada.
Incluye la carpeta y la base de datos en la copia operativa.

```powershell
$offerUrl = "http://127.0.0.1:8080/api/offers/$registrationNumber/attachments"
curl.exe -H "Authorization: Bearer $accessToken" -F "files=@informe.pdf;type=application/pdf" -F "files=@foto.png;type=image/png" $offerUrl
curl.exe -H "Authorization: Bearer $accessToken" $offerUrl
curl.exe -H "Authorization: Bearer $accessToken" -o descarga.pdf "$offerUrl/$attachmentId"
```

Usa el número de radicación, el JWT autorizado y el UUID devuelto por el
listado. Si la transacción revierte, el backend retira los archivos creados
durante la solicitud. Si la limpieza falla o el resultado de la transacción
es incierto, el log registra los UUID para conciliarlos con PostgreSQL.
La carga no cambia la oferta. La inspección por firma no sustituye un
análisis antimalware. La eliminación, la retención y las cuotas acumuladas
quedan fuera de esta etapa.

En despliegues con un PostgreSQL externo al Compose del proyecto, aplica
`log_error_verbosity=terse` y `log_parameter_max_length_on_error=0` para
evitar que los logs del servidor incluyan filas rechazadas o sus parámetros.

### API de seguimiento de ofertas

| Método y ruta | Operación |
|---|---|
| `GET /api/follow-up/offers` | Lista las ofertas visibles para el usuario, ordenadas por número de radicación descendente. |
| `GET /api/follow-up/offers/{number}` | Devuelve la misma proyección para una oferta visible. Para un radicador, una oferta ajena y una inexistente reciben `404`. |

Ambas rutas requieren JWT de una cuenta activa con módulo `SEGUIMIENTO`.
El radicador ve sus propias ofertas. Analistas, jefes y administradores
con el módulo ven todas. La respuesta contiene el número de radicación,
el código BRP, el total, el descuento histórico, el estado, el analista
asignado y las fechas de trazabilidad. `analyst` contiene `id` y `name`
cuando hay asignación. Las fechas se devuelven como instantes UTC y los
eventos que aún no ocurrieron se representan con `null`.

### API de catálogo de seguimiento

Ambas rutas requieren JWT de una cuenta activa con el módulo
`SEGUIMIENTO`.

| Método y ruta | Respuesta |
|---|---|
| `GET /api/follow-up/catalog` | Devuelve una versión vigente por código, ordenada por código ascendente. Cada elemento contiene `code`, `assetType`, `description`, `location` con `department` y `municipality`, `status`, `referencePrice` e `ingestionDate`. |
| `GET /api/follow-up/catalog/freshness` | Devuelve `latestIngestionDate` en formato `YYYY-MM-DD`, o `null` si no hay ingestas. |

El radicador solo ve bienes cuya última versión esté `DISPONIBLE`.
Analistas, jefes y administradores con el módulo ven todas las versiones
vigentes, incluidos bienes sin precio. La fecha de frescura es global
para todos los roles.

## Pruebas de integración

El cierre de la radicación (etapas 6.1 y 6.2) se verificó con
`gradlew.bat clean build` en PostgreSQL 17.12 aislado. Pasaron las 110 pruebas
sin fallos, errores ni omitidas. La etiqueta `postgres:17` del Compose puede
resolver a otra revisión. Para reproducir el cierre, selecciona
`postgres:17.12` en una sobreescritura de Compose y comprueba que
`SHOW server_version_num` devuelve `170012` antes de ejecutar Gradle.

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
