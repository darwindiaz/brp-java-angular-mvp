ALTER TABLE oferta
    ADD COLUMN analista_id bigint REFERENCES usuario(id),
    ADD COLUMN modificado_en timestamptz,
    ADD COLUMN escalado_en timestamptz,
    ADD COLUMN devuelto_en timestamptz,
    ADD COLUMN finalizado_en timestamptz,
    ADD CONSTRAINT oferta_analista_distinto_ck CHECK (analista_id <> radicador_id),
    ADD CONSTRAINT oferta_asignacion_ck CHECK (
        (estado <> 'RADICADO' OR analista_id IS NULL)
        AND (estado NOT IN ('EN_PROCESO', 'ESCALADO', 'APROBADO', 'RECHAZADO') OR analista_id IS NOT NULL)
    ),
    ADD CONSTRAINT oferta_escalamiento_ck CHECK (
        (estado NOT IN ('ESCALADO', 'APROBADO') OR escalado_en IS NOT NULL)
        AND (devuelto_en IS NULL OR escalado_en IS NOT NULL)
    ),
    ADD CONSTRAINT oferta_finalizacion_ck CHECK (
        (estado IN ('APROBADO', 'RECHAZADO')) = (finalizado_en IS NOT NULL)
    );

CREATE INDEX oferta_estado_numero_idx ON oferta (estado, numero_radicacion);
CREATE INDEX oferta_analista_estado_idx ON oferta (analista_id, estado);
