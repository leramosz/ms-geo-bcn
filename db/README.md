# Base de datos PostGIS: fuentes de agua potable (KAN-2)

PostgreSQL 16 + PostGIS 3.5 en Docker, con las 1.760 fuentes de agua potable de Barcelona
(`fonts_beure_2026.csv`, Open Data BCN, 10 distritos). Las fuentes ornamentales quedan fuera.

## Levantarla

```bash
docker compose up -d          # crea el esquema y carga los datos en el primer arranque
docker compose ps             # esperar a "healthy"
psql postgresql://geobcn:geobcn@localhost:5432/geobcn -c "select count(*) from drinking_water_fountain"   # 1760
```

La migración y la carga solo se ejecutan cuando el volumen está vacío. Para empezar de cero:
`docker compose down -v && docker compose up -d`.

Recargar el CSV sin borrar nada (la carga es idempotente, hace upsert por `id`):

```bash
docker compose exec -T postgis psql -U geobcn -d geobcn -v ON_ERROR_STOP=1 \
  -f /geo-bcn/db/load/load_fonts_beure.sql < db/data/fonts_beure_2026.csv
```

## Estructura

| Ruta | Qué es |
|---|---|
| `db/migration/V1__create_drinking_water_fountains.sql` | Esquema (nomenclatura Flyway, reutilizable desde Spring) |
| `db/load/load_fonts_beure.sql` | Carga del CSV por stdin: staging temporal y upsert |
| `db/data/fonts_beure_2026.csv` | Dataset de origen |
| `db/docker/init-db.sh` | Init del contenedor: migraciones y carga |

## Modelo

- `district (id, name)` y `neighbourhood (id, name)`: catálogos derivados del CSV (10 y 73).
- `drinking_water_fountain`: `id` (`NN-NNN`), `name` y `street_number` opcionales,
  `district_id` obligatorio, `neighbourhood_id` opcional (10 fuentes no lo traen),
  `status` (`UNKNOWN` siempre: el dataset no trae estado), `geom` en EPSG:25831 y
  `latitude`/`longitude` WGS84 tal cual vienen del origen.
- `geom` está en UTM 31N (metros) con índice GiST: radio, distancia y bbox se calculan en
  metros sin reproyectar la tabla. La diferencia entre X/Y y lat/lon del origen es < 1 mm.

**Pendiente:** los barrios 18, 19, 29 y 37 aparecen en fuentes de dos distritos distintos.
La regla de negocio no está decidida, así que barrio y distrito son columnas independientes
y `neighbourhood` no tiene FK a `district`.

## Consultas de referencia (historias KAN-6 a KAN-9)

```sql
-- Cerca de mí (KAN-8): fuentes a 300 m, ordenadas por distancia
WITH p AS (SELECT ST_Transform(ST_SetSRID(ST_MakePoint(2.1900, 41.3800), 4326), 25831) AS g)
SELECT f.id, f.name, round(ST_Distance(f.geom, p.g)) AS distance_m
FROM drinking_water_fountain f, p
WHERE ST_DWithin(f.geom, p.g, 300)
ORDER BY f.geom <-> p.g;

-- Mapa por bbox (KAN-6): lon/lat mín y máx
SELECT id, latitude, longitude FROM drinking_water_fountain
WHERE geom && ST_Transform(ST_MakeEnvelope(2.17, 41.37, 2.20, 41.39, 4326), 25831);

-- Filtrar por distrito / barrio (KAN-9)
SELECT id, name FROM drinking_water_fountain WHERE district_id = '01' AND neighbourhood_id = '03';
```

Credenciales `geobcn/geobcn` solo para desarrollo local; se puede cambiar con `POSTGRES_PASSWORD`.
