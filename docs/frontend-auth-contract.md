# Contrato de autenticación para Angular

La inspección corresponde a Spring Boot en `9090ffa`. Angular consume las rutas relativas `/api` mediante el proxy local o Nginx. La configuración del backend inspeccionada no declara una política CORS propia, por lo que la integración prevista conserva el mismo origen.

## Inicio de sesión

`POST /api/auth/login` es público e ignora el encabezado Bearer si se envía. Recibe `application/json` con `login` y `password` como cadenas obligatorias. El DTO limita el identificador a 100 caracteres y la contraseña a 72. BCrypt también impone un máximo de 72 bytes UTF-8. El backend normaliza el identificador y verifica que la cuenta esté activa.

Respuesta `200`:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

El token se recibe en el cuerpo JSON y se envía en el encabezado `Authorization: Bearer <jwt>` de las solicitudes protegidas. El backend usa HS256, emite tokens con issuer `brp-backend` y audience `brp-api`, y exige `now < exp`. La tolerancia de cinco segundos se aplica al `iat` futuro. Los permisos vigentes se cargan desde la base en cada solicitud protegida.

## Usuario y permisos vigentes

`GET /api/auth/me` requiere un Bearer válido y un usuario activo. Devuelve `id`, `name`, `login`, `role`, `active`, `createdAt`, `updatedAt` y `modules`. Las fechas representan instantes y `modules` es una lista de identificadores. El perfil devuelto por este endpoint es la referencia para decidir la navegación.

| Opción | Rol requerido | Módulo |
|---|---|---|
| Radicación | `RADICADOR` | `RADICACION` |
| Análisis | `ANALISTA` | `ANALISIS` |
| Aprobación | `JEFE` | `APROBACION` |
| Seguimiento | Cualquiera de los cuatro roles | `SEGUIMIENTO` |
| Administración | `ADMINISTRADOR` | `ADMINISTRACION` |

Ocultar enlaces y proteger las rutas de Angular coordina la navegación. Spring Security y las políticas contextuales del backend toman la decisión de acceso sobre cada recurso.

## Errores y cierre de sesión

- `400` indica una solicitud inválida. El controlador puede devolver `ProblemDetail` con `errors`, cuyos elementos incluyen `field` y `message`.
- `401` indica credenciales inválidas en login o autenticación ausente o inválida en rutas protegidas. Spring Security puede devolver `WWW-Authenticate` con cuerpo vacío.
- `403` indica que el usuario autenticado carece del permiso exigido. El cliente debe poder manejar una respuesta sin JSON.
- `429` indica que se alcanzó el límite de intentos de login.

La API de identidad no expone endpoints REST de renovación, cierre de sesión ni revocación de JWT. El cierre local puede quitar el token de la sesión del navegador. Un token ya emitido mantiene su vigencia hasta que expire, salvo que la cuenta deje de estar activa. Si la fase exige renovación o revocación inmediata en servidor, queda pendiente definir ese contrato.
