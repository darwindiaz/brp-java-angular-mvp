CREATE SEQUENCE oferta_radicacion_seq AS bigint;

CREATE TABLE oferta (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero_radicacion bigint NOT NULL DEFAULT nextval('oferta_radicacion_seq')
        UNIQUE CHECK (numero_radicacion > 0),
    radicador_id bigint NOT NULL REFERENCES usuario(id),
    brp_version_id bigint NOT NULL REFERENCES brp_catalog(id),
    precio_referencia numeric(19,2) NOT NULL CHECK (
        precio_referencia > 0 AND precio_referencia < 'Infinity'::numeric
    ),
    valor_total numeric(19,2) NOT NULL CHECK (
        valor_total > 0 AND valor_total < 'Infinity'::numeric
    ),
    descuento numeric(25,4) NOT NULL CHECK (descuento <= 100),
    estado text NOT NULL CHECK (
        estado IN ('RADICADO', 'EN_PROCESO', 'ESCALADO', 'APROBADO', 'RECHAZADO', 'DESISTIDO')
    ),
    creado_en timestamptz NOT NULL,
    version bigint NOT NULL CHECK (version >= 0)
);

ALTER SEQUENCE oferta_radicacion_seq OWNED BY oferta.numero_radicacion;
CREATE INDEX oferta_radicador_idx ON oferta (radicador_id);

CREATE TABLE oferta_participacion (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    oferta_id bigint NOT NULL REFERENCES oferta(id),
    tipo_identificacion text NOT NULL CHECK (tipo_identificacion IN ('CC', 'NIT')),
    numero_identificacion text NOT NULL CHECK (
        numero_identificacion = btrim(numero_identificacion)
        AND numero_identificacion ~ '[^[:space:]]'
    ),
    nombre text NOT NULL CHECK (nombre ~ '[^[:space:]]'),
    contacto text,
    porcentaje numeric(7,4) NOT NULL CHECK (porcentaje > 0 AND porcentaje <= 100),
    UNIQUE (oferta_id, numero_identificacion)
);

CREATE TABLE oferta_pago (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    participacion_id bigint NOT NULL REFERENCES oferta_participacion(id),
    modalidad text NOT NULL CHECK (modalidad IN ('CONTADO', 'PRESTAMO')),
    fecha date NOT NULL,
    valor numeric(19,2) NOT NULL CHECK (valor > 0 AND valor < 'Infinity'::numeric),
    financiadora text,
    CHECK (modalidad = 'PRESTAMO' OR financiadora IS NULL)
);

CREATE INDEX oferta_pago_participacion_idx ON oferta_pago (participacion_id);
