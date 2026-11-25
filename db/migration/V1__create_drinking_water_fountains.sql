-- KAN-2: esquema PostGIS para las fuentes de agua potable de Barcelona (fonts_beure).
-- Fuente oficial: Open Data BCN. Coordenadas originales en ETRS89 / UTM 31N (EPSG:25831).

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE district (
    id   CHAR(2)     PRIMARY KEY CHECK (id ~ '^\d{2}$'),
    name VARCHAR(64) NOT NULL
);

-- PENDIENTE (regla de negocio sin decidir): los barrios 18, 19, 29 y 37 tienen fuentes
-- asignadas a más de un distrito en el dataset. Por eso el barrio NO lleva FK a distrito:
-- distrito y barrio son atributos independientes de cada fuente. Cuando se decida la regla
-- (barrio con distrito principal, relación N:M, etc.) se añadirá en una migración nueva.
CREATE TABLE neighbourhood (
    id   CHAR(2)     PRIMARY KEY CHECK (id ~ '^\d{2}$'),
    name VARCHAR(64) NOT NULL
);

CREATE TABLE drinking_water_fountain (
    id               VARCHAR(6)       PRIMARY KEY CHECK (id ~ '^(?!00)\d{2}-(?!000)\d{3}$'),
    name             VARCHAR(128),                       -- NULL si la fuente no trae nombre
    street           VARCHAR(128)     NOT NULL,
    street_number    VARCHAR(16),                        -- NULL si no hay número
    district_id      CHAR(2)          NOT NULL REFERENCES district (id),
    neighbourhood_id CHAR(2)          REFERENCES neighbourhood (id),  -- NULL en 10 fuentes
    -- El dataset no trae estado: siempre UNKNOWN hasta tener otra fuente de datos.
    status           VARCHAR(16)      NOT NULL DEFAULT 'UNKNOWN'
                     CHECK (status IN ('UNKNOWN', 'OPERATIONAL', 'OUT_OF_SERVICE')),
    -- Geometría de trabajo en metros (UTM 31N): radio, distancia y bbox con índice GiST.
    geom             geometry(Point, 25831) NOT NULL,
    -- Lat/lon WGS84 tal como vienen en el origen, para devolverlas sin reproyectar.
    latitude         DOUBLE PRECISION NOT NULL CHECK (latitude  BETWEEN -90  AND 90),
    longitude        DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    source_dataset   VARCHAR(32)      NOT NULL DEFAULT 'fonts_beure_2026',
    loaded_at        TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX drinking_water_fountain_geom_gix      ON drinking_water_fountain USING GIST (geom);
CREATE INDEX drinking_water_fountain_district_ix   ON drinking_water_fountain (district_id);
CREATE INDEX drinking_water_fountain_neighbourh_ix ON drinking_water_fountain (neighbourhood_id);
