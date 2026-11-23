CREATE TABLE usuario (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre text NOT NULL CHECK (btrim(nombre) <> ''),
    identificador_acceso text NOT NULL UNIQUE
        CHECK (identificador_acceso ~ '^[a-z0-9][a-z0-9._-]{0,79}$'),
    password_hash text NOT NULL
        CHECK (password_hash ~ '^[$]2[aby][$](0[4-9]|[12][0-9]|3[01])[$][./A-Za-z0-9]{53}$'),
    rol text NOT NULL CHECK (rol IN ('ADMINISTRADOR', 'RADICADOR', 'ANALISTA', 'JEFE')),
    activo boolean NOT NULL DEFAULT true,
    creado_en timestamptz NOT NULL DEFAULT current_timestamp,
    actualizado_en timestamptz NOT NULL DEFAULT current_timestamp
);

CREATE TABLE modulo (
    codigo text PRIMARY KEY,
    nombre text NOT NULL
);

CREATE TABLE usuario_modulo (
    usuario_id bigint NOT NULL REFERENCES usuario(id),
    modulo_codigo text NOT NULL REFERENCES modulo(codigo),
    PRIMARY KEY (usuario_id, modulo_codigo)
);

CREATE TABLE auditoria_usuario_modulo (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_usuario_id bigint REFERENCES usuario(id),
    destinatario_usuario_id bigint NOT NULL REFERENCES usuario(id),
    modulo_codigo text NOT NULL REFERENCES modulo(codigo),
    accion text NOT NULL CHECK (accion IN ('ASIGNADO', 'RETIRADO')),
    ocurrido_en timestamptz NOT NULL DEFAULT clock_timestamp(),
    origen text NOT NULL CHECK (
        (origen = 'USUARIO' AND actor_usuario_id IS NOT NULL) OR
        (origen = 'SISTEMA' AND actor_usuario_id IS NULL)
    )
);

CREATE TABLE bloqueo_administracion (
    id integer PRIMARY KEY CHECK (id = 1)
);

INSERT INTO bloqueo_administracion (id) VALUES (1);
INSERT INTO modulo (codigo, nombre) VALUES
    ('RADICACION', 'Radicación'),
    ('ANALISIS', 'Análisis'),
    ('APROBACION', 'Aprobación'),
    ('SEGUIMIENTO', 'Seguimiento'),
    ('ADMINISTRACION', 'Administración');
