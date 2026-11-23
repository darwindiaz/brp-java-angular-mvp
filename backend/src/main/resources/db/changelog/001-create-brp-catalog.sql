CREATE TABLE brp_catalog (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code text NOT NULL,
    ingestion_date date NOT NULL,
    status text NOT NULL,
    asset_type text NOT NULL,
    property_registration text,
    license_plate text,
    department text NOT NULL,
    municipality text NOT NULL,
    description text NOT NULL,
    price numeric,
    price_assigned_on date,
    CONSTRAINT brp_code_ingestion_uk UNIQUE (code, ingestion_date),
    CONSTRAINT brp_status_ck CHECK (status IN (
        'DISPONIBLE', 'SIN_PRECIO', 'PENDIENTE_TECNICO',
        'EN_TRAMITE_DE_VENTA', 'SANEAMIENTO', 'DADO_DE_BAJA'
    )),
    CONSTRAINT brp_price_pair_ck CHECK ((price IS NULL) = (price_assigned_on IS NULL)),
    CONSTRAINT brp_unpriced_status_ck CHECK (price IS NOT NULL OR status = 'SIN_PRECIO'),
    CONSTRAINT brp_positive_price_ck CHECK (
        price IS NULL OR (price > 0 AND price < 'Infinity'::numeric)
    ),
    CONSTRAINT brp_registration_ck CHECK (
        (asset_type IN ('APARTAMENTO', 'CASA', 'LOCAL', 'LOTE')
            AND property_registration IS NOT NULL AND btrim(property_registration) <> ''
            AND license_plate IS NULL)
        OR (asset_type = 'VEHICULO' AND property_registration IS NULL
            AND license_plate IS NOT NULL AND btrim(license_plate) <> '')
        OR (asset_type = 'MAQUINARIA' AND property_registration IS NULL AND license_plate IS NULL)
    )
);
