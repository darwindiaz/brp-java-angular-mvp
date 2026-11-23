INSERT INTO brp_catalog (
    code, ingestion_date, status, asset_type, property_registration,
    license_plate, department, municipality, description, price, price_assigned_on
)
SELECT
    codigo,
    make_date(anio_ingesta::integer, mes_ingesta::integer, dia_ingesta::integer),
    CASE WHEN NULLIF(btrim(fecha_asignacion_precio), '') IS NULL THEN 'SIN_PRECIO' ELSE estado END,
    tipo_brp,
    NULLIF(btrim(matricula_inmobiliaria), ''),
    NULLIF(btrim(placa), ''),
    departamento,
    municipio,
    descripcion,
    CASE WHEN NULLIF(btrim(fecha_asignacion_precio), '') IS NOT NULL
        THEN NULLIF(btrim(precio), '')::numeric END,
    NULLIF(btrim(fecha_asignacion_precio), '')::date
FROM (SELECT DISTINCT * FROM brp_csv_stage) AS source_rows;

DROP TABLE brp_csv_stage;
