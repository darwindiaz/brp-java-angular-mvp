# Informe funcional y arquitectónico del MVP BRP

> Análisis inicial. Las decisiones aprobadas y los supuestos vigentes se mantienen en [requisitos](requirements.md) y [supuestos](assumptions.md).

## 1. Resumen ejecutivo

Recomiendo un **monolito modular en Java 25 y Spring Boot, con PostgreSQL en Docker Compose y un frontend Angular funcional**. Una sola aplicación backend permite guardar la oferta, sus oferentes y sus pagos en una transacción, y coordinar los cambios de estado y la auditoría de permisos sin infraestructura adicional. Angular se construye después de comprobar el recorrido por API.

El objetivo de las 4 a 6 horas es demostrar un flujo seguro de punta a punta: autenticarse, consultar un BRP disponible, radicar una oferta con oferentes y pagos, tomarla, escalarla, aprobarla y consultar su seguimiento. La devolución, el rechazo, la separación de funciones y el conflicto por concurrencia deben quedar comprobados en el backend aunque la interfaz sea mínima.

Se inspeccionaron los dos archivos de `docs/source/`, incluido el diagrama incrustado en Word. El CSV contiene **100 códigos distintos** y dos fechas de ingesta: 70 filas del 10 y 30 del 17 de noviembre de 2026. La muestra tiene 10 filas sin fecha ni precio. Cinco requieren corregir su estado a `SIN_PRECIO`, tres de ellas venían como `DISPONIBLE`. Tras normalizar quedan **56 BRP disponibles** y la fecha global de actualización es **17 de noviembre de 2026**. La muestra carece de códigos repetidos, así que el comportamiento entre ingestas requiere un caso de prueba adicional. [Enunciado de la prueba](source/prueba_tecnica_senior.docx) · [Catálogo de ejemplo](source/brp_ejemplo.csv)

En el informe, **explícito** identifica lo exigido por la prueba, **inferencia** identifica una consecuencia técnica o funcional de sus reglas, **supuesto** identifica una interpretación provisional y **pendiente** identifica una decisión que requiere aprobación. El repositorio se mantuvo en solo lectura durante el análisis.

## 2. Contexto del negocio

La Gerencia de Mejoramiento de Originación y Cobranza necesita que sus usuarios internos puedan registrar, evaluar y decidir ofertas sobre Bienes Recibidos en Pago. El proceso vincula un bien del catálogo, las personas que participan en la oferta, sus pagos propuestos y los responsables internos de cada decisión. La demo está dirigida a usuarios que verán el sistema por primera vez.

| Actor | Responsabilidad explícita |
|---|---|
| Radicador | Registra la oferta a nombre de los oferentes y consulta las que radicó. |
| Analista | Toma una oferta radicada y decide si la escala o la rechaza. |
| Jefe | Atiende cualquier oferta escalada. Puede aprobarla, rechazarla o devolverla al mismo analista. |
| Administrador | Crea usuarios, asigna roles y módulos, y consulta ofertas y catálogo dentro de su alcance de acceso. |
| Oferente | Persona o empresa cuyos datos, participación y pagos quedan registrados en la oferta. Carece de cuenta de acceso. |

El radicador selecciona un BRP disponible y registra los oferentes, participaciones y pagos al radicar. El backend valida la composición y calcula el valor total y el descuento. El analista toma la oferta y la procesa. Si la escala, un jefe decide. Si la devuelve, el mismo analista retoma el análisis y puede escalarla otra vez o rechazarla. El seguimiento muestra el resultado y las fechas según el alcance del usuario.

## 3. Módulos funcionales

| Módulo | Función |
|---|---|
| Radicación | Seleccionar un BRP ofertable y registrar la oferta con sus oferentes y pagos. |
| Análisis | Consultar la bandeja, tomar una oferta, escalarla o rechazarla. |
| Aprobación | Consultar las escaladas, aprobar, rechazar o devolver. |
| Seguimiento | Mostrar dos pestañas, una de ofertas con trazabilidad y otra de catálogo de solo lectura, ambas filtradas en servidor. |
| Administración | Crear usuarios, asignar roles y módulos, y registrar los cambios de permisos de módulo. |

Los primeros cuatro son los módulos del proceso BRP. Administración completa el mapa de permisos. Inicio y autenticación son capacidades transversales. El catálogo tiene consulta propia y se consume también desde radicación. Los adjuntos pertenecen a la oferta.

## 4. Requisitos trazables

Los números de pregunta remiten al documento original. Las opciones recomendadas del stack se distinguen de las restricciones de cumplimiento.

| ID | Clase | Requisito explícito | Fuente |
|---|---|---|---|
| RF-01 | Funcional | Consultar el catálogo de solo lectura, mostrar una entrada por código con su ingesta más reciente y exponer la fecha global de actualización en un endpoint y en el encabezado de Angular. | 2, 7 |
| RF-02 | Funcional | Radicar sobre un BRP disponible, con oferentes, participaciones y pagos. | 3, 6 |
| RF-03 | Funcional | Calcular en servidor el total y el descuento. Mostrar el descuento durante el armado y el resultado negativo como sobreprecio. | 3, 6 |
| RF-04 | Funcional | Ejecutar la toma, el escalamiento, el rechazo, la devolución y la aprobación con las fechas correspondientes. | 4, 6 |
| RF-05 | Funcional | Consultar seguimiento con BRP, valor, descuento, estado, analista y fechas, y una segunda pestaña de catálogo. | 7 |
| RF-06 | Funcional | Crear usuarios y asignar roles y módulos por persona, sin grupos. | 5 |
| RF-07 | Funcional | Asociar adjuntos a una oferta y registrar nombre, ruta local y fecha de carga. | 3, Entrega |
| RF-08 | Funcional | Mostrar en inicio las opciones habilitadas para el usuario y ocultar las demás. | 5, 8 |
| RNF-01 | No funcional | La demo debe entenderse sin capacitación previa. El backend tiene mayor peso y el frontend puede ser sencillo. | Contexto, 1 |
| RNF-02 | No funcional | Proteger cambios concurrentes en oferta y devolver errores con un formato común: `400`, `403`, `404` y `409` según el caso. | 6 |
| RT-01 | Técnica | Backend Java, frontend Angular y PostgreSQL levantado con Docker Compose. La entrega debe arrancar con Docker y Java 25, sin instalaciones adicionales. | Contexto, 1, Entrega |
| RT-02 | Técnica | Cargar catálogo y administrador inicial mediante migraciones. Usar los `CHECK` indicados para el catálogo y omitir campos de auditoría y versionado optimista en BRP. | 1, 2, 5 |
| RT-03 | Técnica | Validar en backend con Bean Validation para entradas simples, servicio transaccional para negocio y `@Version` en Oferta. | 6 |
| RT-04 | Técnica | Guardar los archivos en disco local con carpeta configurable y los metadatos en base de datos. | 1, 3, Entrega |
| RS-01 | Seguridad | Login propio con usuarios en base, BCrypt y JWT con expiración, sin Active Directory. El administrador inicial nace con hash. | 1, 5 |
| RS-02 | Seguridad | Autorizar en backend con `@PreAuthorize` por endpoint, combinando rol y módulo. Angular usa guards para navegación. | 5, 8 |
| RS-03 | Seguridad | Restringir las consultas del radicador a sus ofertas y a BRP disponibles. Los otros roles tienen alcance global de consulta. Los filtros se ejecutan en servidor. | 6, 7 |
| RS-04 | Seguridad | Quien radica no analiza su oferta y quien analiza no la aprueba. | 5 |
| RS-05 | Seguridad | Impedir que el administrador retire su propio permiso y que el sistema quede sin administradores. Auditar quién otorga o retira qué módulo, a quién y cuándo. | 5 |
| RE-01 | Entrega | Repositorio Git con commits legibles y README de arranque y explicación de la solución. | Entrega |
| RE-02 | Entrega | Administrador y cuentas de prueba para radicador, analista y jefe, más los 100 BRP cargados al arrancar. | Entrega |
| RE-03 | Entrega | Guion del flujo completo, ejemplos de autenticación y API, pruebas unitarias JUnit y comando de ejecución. No se exige cobertura mínima. | 1, Entrega |
| RE-04 | Entrega | Responder las ocho preguntas de la sección 9 dentro del README. | 9, Entrega |

La pregunta 1 recomienda Java 21 o 25, Spring Boot, Spring Data JPA, Spring Security, Angular 17 o superior, Liquibase, Gradle 8 o superior e imágenes JDK sugeridas. La entrega fija Java 25 en la máquina del evaluador. Cualquier desviación del stack recomendado debe justificarse en el README.

## 5. Reglas de negocio y validaciones

| ID | Regla explícita | Requisitos |
|---|---|---|
| RN-01 | El catálogo es de consulta. Sus tipos son apartamento, casa, local, lote, vehículo y maquinaria. Sus estados son disponible, sin precio, pendiente técnico, en trámite de venta, saneamiento y dado de baja. | RF-01, RT-02 |
| RN-02 | Precio y fecha de asignación existen juntos o ambos son nulos. Sin fecha, la carga deja precio nulo y fuerza `SIN_PRECIO`, cualquiera que sea el estado de origen. | RF-01, RT-02 |
| RN-03 | Inmuebles con matrícula, vehículos con placa y maquinaria sin ambos identificadores. Matrícula y placa son mutuamente excluyentes. | RF-01, RT-02 |
| RN-04 | La consulta conserva todos los códigos distintos y muestra por código la ingesta más reciente. La frescura global se obtiene de la mayor fecha de ingesta. | RF-01 |
| RN-05 | Solo se radica sobre un BRP `DISPONIBLE`. Angular oculta la acción para los demás estados. | RF-02 |
| RN-06 | Una oferta admite hasta cinco oferentes y cada oferente hasta seis pagos. Los oferentes se identifican por cédula o NIT, con nombre o razón social. Correo y teléfono son opcionales. | RF-02 |
| RN-07 | Los pagos son de contado o préstamo, con fecha y valor. La financiadora es opcional y solo corresponde a préstamos. | RF-02 |
| RN-08 | Las participaciones suman 100 %. Los pagos de cada oferente suman su participación del valor total. El total suma los pagos de todos los oferentes. | RF-02, RF-03 |
| RN-09 | El descuento es `(precio BRP − total) / precio BRP × 100`. Una oferta igual al avalúo da cero. Una superior produce sobreprecio y se representa con descuento negativo. | RF-03 |
| RN-10 | La oferta nace `RADICADO`. Se aplican las seis transiciones del diagrama. `APROBADO` y `RECHAZADO` son finales. `DESISTIDO` existe en el enumerado y queda fuera del recorrido. | RF-04 |
| RN-11 | Al tomar se asigna el analista. La devolución conserva al mismo analista y actualiza su fecha. El cierre registra la fecha de finalización. Cualquier jefe puede atender una escalada. | RF-04 |
| RN-12 | La autorización combina rol, permiso individual de módulo y, cuando corresponde, identidad del actor frente a la oferta. El radicador consulta únicamente sus ofertas y el catálogo disponible. | RS-02, RS-03, RS-04 |
| RN-13 | Las concesiones y retiros de módulos identifican actor, destinatario, módulo, acción y momento. La administración conserva su propio permiso y al menos un administrador. | RS-05 |

**Validaciones técnicas.** Bean Validation cubre campos obligatorios, valores positivos, formatos y longitudes. El servicio aplica las reglas que cruzan filas, roles o estado actual. PostgreSQL protege con `CHECK` el par precio-fecha y la combinación tipo-matrícula-placa. `@Version` detecta actualizaciones en competencia. El contrato de errores distingue entrada o composición inválida (`400`), permiso insuficiente (`403`), recurso inexistente (`404`) y conflicto de estado o versión (`409`). Responder `401` ante autenticación ausente o inválida es una inferencia del contrato HTTP.

## 6. Modelo de dominio conceptual

| Concepto | Responsabilidad y cardinalidad |
|---|---|
| BRP e ingesta | Un bien se reconoce por su código. Un código puede aparecer en varias ingestas y la consulta elige la más reciente. Un BRP puede estar asociado a varias ofertas en el modelo propuesto, pendiente de confirmar su exclusividad comercial. |
| Oferta | Pertenece a un BRP y registra un radicador. Tiene cero o un analista, de forma que al tomarla queda exactamente uno. Controla estado, total, descuento, fechas y composición. |
| Oferente en oferta | Pertenece a una única oferta, con un máximo de cinco por oferta. Conserva identificación, nombre, contacto y porcentaje como datos de esa participación. Recomiendo mantenerlo ligado a la oferta para preservar los datos presentados en cada radicación, sin introducir un maestro global de personas. |
| Pago | Pertenece a un oferente de esa oferta, con un máximo de seis por oferente. Registra modalidad, fecha, valor y financiadora cuando corresponda. |
| Adjunto | Una oferta puede tener cero o varios. Cada adjunto pertenece a una oferta y vincula el archivo con sus metadatos. |
| Usuario interno, rol y módulo | El rol expresa la función. Usuario y módulo tienen relación muchos a muchos mediante el permiso individual usuario–módulo. La cardinalidad de roles por usuario requiere confirmación. |
| Evento de permiso | Registra una concesión o un retiro de módulo, con usuario actor, usuario destinatario, módulo y fecha. Debe conservarse al retirar el permiso que originó el evento. |

**Invariantes del modelo.** Una oferta válida conserva sus relaciones, límites de composición, coherencia total–pagos–participaciones y consistencia entre estado, responsable y fechas. Los pagos y oferentes se guardan con la oferta como una sola unidad. El modelo evita que un estado final se reabra mediante una modificación genérica.

**Reglas del servicio.** La aplicación comprueba la disponibilidad del BRP, el acceso por rol y módulo, la identidad de quien actúa y la asignación al analista. También resuelve la aritmética monetaria con la política aprobada, y la asignación de permisos con su auditoría. La persistencia refuerza integridad referencial, unicidad del permiso usuario–módulo y consistencia del catálogo. El precio de referencia de una oferta requiere una decisión expresa para que una nueva ingesta no altere retrospectivamente el descuento.

## 7. Matriz de transiciones

Todas las acciones requieren el módulo correspondiente. La fecha de modificación se actualiza en cada cambio según la propuesta. El documento enumera esa fecha como dato de trazabilidad, pero no especifica su semántica exacta.

| Origen | Acción | Destino | Rol | Condición y trazabilidad |
|---|---|---|---|---|
| Creación | Radicar | `RADICADO` | Radicador | BRP disponible y composición válida. Registra radicador y fecha de creación. La creación precede a las seis transiciones. |
| `RADICADO` | Tomar | `EN_PROCESO` | Analista | El actor es distinto del radicador. Queda como analista asignado. |
| `EN_PROCESO` | Escalar | `ESCALADO` | Analista | Propongo exigir que sea el asignado. Registra la fecha de escalamiento. |
| `EN_PROCESO` | Rechazar | `RECHAZADO` | Analista | Propongo exigir que sea el asignado. Registra la fecha de finalización. |
| `ESCALADO` | Aprobar | `APROBADO` | Jefe | El actor es distinto del analista. Registra la fecha de finalización. |
| `ESCALADO` | Rechazar | `RECHAZADO` | Jefe | Puede atender cualquier escalada. Registra la fecha de finalización. |
| `ESCALADO` | Devolver | `EN_PROCESO` | Jefe | Conserva al analista asignado, distinto del radicador. Actualiza la fecha de devolución. |

Ejemplos de transición inválida con `409`: aprobar desde `RADICADO`, tomar una oferta que ya está `EN_PROCESO`, devolver una oferta final y ejecutar cualquier transición hacia `DESISTIDO`. Un actor sin rol, módulo o relación autorizada recibe `403`. Si dos analistas autorizados toman simultáneamente la misma oferta, solo una operación queda confirmada y la otra recibe `409`.

## 8. Matriz de roles y permisos

La tabla muestra el alcance que concede el **rol** cuando el usuario tiene el **módulo** correspondiente.

| Acción o consulta | Radicador | Analista | Jefe | Administrador |
|---|---|---|---|---|
| Radicar | Sí | — | — | — |
| Tomar `RADICADO` | — | Sí, con separación de funciones | — | — |
| Escalar o rechazar en análisis | — | Sí, asignado según propuesta | — | — |
| Aprobar, rechazar o devolver `ESCALADO` | — | — | Sí, con separación de funciones al aprobar | — |
| Seguimiento de ofertas | Propias | Todas | Todas | Todas |
| Catálogo de seguimiento | Disponibles | Completo | Completo | Completo |
| Crear usuarios y asignar roles o módulos | — | — | — | Sí |

- **Autenticación.** Login propio, BCrypt y JWT con expiración. Propongo usar el JWT para identificar al usuario y leer los permisos vigentes en servidor para que un retiro de acceso surta efecto con la siguiente solicitud.
- **Autorización por rol y módulo.** Una política compartida alimenta `@PreAuthorize` y las capacidades del usuario que consume Angular para inicio, menús y guards. Un módulo asignado por sí solo no concede acciones fuera del rol.
- **Separación de funciones.** La identidad se comprueba sobre la oferta dentro del caso de uso. La consulta directa, el detalle y el acceso a adjuntos aplican el alcance del usuario de la misma manera que los listados.
- **Auditoría de permisos.** El cambio y su evento se guardan en una transacción. Propongo serializar las operaciones que puedan dejar al sistema sin administrador efectivo, porque dos cambios concurrentes podrían superar individualmente una comprobación de conteo.

## 9. Ambigüedades, supuestos y decisiones pendientes

| Tema | Interpretación provisional para aprobación |
|---|---|
| Roles y módulos en inicio | La pregunta 8 describe opciones por rol y la 5 asigna módulos individualmente. Propongo mostrar la intersección. Radicación puede consultar BRP disponibles para seleccionar el bien. La pestaña de catálogo de seguimiento queda asociada al módulo Seguimiento. |
| Uno o varios roles | Supuesto de un rol por usuario para la demo. La separación por identidad se conserva si se amplía a varios roles o se permite cambiar de rol. |
| Administrador efectivo | Propongo considerar efectivo al usuario activo con rol `ADMINISTRADOR` y módulo Administración. Confirmar el alcance de la prohibición de quitarse «su propio permiso», especialmente si abarca cualquier módulo propio. |
| Composición mínima y repetición de identificación | La prueba fija máximos. Con total positivo y participación íntegra, propongo exigir al menos un oferente y un pago por oferente con participación positiva. Queda pendiente confirmar si una misma identificación puede repetirse dentro de una oferta. |
| Precisión y redondeo | Confirmar moneda, escala de montos, escala de porcentajes y residual. Propongo aritmética decimal exacta para montos, una política explícita para fracciones y redondeo del descuento para visualización. Los importes que no cuadren se rechazan con detalle. |
| Vista previa del descuento | La prueba exige cálculo en servidor y visualización durante el armado. Propongo una consulta de cálculo sin persistencia que reutilice la fórmula de radicación y acepte un cálculo provisional cuando haya precio y total. |
| Ofertas simultáneas por bien | El enunciado carece de reglas de exclusividad, adjudicación o cambio del catálogo al aprobar. Propongo permitir varias ofertas sobre un mismo BRP y tratar la aprobación como decisión sobre una oferta. Confirmar si solo puede existir una ganadora. |
| Histórico del catálogo | Propongo conservar el precio de referencia usado al radicar para mantener estable el descuento histórico. Para el mismo código y fecha, admitir una repetición idéntica y rechazar datos contradictorios. El CSV suministrado no resuelve ese empate. |
| Asignación y fechas | Propongo que solo el analista asignado pueda escalar o rechazar en análisis, y que el seguimiento muestre la fecha más reciente de cada acción repetible. Confirmar si negocio requiere un historial de cada vuelta, motivo de rechazo o reasignación. |
| Alcance de adjuntos | La carpeta configurable y los metadatos son explícitos. Queda por acordar quién puede cargar o descargar, en qué estados y con qué límites y formatos. Propongo carga por el radicador durante la radicación y lectura bajo el mismo alcance de la oferta. |

## 10. Propuesta arquitectónica

| Capa | Componentes y responsabilidad |
|---|---|
| Presentación | Angular con inicio, listados, formulario y acciones por estado. API REST con DTO, Bean Validation, `@PreAuthorize` y traducción uniforme de errores. Los controladores delegan la operación completa. |
| Aplicación | Casos de uso de radicación, flujo, seguimiento, catálogo y administración. Coordinan autorización contextual, transacciones, persistencia y auditoría. |
| Dominio | Oferta y su composición, cálculo de participaciones y descuento, política de transiciones y restricciones de catálogo. Las reglas se prueban con JUnit sin arrancar Angular ni el servidor HTTP. |
| Infraestructura | PostgreSQL, repositorios Spring Data JPA, migraciones Liquibase, BCrypt, JWT y almacenamiento local. Docker Compose provisiona PostgreSQL y puede construir Angular sin exigir Node instalado en el host. |

Recomiendo organizar el backend por capacidad, con límites claros entre **ofertas**, **catálogo** e **identidad y permisos**. Un único componente controla las transiciones permitidas. Los casos de uso reúnen los datos y aplican las reglas antes de guardar. JPA puede mapear directamente las entidades cuando resulte sencillo, mientras la lógica se mantiene independiente de controladores y objetos HTTP.

La radicación confirma oferta, oferentes y pagos juntos. Los cambios de estado guardan estado, asignación y fechas en la misma transacción. PostgreSQL comprueba restricciones y `@Version` evita que dos decisiones se sobrescriban. El sistema de archivos tiene un límite transaccional distinto. Para adjuntos propongo preparar el archivo en una carpeta controlada, coordinar el guardado de metadatos y compensar fallos con limpieza de archivos incompletos o huérfanos.

Una arquitectura hexagonal completa aumentaría el número de interfaces y adaptadores para esta demo. Un monolito por capas globales sería rápido de arrancar, pero dispersaría las reglas de cada capacidad a medida que creciera. La organización por capacidad conserva una sola transacción de base de datos, facilita las pruebas de dominio y concentra las responsabilidades que cambian juntas.

## 11. ADR candidatos

Son temas y recomendaciones para revisión, pendientes de aceptación.

| ADR | Problema y contexto | Alternativas | Recomendación y consecuencias | Trazabilidad |
|---|---|---|---|---|
| ADR-01 | Estructura del MVP con backend prioritario y 4–6 horas. | Capas globales, monolito modular, arquitectura hexagonal completa. | Monolito modular con servicios transaccionales. Menos piezas de despliegue y necesidad de cuidar las dependencias internas. | RNF-01, RT-01, RT-03 |
| ADR-02 | Mantener consistente el flujo con seis transiciones y escrituras concurrentes. | Decisiones repartidas por endpoint, política central, motor de workflow. | Política central y `@Version`. Regla fácil de localizar y conflictos explícitos para el usuario. | RF-04, RNF-02, RS-04 |
| ADR-03 | Preservar frescura del catálogo y precio histórico. | Sobrescribir por código o conservar las ingestas. | Conservar ingestas y elegir la última por código. Preservar el precio usado en la oferta según la decisión de negocio. Requiere definir empate e idempotencia. | RF-01, RF-03, RT-02 |
| ADR-04 | Relacionar oferentes y cuadrar montos con poco tiempo. | Maestro global con participación asociativa o datos de oferente propios de la oferta. | Oferente dependiente de la oferta y aritmética decimal. Preserva la información radicada y admite repetir datos de contacto entre ofertas. | RF-02, RF-03 |
| ADR-05 | Mantener permisos coherentes entre API e inicio y aplicar retiros durante la vigencia de un JWT. | Permisos en claims o consulta de permisos vigentes. | JWT de expiración corta para identidad, permisos vigentes en servidor y capacidades derivadas de la misma política. Cuesta una lectura adicional y simplifica el efecto de un retiro. Para la demo propongo sin refresh ni lista de revocación individual. | RF-06, RF-08, RS-01 a RS-05 |
| ADR-06 | Mantener archivos, metadatos y autorización consistentes. | Disco con descarga autorizada, archivos servidos públicamente, almacenamiento de objetos. | Disco configurable y acceso mediado por backend. Requiere compensación ante fallos y respeta el almacenamiento local pedido. | RF-07, RT-04, RS-03 |
| ADR-07 | Arrancar Angular sin Node instalado en la máquina del evaluador. | Contenedor frontend separado o compilación en contenedor y entrega estática con Spring Boot. | Compilar en contenedor y servir los estáticos desde el mismo origen si el empaquetado cabe en el plazo. Simplifica arranque y consumo de API. Docker Compose puede ofrecer un frontend separado como alternativa. | RT-01, RE-01 |

## 12. Riesgos técnicos

| Riesgo | Control que debe comprobarse |
|---|---|
| Acceso a ofertas o adjuntos mediante un ID conocido | Aplicar el alcance también en detalle y adjuntos. Probar que un radicador no accede al registro de otro. |
| Inicio incoherente con API o permisos obsoletos | Usar la misma política para capacidades de inicio y `@PreAuthorize`. Probar una retirada de módulo con el token aún vigente. |
| Dos tomas o decisiones concurrentes | Probar ambas escrituras contra el mismo estado y verificar un solo cambio confirmado y un `409`. |
| Pérdida del último administrador | Proteger el invariante en una operación serializada y auditar el permiso en la misma transacción. |
| Descuadres por precisión o actualización del avalúo | Decimales, prueba de montos por oferente y política aprobada de precio histórico. |
| Catálogo deduplicado en el orden incorrecto | Seleccionar primero la última ingesta por código y después filtrar por disponibilidad. Así una fila antigua disponible no reaparece si la última deja de estarlo. |
| Exposición de archivos, credenciales o JWT | Rutas de archivo generadas por servidor, carpeta privada, autorización por oferta y límites de carga. Credenciales documentadas solo para la demo, hash en migraciones y clave de firma configurable. |

## 13. Priorización del MVP

| Prioridad | Alcance |
|---|---|
| **P0** | Arranque reproducible, migraciones y cuentas de prueba. Login, autorización por rol y módulo, alcance de lecturas, catálogo y frescura. Radicación con oferentes, pagos, participaciones y descuento. Toma, escalamiento, aprobación, devolución y ambos rechazos en backend. Seguimiento, separación de funciones, concurrencia, formato de errores, pruebas de reglas, guion y README. Interfaz Angular mínima para el recorrido principal e inicio por capacidades. |
| **P1** | API de administración con auditoría y protección del último administrador, carga de adjuntos con carpeta configurable y controles de acceso, vista previa del descuento durante el armado y terminación de bandejas y pestañas de Angular. Son puntos explícitos de la prueba y deben quedar visibles como pendientes si el plazo impide completarlos. |
| **P2** | Pulido visual, mejoras de interacción, documentación de API exhaustiva, historial completo de eventos y mecanismos avanzados de sesión como refresh y revocación individual. |

Si la administración entra en la demo, auditoría y protección del administrador entran con ella. Si entran adjuntos, también entra su autorización. Las cuentas sembradas permiten validar el P0 desde temprano y dejan tiempo para completar la gestión de permisos con sus invariantes.

## 14. Plan por orden de dependencias

| Orden | Trabajo y verificación | Tiempo aproximado |
|---|---|---:|
| 1 | Arranque, PostgreSQL y migraciones. Cargar usuarios, módulos y CSV. Verificar 100 registros, 56 disponibles y fecha máxima del 17 de noviembre. | 40–50 min |
| 2 | Login JWT, autorización base, consulta de capacidades y filtros de catálogo. Probar usuarios autenticados, denegaciones y alcance de consulta. | 35–45 min |
| 3 | Radicación, composición y cálculo. Verificar que un fallo en cualquier participación o pago revierte la operación completa. | 45–60 min |
| 4 | Flujo, seguimiento y concurrencia. Recorrer aprobación y ejecutar casos de devolución, rechazos, separación, acceso directo y toma en competencia. | 45–55 min |
| 5 | API de administración y adjuntos. Verificar auditoría, protección del administrador efectivo y fallos de almacenamiento. | 30–45 min |
| 6 | Angular mínimo sobre los contratos ya probados. Construcción en Docker, README, guion y ensayo desde una base vacía. | 45–65 min |

Total orientativo de **4 a 5 horas y 20 minutos**, con el margen restante hasta las 6 horas para fallos de integración. El recorrido por API debe quedar demostrado al final del paso 4 para detectar a tiempo errores de negocio. Para JUnit, priorizar participaciones, descuentos, catálogo y transiciones. Para concurrencia y atomicidad, la comprobación debe usar PostgreSQL, porque una prueba unitaria por sí sola no demuestra el comportamiento de `@Version` al confirmar.

## 15. Checklist de entregables obligatorios

- [ ] Repositorio Git completo con commits legibles.
- [ ] README con prerrequisitos Docker y Java 25, comando de Docker Compose, comando de la aplicación y arranque de Angular sin instalación local adicional.
- [ ] Credenciales de prueba documentadas para administrador, radicador, analista y jefe.
- [ ] Migraciones versionadas con administrador hasheado, datos de ejemplo y los 100 BRP cargados al arrancar.
- [ ] README con modelo y relaciones, elección de vínculo oferente–oferta, flujo, seguridad, validaciones y comportamiento del inicio.
- [ ] Guion para radicar, tomar, escalar, aprobar y verificar el resultado en seguimiento.
- [ ] Carpeta local configurable de adjuntos e instrucciones para configurarla.
- [ ] Ejemplos de login, envío del JWT y consumo de las operaciones principales de la API.
- [ ] Pruebas unitarias pequeñas en JUnit y comando para ejecutarlas.
- [ ] Ocho respuestas en el README sobre dónde viven las transiciones y cómo extenderlas, concurrencia y experiencia del usuario, duración y revocación del JWT y refresh, diseño para usuarios nuevos, ambigüedades y mejoras al enunciado, alcance pendiente y estado del frontend, uso real de IA y habilidades o instrucciones utilizadas, y planteamiento en Django con el nivel real de familiaridad del autor.

**Recomendación para aprobación.** Validar el monolito modular y el orden backend primero. Antes de implementar, conviene resolver cuatro decisiones que cambian el comportamiento observable: exclusividad de ofertas por BRP, precisión y redondeo, acceso efectivo por rol y módulo, y alcance permitido de los adjuntos. El análisis queda cerrado y la implementación queda pendiente de aprobación del responsable técnico.
