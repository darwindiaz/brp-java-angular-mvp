# Plan incremental del MVP BRP

Estado inicial: diseño documentado. Las etapas están pendientes. P0 permite demostrar el recorrido seguro y P1 completa capacidades explícitas adicionales. [Requisitos](../docs/requirements.md) · [Reglas](../docs/business-rules.md)

| Etapa | Prioridad y objetivo | Componentes | Depende de | Aceptación | Pruebas |
|---|---|---|---|---|---|
| 1 | P0. Arrancar PostgreSQL. | Docker Compose, configuración. | — | DB disponible desde Docker con configuración externa. | Arranque desde entorno limpio y conexión. |
| 2 | P0. Iniciar backend Java 25. | Spring Boot, Gradle Wrapper, DTO y errores. | 1 | Aplicación ejecutable sin Gradle instalado y errores homogéneos. | Build limpio y prueba de arranque. |
| 3 | P0. Migrar esquema y catálogo. | Liquibase, catálogo, usuarios y módulos semilla. | 2 | 100 códigos, 56 disponibles normalizados, frescura 17/11/2026. | CHECK, deduplicación y filtro sobre la última ingesta. |
| 4 | P0. Proteger la API. | BCrypt, JWT, roles, módulos y capacidades. | 3 | Cuatro cuentas acceden según función y permiso. | Login inválido, expiración, 403 y alcance de consulta. |
| 5 | P1. Administrar permisos. | Usuarios, roles, módulos y auditoría. | 4 | Cambios auditados y al menos un administrador efectivo. | Retiro propio, auditoría atómica y cambios concurrentes. |
| 6 | P0. Radicar. | Oferta, oferentes, pagos y descuento. | 3, 4 | Oferta en RADICADO registrada en una transacción con avalúo histórico y número de secuencia. | Límites, escalas, documentos duplicados, correspondencia entre participaciones y pagos, rechazo de residuos, rollback y sobreprecio. |
| 7 | P0. Decidir ofertas. | Política de transición y bandejas API. | 6 | Seis transiciones, asignación, fechas y respuestas coherentes. | Rechazos, devolución, separación y conflicto 409 de toma. |
| 8 | P1. Gestionar adjuntos. | Disco local, metadatos y endpoints protegidos. | 4, 6 | Carga múltiple posterior a la radicación y lectura autorizada sin publicar rutas físicas. | Formato inválido, acceso ajeno y compensación de fallos sin invalidar la oferta. |
| 9 | P0. Consultar seguimiento. | Consultas de ofertas y catálogo. | 3, 4, 7 | Filtros por alcance, fechas y resultados coherentes. | Acceso ajeno por listado y detalle. |
| 10 | P0 y P1. Exponer interfaz Angular. | Inicio, radicación, análisis, aprobación y seguimiento. | 4, 6, 7, 9 | Recorrido principal e inicio por rol y módulo. P1 completa vista previa y pantallas auxiliares. | Flujo por los tres roles y opciones ocultas. |
| 11 | P0. Integrar y verificar entrega. | JUnit, PostgreSQL, API, UI. | P0 anteriores y P1 implementadas | Recorrido reproducible desde una base vacía. | Pruebas unitarias, integración y guion de demo. |
| 12 | P0. Completar README. | Instalación, credenciales, guion y respuestas §9. | Contratos y comandos estables | Un evaluador arranca con Docker y Java 25 y repite el flujo. | Ensayo siguiendo únicamente el README. |

Ejecutar 1–4, 6–7 y 9 para tener primero el recorrido por API. Las etapas 5 y 8 pueden completarse después del núcleo, con sus controles de seguridad. Reservar unas 2 h 50 min para el núcleo, 45 min para P1 backend, 55 min para Angular y 40 min para integración y README, unas 5 h 10 min en total. Si se reduce el tiempo, mantener integridad y seguridad, informar pendientes explícitos y privilegiar una demo reproducible. Las pruebas se incorporan en cada etapa.

## Estado del incremento de infraestructura

Las etapas 1 y 2 están implementadas y verificadas en
`feature/backend-foundation`. Tras reparar Docker Desktop,
`docker compose config --quiet` pasó y PostgreSQL 17.12 arrancó con estado
`healthy`, publicado solo en `127.0.0.1:55432`.

Con Java 25, `gradlew.bat clean build` compiló el backend y pasó las tres
pruebas de integración contra el contenedor. Spring Boot arrancó, la
consulta JDBC llegó a PostgreSQL y la prueba HTTP verificó el estado
`UP` y el bloqueo de otros endpoints de gestión.

## Estado del incremento de catálogo

La parte de catálogo de la etapa 3 está implementada y verificada en
`feature/brp-catalog`. Liquibase crea el esquema y carga el CSV original
que Gradle empaqueta como recurso. La consulta interna selecciona primero
la fila vigente por código y después aplica el filtro de disponibilidad.
En PostgreSQL 17 se verificaron los 100 registros contra el CSV,
56 disponibles, 10 sin precio y la última ingesta del 17 de noviembre de
2026. La publicación HTTP del catálogo sigue pendiente y debe aplicar
los controles de acceso de la etapa 4.

La migración 003 rechaza códigos, matrículas y placas vacíos o compuestos
solo por espacios y tabulaciones. Se verificó en PostgreSQL que matrícula
y placa mantienen los nulos permitidos por tipo y que las llaves y la
carga original conservan su comportamiento.

Las pruebas de integración usan el perfil `test` y requieren una conexión
PostgreSQL explícita a una base exclusiva. Se verificó que la ausencia de
la URL y una URL de desarrollo se rechazan antes de iniciar Liquibase.
La instancia dedicada se levanta desde la raíz con
`docker compose -f compose.test.yml up -d --wait postgres-test`, después de
configurar las variables del README. Desde `backend`, `gradlew.bat clean build`
verifica el catálogo. Las 34 pruebas pasaron con JPA y Liquibase conectados
al mismo `DataSource`. Se comprobó el rechazo previo de JNDI y la falta de
acceso del rol de pruebas a `dev` y a las demás bases del servidor de pruebas.

## Estado del incremento de identidad

La parte de identidad de las etapas 3 a 5 comprende esquema y módulos,
administrador inicial, login, JWT, consulta de sesión y administración de
usuarios con auditoría y protección transaccional del último administrador.
El aprovisionamiento rechazó la falta del hash sin dejar usuario, permiso
ni auditoría parciales. La suite de 42 pruebas pasó en PostgreSQL aislado.
Las cuentas adicionales de demostración se pueden crear mediante la API
administrativa. La exposición del catálogo y la autorización por contexto
de ofertas siguen pendientes en sus respectivos incrementos.

La revisión de identidad añadió errores de validación sanitizados,
exclusión del Bearer en rutas públicas, vigencia máxima de JWT en el
decodificador y límites de login por IP e instancia. La última validación con
`gradlew.bat clean build` en PostgreSQL de pruebas aislado registró
51 pruebas exitosas, 0 fallos, 0 errores y 0 omitidas, con los checksums
de las cinco migraciones intactos.

## Etapa 6 — Radicación de ofertas

La etapa 6.1 está completada. El agregado valida la
composición y calcula el descuento. El servicio persiste oferta,
participaciones y pagos en una transacción. La migración 006 conserva la
referencia a la versión BRP por identificador técnico y genera el número
de radicación mediante una secuencia.

`gradlew.bat clean build` pasó con PostgreSQL 18.1 en una instancia temporal
aislada. Se ejecutaron 89 pruebas, incluidas 24 unitarias y 14 de integración
nuevas, con 0 fallos, 0 errores y 0 omitidas.

La etapa 6.2 está completada. Expone radicación, vista previa y consulta propia por API.
Requiere un radicador activo con el módulo Radicación vigente. Las
consultas filtran por propietario en PostgreSQL. La vista previa comparte
el cálculo de dominio con el alta, que vuelve a consultar el catálogo.
El contrato está en el [README](../README.md#api-de-radicación).

El cierre de las etapas 6.1 y 6.2 cubre RF-02, la parte backend de RF-03,
RT-03 y la autorización y consulta propia de RS-02 y RS-03. En PostgreSQL
17.12 aislado mediante el Compose de pruebas, `gradlew.bat clean build`
registró 110 pruebas exitosas, con 0 fallos, 0 errores y 0 omitidas.
La versión se comprobó en el servidor con `SHOW server_version_num`
(`170012`). Se seleccionó `postgres:17.12` con una sobreescritura temporal
de Compose para la prueba de cierre.

Análisis y aprobación, gestión de adjuntos y frontend se mantienen en
sus etapas del plan.

## Etapa 7 — Ciclo de vida de ofertas

Implementada y verificada. Las bandejas de análisis y aprobación y sus
seis transiciones cubren RF-04, RS-04 y el control de concurrencia de RN-14.
La migración 007 incorpora el analista y las fechas del flujo. La devolución
conserva la asignación original. Cada comando exige la versión que vio el
usuario y PostgreSQL protege la escritura mediante el versionado optimista
de JPA. Una versión obsoleta o una transición inválida devuelve `409`.

`gradlew.bat clean build` pasó con PostgreSQL 17.12 aislado mediante Docker
Compose (`server_version_num = 170012`). Resultado de la suite completa:
118 pruebas exitosas, incluidas 2 unitarias y 6 de integración nuevas, con
0 fallos, 0 errores y 0 omitidas. Se comprobó una toma concurrente por HTTP
y el rechazo de una escritura sobre una versión leída por dos transacciones.

## Etapa 8 — Gestión de adjuntos

Implementada y verificada. La migración 008 guarda metadatos de adjuntos
en PostgreSQL con una FK a la oferta. La API permite carga múltiple,
listado y descarga por UUID con autorización sobre la oferta. Los archivos
se guardan en disco privado configurable. Una reversión de la transacción
retira los archivos nuevos y conserva la oferta. El contrato y los
límites de carga están documentados en el [README](../README.md#api-de-adjuntos).

`gradlew.bat clean build` pasó en PostgreSQL 17.12 aislado
(`server_version_num = 170012`). Se ejecutaron 139 pruebas, incluidas
9 unitarias y 12 de integración nuevas, con 0 fallos, 0 errores y
0 omitidas. Se verificaron la reversión del lote y el registro por UUID
cuando falla la compensación. La regresión de privacidad fuerza un
error de inserción y comprueba que se conserva el HTTP 500, se retiran
los archivos nuevos y los logs no incluyen el nombre ni los identificadores
de la oferta.

## Etapa 9 — Seguimiento de ofertas

Primera entrega implementada y verificada. El listado y el detalle
requieren el módulo Seguimiento y aplican el alcance en PostgreSQL.
La proyección de lectura conserva el descuento histórico y las fechas
del flujo. El [README](../README.md#api-de-seguimiento-de-ofertas)
documenta el contrato.

`gradlew.bat clean build` pasó en PostgreSQL 17.12 aislado
(`server_version_num = 170012`). Pasaron 147 pruebas, incluidas 8 nuevas
de integración, con 0 fallos, 0 errores y 0 omitidas.

La segunda entrega expone el catálogo vigente y la frescura global bajo
el módulo Seguimiento. Reutiliza la consulta de última versión por código
y restringe al radicador a las versiones vigentes disponibles. La API de
frescura devuelve `null` cuando no hay ingestas.

La suite completa pasó con PostgreSQL 17.12 aislado mediante
`gradlew.bat clean build`. Se ejecutaron 153 pruebas, incluidas seis nuevas
de integración para catálogo y frescura, con 0 fallos, 0 errores y 0
omitidas.

### Etapa 9.1 — Catálogo de selección para radicación

`GET /api/offers/catalog` permite al radicador con `RADICACION` consultar
los BRP vigentes disponibles. Reutiliza la consulta del catálogo existente.
La pestaña de seguimiento conserva `SEGUIMIENTO` como permiso independiente.

`gradlew.bat clean build` pasó en PostgreSQL 17.12 aislado con 158 pruebas
exitosas, incluidas cinco nuevas del selector, y 0 fallos, 0 errores y 0
omitidas.

## Etapa 10.1 — Fundación Angular y Docker

- Base Angular 20 con compilación estricta, componentes standalone, rutas diferidas, formularios reactivos y diagnóstico de conectividad.
- Proxy de desarrollo y Nginx con el prefijo `/api`. Compose integra frontend, backend y PostgreSQL y publica únicamente el frontend en la configuración conjunta.
- Verificación local en Windows con Node.js 20.19.6. El proyecto fija Angular CLI y el builder en 20.3.43 tras revisar las alertas del parche 20.3.26 instalado globalmente. `npm audit --omit=dev` reportó cero alertas. Quedan 16 alertas moderadas en dependencias de desarrollo, fuera de la imagen de ejecución de Nginx.
- Las compilaciones de producción y desarrollo finalizaron correctamente. El proxy local sirvió `/diagnostico`, obtuvo `UP` de Spring Boot con PostgreSQL 17.12 aislado y recibió `401` al consultar `/api/auth/me` sin credenciales. Chrome comprobó el estado disponible en la pantalla de diagnóstico con el CLI local y con Nginx.
- La imagen de Angular se construyó con `npm ci` y Nginx sirvió `/diagnostico`. `docker compose ps` confirmó que solo el frontend publicó un puerto. Esta comprobación usó una imagen temporal de Spring Boot con el JAR compilado en Windows. El build estándar de la imagen del backend quedó pendiente tras un fallo TLS en Maven Central y dos reintentos interrumpidos por timeout al descargar Gradle.
- La autenticación y las pantallas de negocio corresponden a entregas posteriores de la fase 10.

## Etapa 10.2 — Autenticación, sesión y navegación

- Inspección backend realizada. El [contrato para Angular](../docs/frontend-auth-contract.md) describe login, usuario autenticado, permisos, expiración y manejo HTTP.
- Angular implementa login, recuperación de sesión con `GET /api/auth/me`, cierre local, expiración, interceptor y navegación protegida por rol y módulo. Las rutas de módulos muestran su estado pendiente de implementación.
- Compilación de producción y prueba en Chrome de escritorio con Spring Boot y PostgreSQL aislado. Se comprobaron el acceso directo sin sesión, las credenciales inválidas, el menú de administrador, la recuperación al recargar, el acceso permitido y denegado, el cierre de sesión y el rechazo de tokens inválidos o expirados. El formulario se verificó sin desbordamiento a 375 px.

## Hallazgos de auditoría pendientes

- Etapa 3: concretar normalización en la migración, referencia a la ingesta
  y prueba de deduplicación con una última fila no disponible.
- Etapas 4 y 5: cerrar la matriz general por operación, módulo y contexto
  y revisar el bloqueo del último administrador.
- Etapa 6, deuda aceptada para el cierre del proyecto: rechazar caracteres
  NUL en entradas de radicación y cubrir el rollback ante un fallo intermedio
  durante la escritura del agregado.
- Etapa 8: la validación identifica el formato mediante firma. Quedan
  como deuda no bloqueante la verificación automática de que el directorio
  no esté expuesto públicamente y las pruebas de un resultado indeterminado
  durante el commit. Las cuotas acumuladas, la eliminación y la retención
  quedan pendientes de definición.
- Etapas 9 a 12: comprobar los cuatro roles, ambas pestañas, frescura,
  acción de ofertar oculta, administración y vista previa. Verificar
  arranque desde cero y contenido del README, incluidas las ocho respuestas.
