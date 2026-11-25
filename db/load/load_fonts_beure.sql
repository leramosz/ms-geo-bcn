-- KAN-2: carga idempotente de fonts_beure (CSV de Open Data BCN) en PostGIS.
-- Uso:  psql -v ON_ERROR_STOP=1 -f db/load/load_fonts_beure.sql < db/data/fonts_beure_2026.csv
-- El CSV llega por stdin (pstdin), así el script no depende de rutas.

BEGIN;

CREATE TEMP TABLE stg_fonts_beure (
    codi text, nom text, carrer text, numero_carrer text,
    codi_barri text, nom_barri text, codi_districte text, nom_districte text,
    x_etrs89 text, y_etrs89 text, latitud text, longitud text
) ON COMMIT DROP;

\copy stg_fonts_beure FROM pstdin WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

INSERT INTO district (id, name)
SELECT DISTINCT codi_districte, nom_districte FROM stg_fonts_beure
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO neighbourhood (id, name)
SELECT DISTINCT codi_barri, nom_barri FROM stg_fonts_beure WHERE nullif(codi_barri, '') IS NOT NULL
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO drinking_water_fountain
    (id, name, street, street_number, district_id, neighbourhood_id, geom, latitude, longitude)
SELECT codi,
       nullif(trim(nom), ''),
       trim(carrer),
       nullif(trim(numero_carrer), ''),
       codi_districte,
       nullif(codi_barri, ''),
       ST_SetSRID(ST_MakePoint(x_etrs89::double precision, y_etrs89::double precision), 25831),
       latitud::double precision,
       longitud::double precision
FROM stg_fonts_beure
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, street = EXCLUDED.street, street_number = EXCLUDED.street_number,
    district_id = EXCLUDED.district_id, neighbourhood_id = EXCLUDED.neighbourhood_id,
    geom = EXCLUDED.geom, latitude = EXCLUDED.latitude, longitude = EXCLUDED.longitude,
    loaded_at = now();

COMMIT;

SELECT count(*) AS fountains,
       count(DISTINCT district_id) AS districts,
       count(*) FILTER (WHERE neighbourhood_id IS NULL) AS without_neighbourhood
FROM drinking_water_fountain;
