# Arquitectura del MVP BRP

La arquitectura aprobada es un monolito modular Java 25 y Spring Boot con PostgreSQL en Docker Compose. El frontend Angular se incorpora después de verificar el flujo por API. Una aplicación y una base de datos permiten transacciones ACID sobre cada cambio crítico sin coordinación distribuida. [ADR-001](decisions/ADR-001.md)

## Organización

| Capacidad | Responsabilidad |
|---|---|
| Catálogo | Ingesta normalizada, consulta de la última versión por código, disponibilidad y frescura. |
| Ofertas | Radicación, composición, descuento, flujo, adjuntos y consultas de seguimiento. |
| Identidad y permisos | Login, usuarios, roles, módulos, capacidades y auditoría. |

Dentro de cada capacidad, presentación traduce HTTP y valida DTO. Aplicación coordina autorización contextual, transacciones y consultas. Dominio valida composición y transiciones sin depender de controladores. Infraestructura implementa JPA, migraciones, JWT y disco. Se favorecen dependencias explícitas entre capacidades y se extraen interfaces donde exista una frontera concreta, como almacenamiento de archivos.

En Angular, organizar por inicio, radicación, análisis, aprobación, seguimiento y administración. Los guards y el menú consumen las capacidades de sesión. Seguimiento ofrece las pestañas de ofertas y catálogo. El formulario pide al backend la vista previa del descuento. Construir Angular en Docker para cumplir el arranque sin Node local.

## Modelo conceptual

- Un código BRP puede tener múltiples registros de ingesta. Cada oferta corresponde a un BRP y conserva el precio de referencia tomado al radicar. Un BRP admite varias ofertas. [ADR-004](decisions/ADR-004.md)
- Una oferta tiene un radicador y cero o un analista asignado. Contiene hasta cinco participaciones con los datos del oferente y hasta seis pagos por participación. Los datos registrados conservan su contexto histórico.
- Una oferta puede tener varios adjuntos. Cada adjunto pertenece a una oferta y guarda metadatos. El archivo vive en disco configurable.
- Usuario y módulo se relacionan mediante un permiso individual único. El evento de concesión o retiro conserva actor, destinatario, módulo, acción y fecha. La cardinalidad de roles queda en [supuestos](assumptions.md).

## Flujo y persistencia

1. Identidad valida BCrypt y emite un JWT con expiración. El backend verifica el token y resuelve capacidades vigentes.
2. Radicación consulta el catálogo vigente, verifica contexto y composición y persiste la oferta completa con el precio histórico.
3. Análisis y aprobación ejecutan comandos que pasan por una política única. Estado, asignación y fechas se confirman juntos bajo @Version.
4. Seguimiento consulta la oferta y el catálogo con filtros de servidor. Administración confirma el permiso y su auditoría en una transacción.
5. Adjuntos valida acceso y contenido, usa rutas controladas y compensa fallos entre disco y base. Descarga por identificador mediante backend. [ADR-003](decisions/ADR-003.md)

Liquibase versiona esquema, datos de catálogo, módulos y usuarios de prueba. El administrador inicial se siembra con hash BCrypt. La ingesta aplica normalización antes de persistir restricciones de PostgreSQL. No se agregan campos de auditoría ni @Version a BRP. La autorización de recursos se comprueba con datos vigentes. [ADR-002](decisions/ADR-002.md)

## Errores y pruebas

La API devuelve un formato común con código, mensaje y errores de campo cuando apliquen. El manejador de errores MVC y los manejadores de seguridad comparten ese formato. 400 para entradas inválidas, 403 para permisos, 404 para recursos inexistentes y 409 para estado o versión en conflicto. Se propone 401 para autenticación inválida o ausente.

JUnit cubre composición, descuento y transiciones. PostgreSQL verifica CHECK, deduplicación, atomicidad, concurrencia y auditoría. Pruebas de API cubren rol, módulo y acceso a registros ajenos. El guion final comprueba el recorrido con las cuentas de prueba y la navegación de Angular.
