# Supuestos y decisiones BRP

## Decisiones aprobadas

DA-01 a DA-06 están en [requisitos](requirements.md). Comprenden monolito modular, ofertas múltiples sin cambio automático del BRP, BigDecimal, autorización contextual, adjuntos privados en disco y precio histórico. Estas decisiones sustituyen las propuestas pendientes del informe inicial.

## Supuestos técnicos de trabajo

| ID | Supuesto provisional | Motivo |
|---|---|---|
| ST-01 | Un rol por cuenta, confirmado para identidad. La visibilidad resulta de rol y módulos habilitados. El catálogo de selección usa Radicación y la pestaña de catálogo usa Seguimiento. | Compatibilizar las preguntas 5, 7 y 8 con una política común. |
| ST-02 | Solo el analista asignado escala o rechaza en análisis. Se muestra la fecha más reciente de escalamiento o devolución. | Dar continuidad a la asignación y al seguimiento. |
| ST-03 | Al menos un oferente y un pago por oferente, con participación positiva. La escala y el residual se tratan según la propuesta de [reglas](business-rules.md). | Mantener una oferta positiva y cuadrada. |
| ST-04 | Misma ingesta por código y fecha: repetición idéntica idempotente y conflicto si el contenido difiere. | Evitar que el orden del archivo determine el registro vigente. |
| ST-05 | Administrador efectivo significa usuario activo con rol Administrador y módulo Administración. Se prohíbe perder el acceso administrativo propio por retiro del módulo, cambio de rol o desactivación. Los demás módulos propios se pueden retirar. | Mantener acceso operativo administrativo. |
| ST-06 | JWT de acceso de 15 minutos, sin refresh ni revocación individual en la demo. Permisos y estado del usuario se verifican en cada solicitud. | Reducir complejidad y aplicar revocaciones de acceso administrativo al siguiente uso. |
| ST-07 | El radicador carga adjuntos al radicar. Lectura bajo alcance de oferta. Límite provisional configurable de 10 MiB por archivo. | Acotar operaciones y consumo de disco hasta confirmar alcance. |

## Preguntas pendientes de negocio

- ¿Qué moneda, escalas y tratamiento de residuos acepta negocio? ¿Se permiten participaciones repetidas para la misma identificación?
- ¿Qué módulo protege la consulta de frescura del catálogo?
- ¿Puede otro analista sustituir al asignado? ¿Se requieren motivo de rechazo e historial completo de cada escalamiento y devolución?
- ¿Hay carga de adjuntos después de radicar, límites acumulados, eliminación o retención?
- ¿Puede coexistir más de una fila diferente para el mismo código y día? Si ocurre, ¿cuál fuente decide el empate?

## Limitaciones conocidas

El CSV tiene 100 códigos distintos y dos fechas de ingesta. La deduplicación entre ingestas necesita datos adicionales de prueba. La demo usa disco local, una instancia operativa y fechas resumidas de seguimiento. El historial completo y la gestión avanzada de sesiones quedan fuera del alcance propuesto.
