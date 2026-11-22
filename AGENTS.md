# Instrucciones del repositorio BRP

- Trabaja y documenta en español. Usa nombres descriptivos en inglés para ramas y código. Sigue [el flujo Git](docs/git-workflow.md).
- Mantén un monolito modular Java/Spring Boot. Organiza por catálogo, ofertas e identidad y permisos. Los controladores adaptan HTTP, los servicios coordinan los casos de uso y el dominio concentra las invariantes. Angular consume la API.
- Consulta [requisitos](docs/requirements.md), [reglas](docs/business-rules.md) y [supuestos](docs/assumptions.md) antes de diseñar. Antes de cambiar una regla, identifica el impacto en datos, permisos, API, UI y pruebas. Registra la decisión y actualiza la trazabilidad.
- Autoriza en servidor por rol, módulo y contexto de recurso. Usa BCrypt, JWT con expiración y configuración externa para secretos. Mantén los adjuntos fuera de rutas públicas y los datos sensibles fuera de Git y de los logs.
- Usa BigDecimal para importes y porcentajes. Guarda las operaciones críticas en transacciones y protege las transiciones con versionado optimista.
- Prueba las reglas con JUnit y comprueba migraciones, persistencia, concurrencia y autorización sobre PostgreSQL cuando el cambio las afecte. Antes de cerrar, revisa el diff, el alcance, los resultados de las pruebas pertinentes y la ausencia de secretos y artefactos generados.
- Analiza, implementa por incrementos, verifica y documenta. Actualiza siempre el [plan](plans/implementation-plan.md) con el estado real y registra lo que quede pendiente. Conserva el README como guía final para la entrega.
