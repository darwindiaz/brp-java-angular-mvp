# Plan incremental del MVP BRP

Estado inicial: diseño documentado. Las etapas están pendientes. P0 permite demostrar el recorrido seguro y P1 completa capacidades explícitas adicionales. [Requisitos](../docs/requirements.md) · [Reglas](../docs/business-rules.md)

| Etapa | Prioridad y objetivo | Componentes | Depende de | Aceptación | Pruebas |
|---|---|---|---|---|---|
| 1 | P0. Arrancar PostgreSQL. | Docker Compose, configuración. | — | DB disponible desde Docker con configuración externa. | Arranque desde entorno limpio y conexión. |
| 2 | P0. Iniciar backend Java 25. | Spring Boot, Gradle Wrapper, DTO y errores. | 1 | Aplicación ejecutable sin Gradle instalado y errores homogéneos. | Build limpio y prueba de arranque. |
| 3 | P0. Migrar esquema y catálogo. | Liquibase, catálogo, usuarios y módulos semilla. | 2 | 100 códigos, 56 disponibles normalizados, frescura 17/11/2026. | CHECK, deduplicación y filtro sobre la última ingesta. |
| 4 | P0. Proteger la API. | BCrypt, JWT, roles, módulos y capacidades. | 3 | Cuatro cuentas acceden según función y permiso. | Login inválido, expiración, 403 y alcance de consulta. |
| 5 | P1. Administrar permisos. | Usuarios, roles, módulos y auditoría. | 4 | Cambios auditados y al menos un administrador efectivo. | Retiro propio, auditoría atómica y cambios concurrentes. |
| 6 | P0. Radicar. | Oferta, oferentes, pagos y descuento. | 3, 4 | Oferta válida registrada en una transacción con avalúo histórico. | Límites, participaciones, residual, rollback y sobreprecio. |
| 7 | P0. Decidir ofertas. | Política de transición y bandejas API. | 6 | Seis transiciones, asignación, fechas y respuestas coherentes. | Rechazos, devolución, separación y conflicto 409 de toma. |
| 8 | P1. Gestionar adjuntos. | Disco local, metadatos y endpoints protegidos. | 4, 6 | Carga múltiple y lectura autorizada sin publicar rutas físicas. | Formato inválido, acceso ajeno y compensación de fallos. |
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
2026. La siembra de usuarios y módulos de la etapa 3 y las etapas
posteriores siguen pendientes. La publicación HTTP requiere los
controles de acceso de la etapa 4.

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

## Hallazgos de auditoría pendientes

- Etapa 3: concretar normalización en la migración, referencia a la ingesta
  y prueba de deduplicación con una última fila no disponible.
- Etapas 4, 5 y 7: cerrar la matriz por operación, módulo y contexto, el
  bloqueo del último administrador y las fechas de acciones repetidas.
- Etapas 6 y 8: confirmar precisión y residual, límites de adjuntos y el
  comportamiento de radicación, compensación y reintento ante fallos.
- Etapas 9 a 12: comprobar los cuatro roles, ambas pestañas, frescura,
  acción de ofertar oculta, administración y vista previa. Verificar
  arranque desde cero y contenido del README, incluidas las ocho respuestas.
