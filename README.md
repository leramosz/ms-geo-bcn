# ms-geo-bcn

Microservicio Spring Boot (Java 17) de la API Geo BCN v2: fuentes de agua potable de Barcelona (1.760 fuentes,
10 distritos) sobre PostGIS. Contrato: [`src/main/resources/api/openapi.yaml`](src/main/resources/api/openapi.yaml)
(también en `/geo-bcn/v2/openapi.yaml` con la app en marcha).

## Arrancar

```bash
docker compose up -d                  # PostGIS con esquema y datos (ver db/README.md); esperar a "healthy"
./mvnw spring-boot:run                # http://localhost:8080/geo-bcn/v2
```

Credenciales de desarrollo (cámbialas fuera de local):

| Variable | Por defecto |
|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/geobcn` / `geobcn` / `geobcn` |
| `GEOBCN_API_KEYS` (lista separada por comas) | `dev-api-key` |
| `GEOBCN_JWT_SECRET` (HS256, mínimo 32 bytes) | `dev-only-secret-change-me-at-least-32-bytes` |

```bash
curl -H 'X-Api-Key: dev-api-key' 'http://localhost:8080/geo-bcn/v2/drinking-water-fountains?lat=41.3851&lon=2.1734&limit=5'
```

## Endpoints

| Método y ruta | Historia |
|---|---|
| `GET /drinking-water-fountains` — listado, `bbox`, `lat`+`lon`+`radius`, `district_id`, `neighbourhood_id`, `limit` (máx. 2000), `cursor` | KAN-6, KAN-8, KAN-9 |
| `GET /drinking-water-fountains/{id}` — ficha | KAN-7 |
| `GET /districts` — distritos con sus barrios | KAN-9 |

JSON o GeoJSON (`Accept: application/geo+json`), paginación por cursor sin total, errores `application/problem+json`,
`ETag`/`If-None-Match`, `X-Flow-ID`. Autenticación: `X-Api-Key` o `Authorization: Bearer <JWT HS256 con exp>`.

## Postman

Importa `postman/geo-bcn-v2.postman_collection.json` y `postman/geo-bcn-local.postman_environment.json`
(selecciona el entorno «Geo BCN local»). La API key va como autenticación de la colección, el JWT de la carpeta
«Autenticación» se firma en un script de pre-request y cada petición lleva tests (Runner → Run collection).
Ejecuta «Listar paginado» antes de «Página siguiente», y «Ficha de una fuente» antes de la petición condicional.

## Tests

```bash
./mvnw test      # unitarios (sin BD)
./mvnw verify    # + integración y e2e contra PostGIS real
```

Los `*IT` usan Testcontainers (`postgis/postgis:16-3.5`, requiere Docker). Sin Docker, apunta a un Postgres con
PostGIS: `GEOBCN_TEST_DB_URL=jdbc:postgresql://localhost:5432/postgres GEOBCN_TEST_DB_USER=… GEOBCN_TEST_DB_PASSWORD=… ./mvnw verify`
(crea y borra bases `geobcn_test_*`; el usuario necesita permiso para crear bases y extensiones).
El e2e valida además cada respuesta contra `openapi.yaml`.
