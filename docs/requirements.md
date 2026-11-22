# Requisitos BRP

Fuente primaria: [enunciado](source/prueba_tecnica_senior.docx) y [CSV](source/brp_ejemplo.csv). Se conservan los identificadores del [análisis inicial](analisis-funcional-y-arquitectonico.md).

| ID | Requisito explícito | Fuente |
|---|---|---|
| RF-01 | Catálogo de solo lectura, última ingesta por código, fecha global por API y en encabezado. | §2, §7 |
| RF-02 | Radicar sobre BRP disponible con oferentes, participaciones y pagos válidos. | §3, §6 |
| RF-03 | Backend calcula total y descuento, Angular muestra la vista previa y distingue sobreprecio. | §3, §6 |
| RF-04 | Toma, escalamiento, aprobación, devolución y dos rutas de rechazo, con fechas. | §4, §6 |
| RF-05 | Seguimiento de ofertas con BRP, monto, descuento, estado, analista y fechas. Segunda pestaña para catálogo. | §7 |
| RF-06 | Administrar usuarios, roles y módulos por persona, sin grupos. | §5 |
| RF-07 | Adjuntos por oferta con nombre, ruta local y fecha de carga. | §3, entrega |
| RF-08 | Inicio posterior al login con opciones habilitadas y demás opciones ocultas. | §5, §8 |
| RNF-01 | Demo comprensible sin capacitación, prioridad backend y frontend Angular funcional. | Contexto, §1 |
| RNF-02 | Errores homogéneos: 400, 403, 404 y 409. Control de concurrencia. | §6 |
| RT-01 | Java y Angular. PostgreSQL con Docker Compose. Arranque con Docker y Java 25, sin otras instalaciones. | §1, entrega |
| RT-02 | Migraciones versionadas para esquema y datos, con 100 BRP y administrador inicial. CHECK del catálogo. | §1, §2, §5 |
| RT-03 | Bean Validation, servicios transaccionales y @Version en oferta. | §6 |
| RT-04 | Disco local configurable y metadatos en PostgreSQL. | §1, §3, entrega |
| RS-01 | Login propio con BCrypt y JWT con expiración, sin Active Directory. Administrador inicial con hash. | §5 |
| RS-02 | @PreAuthorize por endpoint protegido. Autorización en servidor y guards de Angular para navegación. | §5, §8 |
| RS-03 | Radicador ve sus ofertas y BRP disponibles. Los demás roles tienen alcance global de consulta. Filtros en servidor. | §6, §7 |
| RS-04 | El radicador no analiza su oferta y el analista no la aprueba. | §5 |
| RS-05 | Proteger permisos propios del administrador y al último administrador. Auditar concesiones y retiros de módulos. | §5 |
| RE-01 | Git y README con arranque, migraciones, modelado, flujo, seguridad, validaciones, inicio y configuración de adjuntos. | Entrega |
| RE-02 | Credenciales de prueba de los cuatro roles y catálogo completo al arrancar. | Entrega |
| RE-03 | Guion completo, ejemplos de autenticación y API, JUnit y comando de ejecución. | Entrega |
| RE-04 | Ocho respuestas de la §9 en el README. | §9, entrega |

## Decisiones aprobadas

- DA-01. Monolito modular Java/Spring Boot.
- DA-02. Varias ofertas por BRP, sin exclusividad ni mutación automática del catálogo al aprobar.
- DA-03. BigDecimal para montos, participaciones y descuentos.
- DA-04. Autorización por rol, módulo habilitado y contexto.
- DA-05. Múltiples adjuntos PDF, JPG, JPEG o PNG en disco local y acceso autorizado vía backend.
- DA-06. Precio histórico de referencia al radicar y descuento estable ante nuevas ingestas.

La formalización fija Spring Boot, Spring Data JPA y Spring Security. Se selecciona Java 25 para ejecución reproducible. Liquibase y Gradle Wrapper siguen la recomendación del enunciado. Las políticas provisionales y los asuntos abiertos están en [supuestos](assumptions.md).
