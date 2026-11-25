package com.geobcn.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.geobcn.service.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E real: app Spring Boot en un puerto aleatorio, PostGIS con las 1.760 fuentes y HTTP de verdad.
 * Cada respuesta se valida además contra el contrato src/main/resources/api/openapi.yaml.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FountainApiE2EIT {

    private static final String API_KEY = "e2e-key";
    private static final String JWT_SECRET = "e2e-secret-with-at-least-32-bytes-long";
    private static final String BASE = "/geo-bcn/v2";
    private static TestDatabase db;
    private static final OpenApiInteractionValidator CONTRACT =
            OpenApiInteractionValidator.createFor("api/openapi.yaml")
                    // Con allOf (PageLinks + items) el validador exige additionalProperties: no aplica a este contrato.
                    .withLevelResolver(LevelResolver.create()
                            .withLevel("validation.schema.additionalProperties", ValidationReport.Level.IGNORE).build())
                    .build();
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void database() {
        db = TestDatabase.createWithSchema();
        db.loadFountains();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> db.url());
        registry.add("spring.datasource.username", () -> db.user());
        registry.add("spring.datasource.password", () -> db.password());
        registry.add("geobcn.security.api-keys", () -> API_KEY);
        registry.add("geobcn.security.jwt-secret", () -> JWT_SECRET);
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    // ---------- helpers ----------

    private ResponseEntity<String> get(String pathAndQuery, String... headers) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Api-Key", API_KEY);
        for (int i = 0; i < headers.length; i += 2) {
            h.set(headers[i], headers[i + 1]);
        }
        return exchange(pathAndQuery, h);
    }

    private ResponseEntity<String> exchange(String pathAndQuery, HttpHeaders headers) {
        ResponseEntity<String> response = rest.exchange(URI.create("http://localhost:" + port + pathAndQuery),
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertMatchesContract(pathAndQuery, headers, response);
        return response;
    }

    private static void assertMatchesContract(String pathAndQuery, HttpHeaders headers, ResponseEntity<String> response) {
        URI uri = URI.create(pathAndQuery);
        SimpleRequest.Builder request = SimpleRequest.Builder.get(uri.getRawPath().substring(BASE.length()));
        if (uri.getRawQuery() != null) {
            for (String pair : uri.getRawQuery().split("&")) {
                String[] kv = pair.split("=", 2);
                request.withQueryParam(kv[0], java.net.URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.UTF_8));
            }
        }
        headers.forEach((k, v) -> request.withHeader(k, v));
        SimpleResponse.Builder expected = SimpleResponse.Builder.status(response.getStatusCode().value());
        response.getHeaders().forEach((k, v) -> expected.withHeader(k, v));
        if (response.getBody() != null) {
            expected.withBody(response.getBody());
        }
        // La respuesta se valida siempre; la petición solo cuando es válida (los tests negativos envían parámetros erróneos a propósito).
        List<ValidationReport.Message> messages = new ArrayList<>(
                CONTRACT.validateResponse(request.build().getPath(), Request.Method.GET, expected.build()).getMessages());
        if (response.getStatusCode().value() < 400) {
            messages.addAll(CONTRACT.validateRequest(request.build()).getMessages());
        }
        assertThat(messages).as("contrato: %s → %s", pathAndQuery, messages).isEmpty();
    }

    /** Ids del primer nivel de cada elemento (findValuesAsText("id") también recoge los ids anidados). */
    private static List<String> ids(JsonNode array) {
        List<String> ids = new ArrayList<>();
        array.forEach(i -> ids.add(i.get("id").asText()));
        return ids;
    }

    private static JsonNode json(ResponseEntity<String> r) throws Exception {
        return JSON.readTree(r.getBody());
    }

    /** Recorre todas las páginas siguiendo "next" y devuelve los ids en orden. */
    private List<String> walk(String firstUrl) throws Exception {
        List<String> ids = new ArrayList<>();
        String url = firstUrl;
        while (url != null) {
            JsonNode page = json(get(url));
            page.get("items").forEach(i -> ids.add(i.get("id").asText()));
            url = page.has("next") ? page.get("next").asText() : null;
        }
        return ids;
    }

    private static void assertProblem(ResponseEntity<String> r, int status, String paramName) throws Exception {
        assertThat(r.getStatusCode().value()).isEqualTo(status);
        assertThat(r.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        JsonNode body = json(r);
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("title").asText()).isNotBlank();
        if (paramName != null) {
            assertThat(body.get("invalid_params").findValuesAsText("name")).contains(paramName);
        }
    }

    // ---------- KAN-6: mapa ----------

    @Test
    @DisplayName("KAN-6 CA4: con limit=2000 una sola página devuelve las 1.760 fuentes, sin página siguiente")
    void allFountainsInOnePage() throws Exception {
        JsonNode page = json(get(BASE + "/drinking-water-fountains?limit=2000"));

        assertThat(page.get("items")).hasSize(1760);
        assertThat(page.has("next")).isFalse();
        assertThat(page.has("prev")).isFalse();
        assertThat(page.has("total")).isFalse();
        assertThat(ids(page.get("items"))).doesNotHaveDuplicates();
        assertThat(page.get("items").findValuesAsText("status")).containsOnly("UNKNOWN");
    }

    @Test
    @DisplayName("KAN-6: la carga paginada por cursor recorre las 1.760 sin repetir ni saltar ninguna")
    void cursorWalkCoversEverything() throws Exception {
        List<String> ids = walk(BASE + "/drinking-water-fountains?limit=500");

        assertThat(ids).hasSize(1760).doesNotHaveDuplicates().isSorted();
    }

    @Test
    @DisplayName("KAN-6 CA2: application/geo+json devuelve una FeatureCollection con Point [lon, lat]")
    void geoJson() throws Exception {
        ResponseEntity<String> r = get(BASE + "/drinking-water-fountains?limit=2000", "Accept", "application/geo+json");
        JsonNode fc = json(r);

        assertThat(r.getHeaders().getContentType().toString()).startsWith("application/geo+json");
        assertThat(fc.get("type").asText()).isEqualTo("FeatureCollection");
        assertThat(fc.get("features")).hasSize(1760);
        JsonNode santCarles = null;
        for (JsonNode f : fc.get("features")) {
            if (f.get("id").asText().equals("01-004")) {
                santCarles = f;
            }
        }
        assertThat(santCarles.get("geometry").get("coordinates").get(0).asDouble()).isEqualTo(2.18896495);
        assertThat(santCarles.get("geometry").get("coordinates").get(1).asDouble()).isEqualTo(41.379145587);
    }

    @Test
    @DisplayName("KAN-6: bbox devuelve solo fuentes dentro de la caja, ordenadas por id")
    void bbox() throws Exception {
        JsonNode page = json(get(BASE + "/drinking-water-fountains?bbox=2.17,41.37,2.20,41.39&limit=2000"));

        assertThat(page.get("items")).isNotEmpty();
        for (JsonNode f : page.get("items")) {
            assertThat(f.get("location").get("longitude").asDouble()).isBetween(2.17, 2.20);
            assertThat(f.get("location").get("latitude").asDouble()).isBetween(41.37, 41.39);
            assertThat(f.has("distance")).isFalse();
        }
        assertThat(ids(page.get("items"))).isSorted();
    }

    // ---------- KAN-7: ficha ----------

    @Test
    @DisplayName("KAN-7 CA1: la ficha de 01-004 trae nombre, dirección, barrio, distrito, coordenadas y estado")
    void fichaSantCarles() throws Exception {
        ResponseEntity<String> r = get(BASE + "/drinking-water-fountains/01-004");
        JsonNode f = json(r);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(f.get("id").asText()).isEqualTo("01-004");
        assertThat(f.get("name").asText()).isEqualTo("Sant Carles");
        assertThat(f.get("address").get("formatted").asText()).isEqualTo("C COMTE DE SANTA CLARA, 11");
        assertThat(f.get("neighbourhood").get("name").asText()).isEqualTo("la Barceloneta");
        assertThat(f.get("district").get("name").asText()).isEqualTo("Ciutat Vella");
        assertThat(f.get("location").get("latitude").asDouble()).isEqualTo(41.379145587);
        assertThat(f.get("location").get("longitude").asDouble()).isEqualTo(2.18896495);
        assertThat(f.get("status").asText()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("KAN-7 CA2: id inexistente → 404 Problem JSON; id mal formado → 400")
    void fichaNotFound() throws Exception {
        assertProblem(get(BASE + "/drinking-water-fountains/99-999"), 404, null);
        assertProblem(get(BASE + "/drinking-water-fountains/abc"), 400, "drinking_water_fountain_id");
    }

    @Test
    @DisplayName("KAN-7 CA3: una fuente sin número de calle muestra solo la calle y nunca campos vacíos")
    void fichaWithoutNumber() throws Exception {
        String id = jdbc.queryForObject("""
                SELECT id FROM drinking_water_fountain
                WHERE street_number IS NULL AND street <> '' ORDER BY id LIMIT 1""", String.class);

        JsonNode f = json(get(BASE + "/drinking-water-fountains/" + id));

        assertThat(f.get("address").get("street_number").isNull()).isTrue();
        assertThat(f.get("address").get("formatted").asText()).isEqualTo(f.get("address").get("street").asText());
        assertThat(f.get("status").asText()).isEqualTo("UNKNOWN");
    }

    // ---------- KAN-8: cerca de mí ----------

    @Test
    @DisplayName("KAN-8 CA5: en (41.3851, 2.1734) hasta 10 fuentes ordenadas por distancia, con metros enteros ≤ radio")
    void nearMe() throws Exception {
        JsonNode page = json(get(BASE + "/drinking-water-fountains?lat=41.3851&lon=2.1734&radius=500&limit=10"));

        List<Integer> distances = new ArrayList<>();
        page.get("items").forEach(i -> {
            assertThat(i.get("distance").isIntegralNumber()).isTrue();
            distances.add(i.get("distance").asInt());
        });
        assertThat(distances).hasSize(10).isSorted().allMatch(d -> d <= 500);
        assertThat(page.get("next").asText()).contains("cursor=");
    }

    @Test
    @DisplayName("KAN-8: la distancia coincide con un cálculo independiente en PostGIS (geography, esferoide)")
    void distanceMatchesIndependentCalculation() throws Exception {
        JsonNode first = json(get(BASE + "/drinking-water-fountains?lat=41.3851&lon=2.1734&limit=1")).get("items").get(0);

        Double expected = jdbc.queryForObject("""
                SELECT ST_Distance(ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography,
                                   ST_SetSRID(ST_MakePoint(2.1734, 41.3851), 4326)::geography)
                FROM drinking_water_fountain WHERE id = ?""", Double.class, first.get("id").asText());
        assertThat((double) first.get("distance").asInt()).isCloseTo(expected, org.assertj.core.data.Offset.offset(2.0));
    }

    @Test
    @DisplayName("KAN-8 CA2: el siguiente cursor continúa sin solaparse y 'prev' vuelve a la página anterior")
    void nextAndPrev() throws Exception {
        JsonNode p1 = json(get(BASE + "/drinking-water-fountains?lat=41.3851&lon=2.1734&radius=1000&limit=10"));
        JsonNode p2 = json(get(p1.get("next").asText()));
        JsonNode back = json(get(p2.get("prev").asText()));

        assertThat(p1.has("prev")).isFalse();
        assertThat(ids(p2.get("items"))).doesNotContainAnyElementsOf(ids(p1.get("items")));
        assertThat(p2.get("items").get(0).get("distance").asInt()).isGreaterThanOrEqualTo(p1.get("items").get(9).get("distance").asInt());
        assertThat(ids(back.get("items"))).isEqualTo(ids(p1.get("items")));
        assertThat(back.has("prev")).isFalse();
    }

    @Test
    @DisplayName("KAN-8 CA3: lat/lon fuera de rango o radio ≤ 0 → 400 Problem JSON indicando el parámetro")
    void nearMeInvalid() throws Exception {
        assertProblem(get(BASE + "/drinking-water-fountains?lat=91&lon=2.17"), 400, "lat");
        assertProblem(get(BASE + "/drinking-water-fountains?lat=41.38&lon=181"), 400, "lon");
        assertProblem(get(BASE + "/drinking-water-fountains?lat=41.38&lon=2.17&radius=0"), 400, "radius");
        assertProblem(get(BASE + "/drinking-water-fountains?lat=41.38"), 400, "lon");
        assertProblem(get(BASE + "/drinking-water-fountains?radius=300"), 400, "radius");
        assertProblem(get(BASE + "/drinking-water-fountains?bbox=2.1,41.3,2.2,41.4&lat=41.38&lon=2.17"), 400, "bbox");
        assertProblem(get(BASE + "/drinking-water-fountains?limit=2001"), 400, "limit");
    }

    @Test
    @DisplayName("KAN-8 CA4/CA7: un punto sin fuentes en el radio → 200 con lista vacía")
    void nearMeEmpty() throws Exception {
        ResponseEntity<String> r = get(BASE + "/drinking-water-fountains?lat=48.8566&lon=2.3522&radius=500");
        JsonNode page = json(r);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.get("items")).isEmpty();
        assertThat(page.has("next")).isFalse();
    }

    // ---------- KAN-9: distrito y barrio ----------

    @Test
    @DisplayName("KAN-9 CA1: filtrar por distrito 01 devuelve solo ese distrito y el total coincide con la base de datos")
    void byDistrict() throws Exception {
        int expected = jdbc.queryForObject("SELECT count(*) FROM drinking_water_fountain WHERE district_id = '01'", Integer.class);

        JsonNode page = json(get(BASE + "/drinking-water-fountains?district_id=01&limit=2000"));

        assertThat(page.get("items")).hasSize(expected);
        assertThat(ids(page.get("items")).size()).isEqualTo(expected);
        page.get("items").forEach(i -> assertThat(i.get("district").get("id").asText()).isEqualTo("01"));
    }

    @Test
    @DisplayName("KAN-9 CA2: filtrar por el barrio 03 devuelve solo fuentes de la Barceloneta")
    void byNeighbourhood() throws Exception {
        JsonNode page = json(get(BASE + "/drinking-water-fountains?neighbourhood_id=03&limit=2000"));

        assertThat(page.get("items")).isNotEmpty();
        page.get("items").forEach(i -> assertThat(i.get("neighbourhood").get("id").asText()).isEqualTo("03"));
    }

    @Test
    @DisplayName("KAN-9 CA3: barrio de otro distrito → 400 Problem JSON que lo indica")
    void neighbourhoodOutsideDistrict() throws Exception {
        ResponseEntity<String> r = get(BASE + "/drinking-water-fountains?district_id=02&neighbourhood_id=03");

        assertProblem(r, 400, "neighbourhood_id");
        assertThat(json(r).get("detail").asText()).isEqualTo("Neighbourhood 03 does not belong to district 02.");
    }

    @Test
    @DisplayName("KAN-9 CA4: códigos inexistentes → 400, no una lista vacía")
    void unknownCodes() throws Exception {
        assertProblem(get(BASE + "/drinking-water-fountains?neighbourhood_id=99"), 400, "neighbourhood_id");
        assertProblem(get(BASE + "/drinking-water-fountains?district_id=11"), 400, "district_id");
        assertProblem(get(BASE + "/drinking-water-fountains?district_id=00"), 400, "district_id");
    }

    @Test
    @DisplayName("KAN-9 CA5: el catálogo devuelve los 10 distritos, cada uno con sus barrios")
    void catalog() throws Exception {
        JsonNode catalog = json(get(BASE + "/districts"));

        assertThat(catalog.get("items")).hasSize(10);
        assertThat(ids(catalog.get("items"))).containsExactly("01", "02", "03", "04", "05", "06", "07", "08", "09", "10");
        catalog.get("items").forEach(d -> assertThat(d.get("neighbourhoods")).isNotEmpty());
        JsonNode ciutatVella = catalog.get("items").get(0);
        assertThat(ciutatVella.get("name").asText()).isEqualTo("Ciutat Vella");
        assertThat(ciutatVella.get("neighbourhoods").findValuesAsText("id")).contains("03");
    }

    @Test
    @DisplayName("KAN-9 CA9: filtro de barrio combinado con cercanía → solo ese barrio, ordenado por distancia")
    void neighbourhoodWithProximity() throws Exception {
        JsonNode page = json(get(BASE + "/drinking-water-fountains?neighbourhood_id=03&lat=41.3805&lon=2.188&radius=2000&limit=50"));

        List<Integer> distances = new ArrayList<>();
        page.get("items").forEach(i -> {
            assertThat(i.get("neighbourhood").get("id").asText()).isEqualTo("03");
            distances.add(i.get("distance").asInt());
        });
        assertThat(distances).isNotEmpty().isSorted();
        assertThat(page.get("self").asText()).contains("neighbourhood_id=03");
    }

    @Test
    @DisplayName("Los enlaces de paginación conservan los filtros aplicados")
    void linksKeepFilters() throws Exception {
        JsonNode page = json(get(BASE + "/drinking-water-fountains?district_id=01&limit=5"));

        for (String link : List.of("self", "first", "next")) {
            assertThat(page.get(link).asText()).startsWith(BASE + "/drinking-water-fountains?district_id=01&limit=5");
        }
        assertThat(walk(BASE + "/drinking-water-fountains?district_id=01&limit=7"))
                .hasSize(jdbc.queryForObject("SELECT count(*) FROM drinking_water_fountain WHERE district_id = '01'", Integer.class));
    }

    // ---------- Transversales: auth, cabeceras, caché, errores ----------

    @Test
    @DisplayName("Sin credenciales o con API key errónea → 401 Problem JSON; con JWT HS256 vigente → 200")
    void authentication() throws Exception {
        HttpHeaders none = new HttpHeaders();
        assertProblem(exchange(BASE + "/drinking-water-fountains/01-004", none), 401, null);

        HttpHeaders wrongKey = new HttpHeaders();
        wrongKey.set("X-Api-Key", "otra");
        assertProblem(exchange(BASE + "/districts", wrongKey), 401, null);

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .subject("e2e").expirationTime(new Date(System.currentTimeMillis() + 60_000)).build());
        jwt.sign(new MACSigner(JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
        HttpHeaders bearer = new HttpHeaders();
        bearer.setBearerAuth(jwt.serialize());
        assertThat(exchange(BASE + "/drinking-water-fountains/01-004", bearer).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("X-Flow-ID se repite en la respuesta, se genera si falta y uno inválido da 400")
    void flowId() throws Exception {
        assertThat(get(BASE + "/districts", "X-Flow-ID", "mi-flujo-1").getHeaders().getFirst("X-Flow-ID")).isEqualTo("mi-flujo-1");
        assertThat(get(BASE + "/districts").getHeaders().getFirst("X-Flow-ID")).hasSize(36);
        assertProblem(get(BASE + "/drinking-water-fountains", "X-Flow-ID", "con espacios!"), 400, "X-Flow-ID");
    }

    @Test
    @DisplayName("ETag + If-None-Match → 304 sin cuerpo; las respuestas llevan Cache-Control y Vary")
    void caching() {
        ResponseEntity<String> first = get(BASE + "/drinking-water-fountains/01-004");
        String etag = first.getHeaders().getETag();

        ResponseEntity<String> second = get(BASE + "/drinking-water-fountains/01-004", "If-None-Match", etag);

        assertThat(etag).isNotBlank();
        assertThat(first.getHeaders().getCacheControl()).contains("max-age=86400");
        assertThat(first.getHeaders().getFirst("Vary")).isEqualTo("accept, accept-encoding");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(second.getBody()).isNull();
    }

    @Test
    @DisplayName("Una ruta con doble barra (//geo-bcn/v2/...) → 404 Problem JSON, no 500")
    void doubleSlash() throws Exception {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Api-Key", API_KEY);
        ResponseEntity<String> r = rest.exchange(URI.create("http://localhost:" + port + "//geo-bcn/v2/drinking-water-fountains?limit=1"),
                HttpMethod.GET, new HttpEntity<>(h), String.class);

        assertProblem(r, 404, null);
    }

    @Test
    @DisplayName("Accept no soportado → 406 Problem JSON")
    void notAcceptable() throws Exception {
        assertProblem(get(BASE + "/drinking-water-fountains", "Accept", "text/html"), 406, null);
    }

    @Test
    @DisplayName("El contrato OpenAPI se sirve sin credenciales")
    void specServed() {
        ResponseEntity<String> r = rest.getForEntity("http://localhost:" + port + BASE + "/openapi.yaml", String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("version: 2.0.0");
    }
}
