# Estrategia de ramas y commits

| Rama | Origen | Uso e integración |
|---|---|---|
| `master` | Commit inicial `init` | Rama estable de entregables. Integra `develop` después de probar y validar la versión. El trabajo ordinario se hace en ramas de cambio. |
| `develop` | `master` | Rama de integración. Recibe las funcionalidades y correcciones ordinarias. |
| `feature/<nombre>` | `develop` | Nueva funcionalidad. Se integra en `develop`. |
| `fix/<nombre>` | `develop` | Corrección ordinaria. Se integra en `develop`. |
| `hotfix/<nombre>` | `master` | Corrección urgente. Tras validarla, se integra en `master` y se incorpora también en `develop`. |

Usa nombres descriptivos en inglés y separados por guiones, como
`feature/offer-registration`, `fix/discount-rounding` y
`hotfix/login-timeout`.

## Integración

1. Actualiza la rama de origen desde `origin` y crea la rama de cambio.
2. Publica la rama y abre un pull request hacia la rama de integración indicada.
3. Comprueba las pruebas, el alcance del cambio y los archivos incluidos antes de integrar.
4. Promueve `develop` a `master` mediante un pull request cuando la versión esté probada y validada.

Publica cambios sin forzar el envío ni reescribir el historial compartido.

## Convención de commits

El primer commit usa el mensaje `init`. Los siguientes siguen
[Conventional Commits](https://www.conventionalcommits.org/es/v1.0.0/) con
el formato `tipo: descripción` o `tipo(ámbito): descripción`.

| Tipo | Uso |
|---|---|
| `feat:` | Nuevas funcionalidades. |
| `fix:` | Correcciones de errores. |
| `docs:` | Cambios de documentación. |
| `test:` | Pruebas nuevas o actualizadas. |
| `refactor:` | Cambios internos que preservan el comportamiento. |
| `chore:` | Configuración y mantenimiento. |

Ejemplo de mensaje: `feat(offers): validate payment totals`.
