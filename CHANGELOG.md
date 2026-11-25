# Changelog

Todos los cambios relevantes de este proyecto se documentan en este fichero.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el versionado, el de la API
([Versionado Semántico](https://semver.org/lang/es/)).

## [Sin publicar]

## [2.0.0] - 2026-11-25

Primera versión con el back real (KAN-3): el microservicio deja de ser un stub y consulta PostGIS (KAN-2)
siguiendo la especificación v2 de [api-geo-bcn](https://github.com/leramosz/api-geo-bcn).

### Añadido

- Conexión a PostGIS con JDBC (`DB_URL`, `DB_USER`, `DB_PASSWORD`); consultas espaciales en EPSG:25831 con índice GiST.
- `GET /geo-bcn/v2/drinking-water-fountains`: listado de las 1.760 fuentes de agua potable, con filtros
  `bbox`, cerca de mí (`lat`, `lon`, `radius`, distancia en metros enteros), `district_id` y `neighbourhood_id`, combinables.
- `GET /geo-bcn/v2/drinking-water-fountains/{id}`: ficha de una fuente.
- `GET /geo-bcn/v2/districts`: catálogo de los 10 distritos con sus barrios.
- Paginación por cursor (`limit` hasta 2000, enlaces `self`, `first`, `prev` y `next`, sin total de fuentes).
- Respuestas en JSON o GeoJSON (`application/geo+json`) por negociación de contenido; `406` si el `Accept` no se soporta.
- Errores `application/problem+json` (RFC 9457) con `invalid_params`: `400`, `401`, `404`, `405`, `406`, `500` y `503`.
- Autenticación por API key (`X-Api-Key`) o JWT HS256 (`Authorization: Bearer`), sin OAuth2.
- `X-Flow-ID` (se valida, se genera si falta y se devuelve siempre), `ETag` con `If-None-Match` (`304`), `Cache-Control` y `Vary`.
- Contrato `openapi.yaml` v2 servido en `/geo-bcn/v2/openapi.yaml`.
- Colección de Postman v2.1 y entorno local en `postman/`, con ejemplos y tests por petición.
- Tests unitarios, de integración sobre PostGIS y e2e que validan cada respuesta contra el contrato.
- README con arranque, credenciales de desarrollo y ejecución de tests.

### Cambiado

- La API pasa de `/geo-bcn/v1` a `/geo-bcn/v2`: propiedades y parámetros en `snake_case`, sin sobre `data`,
  estado `UNKNOWN` mientras el dataset no tenga estado de la fuente.
- Los DTOs son `records` escritos a mano en lugar de clases generadas por openapi-generator.
- `pom.xml`: se elimina la dependencia de test duplicada y un `]` suelto; se retiran openapi-generator, Lombok y springdoc.

### Corregido

- Las rutas con segmentos vacíos (`//geo-bcn/v2/...`) responden `404` Problem JSON en lugar de `500`.
- Postman: la petición «Página siguiente» generaba `{{host}}/{{nextLink}}` (doble barra); ahora usa `{{host}}{{nextLink}}`.

### Eliminado

- El controller stub y el `GlobalExceptionHandler` de la v1.

### Pendiente

- Regla de los barrios 18, 19, 29 y 37, que aparecen en dos distritos: hoy se aceptan con ambos.
- Rate limit (`429`) y mensajes de error traducidos según `Accept-Language`.
