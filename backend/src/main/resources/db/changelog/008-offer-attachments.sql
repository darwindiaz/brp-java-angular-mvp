CREATE TABLE oferta_adjunto (
    id uuid PRIMARY KEY,
    oferta_id bigint NOT NULL REFERENCES oferta(id),
    nombre_archivo varchar(255) NOT NULL CHECK (btrim(nombre_archivo) <> ''),
    tipo_contenido text NOT NULL CHECK (tipo_contenido IN ('application/pdf', 'image/jpeg', 'image/png')),
    tamano_bytes bigint NOT NULL CHECK (tamano_bytes > 0),
    cargado_en timestamptz NOT NULL
);

CREATE INDEX oferta_adjunto_oferta_carga_idx ON oferta_adjunto (oferta_id, cargado_en, id);
