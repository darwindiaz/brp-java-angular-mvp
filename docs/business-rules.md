# Reglas de negocio BRP

Los identificadores RN-01 a RN-13 provienen del análisis inicial. RN-14 y RN-15 amplían la trazabilidad de concurrencia y adjuntos. Las decisiones aprobadas se identifican en [requisitos](requirements.md).

| ID | Regla | Garantía |
|---|---|---|
| RN-01 | Catálogo de solo lectura para usuarios. Tipos: apartamento, casa, local, lote, vehículo y maquinaria. Estados: disponible, sin precio, pendiente técnico, en trámite de venta, saneamiento y dado de baja. | API de consulta y valores admitidos en persistencia. |
| RN-02 | Sin fecha de precio, normalizar precio a nulo y estado a SIN_PRECIO. Precio y fecha coexisten o ambos son nulos. | Normalización en carga y CHECK en PostgreSQL. Validar precio positivo cuando exista para permitir el cálculo. |
| RN-03 | Inmueble con matrícula, vehículo con placa y maquinaria sin ambas. | Validación de carga y CHECK por tipo. |
| RN-04 | Elegir última ingesta por código antes de aplicar disponibilidad. Fecha global igual a la máxima de todas las ingestas. | Consulta en servidor y unicidad código–fecha de ingesta. |
| RN-05 | Solo radicar sobre un BRP vigente DISPONIBLE. Varias ofertas por BRP, sin exclusividad ni cambio automático de estado del bien. | Servicio consulta disponibilidad y captura la referencia en la transacción. FK de oferta al bien o ingesta de referencia. |
| RN-06 | Hasta cinco oferentes por oferta y seis pagos por oferente. Identificación CC o NIT, nombre o razón social y contacto opcional. | Agregado valida límites. FK y columnas obligatorias garantizan pertenencia. |
| RN-07 | Pago de contado o préstamo, con fecha y valor positivo. Financiadora opcional solo en préstamo. | Bean Validation, servicio y CHECK de valor y compatibilidad. |
| RN-08 | Participaciones suman 100 %. Los pagos de cada oferente igualan su parte del total. El total suma todos los pagos. | Agregado y transacción. Las sumas entre registros se verifican en aplicación. |
| RN-09 | Descuento = (precio histórico − total) / precio histórico × 100. Cero para igualdad, negativo para sobreprecio. Nuevas ingestas conservan el valor histórico. | Cálculo único en dominio, reutilizado en vista previa, con referencia persistida. |
| RN-10 | Nace RADICADO. APROBADO y RECHAZADO son finales. DESISTIDO existe sin transiciones. | Política central y estado persistido. |
| RN-11 | Al tomar, asignar analista. Devolver al mismo analista, actualizar fecha de devolución y fijar finalización al cerrar. | Servicio guarda asignación, fechas y estado en una transacción. |
| RN-12 | Rol, módulo e identidad aplican conjuntamente. El radicador no analiza su oferta y el analista no la aprueba. El radicador solo consulta sus ofertas y BRP disponibles, los demás roles consultan todo con permiso de módulo. | @PreAuthorize, política contextual y filtros en consultas, detalle y adjuntos. |
| RN-13 | Administración asigna permisos por persona. Impedir que el actor pierda su acceso administrativo por retiro de Administración, cambio de rol o desactivación, y conservar al menos un administrador activo con ese módulo. Se permite retirar otros módulos propios. Auditar actor, destinatario, módulo, acción, instante y origen en cambios efectivos. | Unicidad usuario–módulo, FK, serialización mediante la fila de bloqueo y auditoría en la misma transacción. |
| RN-14 | Dos operaciones incompatibles sobre una oferta no pueden confirmarse sobre la misma versión. | @Version y traducción de conflicto a 409. |
| RN-15 | Varios PDF, JPG, JPEG o PNG por oferta. Persistir nombre, ruta local y fecha. Acceso mediante backend según permisos de la oferta. | Lista de formatos, validación de contenido, ruta generada por servidor, FK y compensación si falla la persistencia. |

## Política monetaria provisional

Usar BigDecimal. Propuesta para la demo: montos con dos decimales y participaciones con cuatro. Admitir valores representables en esas escalas sin pérdida. La suma de participaciones se compara con 100 por valor numérico. Multiplicar total por participación y dividir por 100 de forma exacta. Si el resultado exige una fracción inferior a la unidad monetaria admitida, devolver 400 con el detalle y solicitar ajustar la composición. Aplicar HALF_UP a cuatro decimales para persistencia y a dos para visualización. Determinar si hay sobreprecio comparando el total con el avalúo histórico, de modo que la etiqueta siga siendo correcta aunque el porcentaje mostrado redondee a cero. Estas escalas quedan sujetas a confirmación de moneda y residual.

## Transiciones

| Origen | Acción | Destino | Rol | Efecto |
|---|---|---|---|---|
| Alta | Radicar | RADICADO | RADICADOR | Registra autor, precio de referencia y creación. |
| RADICADO | Tomar | EN_PROCESO | ANALISTA | Asigna al actor, que debe ser distinto del radicador. |
| EN_PROCESO | Escalar | ESCALADO | ANALISTA | Guarda fecha de escalamiento. |
| EN_PROCESO | Rechazar | RECHAZADO | ANALISTA | Guarda fecha de finalización. |
| ESCALADO | Aprobar | APROBADO | JEFE | Guarda finalización. El jefe debe ser distinto del analista. |
| ESCALADO | Rechazar | RECHAZADO | JEFE | Guarda finalización. |
| ESCALADO | Devolver | EN_PROCESO | JEFE | Mantiene al mismo analista y actualiza fecha de devolución. |

Cualquier jefe autorizado puede atender una escalada. Se propone que solo el analista asignado pueda escalar o rechazar en análisis y que cada cambio registre modificación. Para un actor autorizado, una transición no listada da 409. La falta de permiso da 403. Campos obligatorios, valores positivos, longitudes y formatos se validan en la entrada. Las invariantes entre objetos se validan dentro de la transacción.
