package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest;

import com.geobcn.service.fountains.drinkingwater.domain.InvalidParam;
import com.geobcn.service.fountains.drinkingwater.domain.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchParamsParserTest {

    private static InvalidRequestException invalid(Map<String, String> params) {
        return (InvalidRequestException) catchThrowable(params);
    }

    private static Throwable catchThrowable(Map<String, String> params) {
        try {
            SearchParamsParser.parse(params);
            return null;
        } catch (InvalidRequestException e) {
            return e;
        }
    }

    @ParameterizedTest(name = "KAN-8 CA3: {0}={1} → 400 indicando {0}")
    @CsvSource({"lat,90.5", "lat,-91", "lat,abc", "radius,0", "radius,-5", "radius,5001", "limit,0", "limit,2001",
            "district_id,11", "district_id,1", "neighbourhood_id,3"})
    void outOfRange(String name, String value) {
        Map<String, String> params = new java.util.HashMap<>(Map.of(name, value));
        if (name.equals("lat")) {
            params.put("lon", "2.17");
        }
        if (name.equals("radius")) {
            params.put("lat", "41.38");
            params.put("lon", "2.17");
        }

        assertThat(invalid(params).getInvalidParams()).extracting(InvalidParam::name).contains(name);
    }

    @Test
    @DisplayName("KAN-8 CA3: longitud fuera de [-180, 180]")
    void longitudeOutOfRange() {
        assertThat(invalid(Map.of("lat", "41.38", "lon", "181")).getInvalidParams())
                .extracting(InvalidParam::name).containsExactly("lon");
    }

    @Test
    @DisplayName("lat sin lon, radius sin punto y bbox con lat/lon son combinaciones inválidas")
    void invalidCombinations() {
        assertThat(invalid(Map.of("lat", "41.38")).getMessage()).isEqualTo("Invalid combination of spatial filters.");
        assertThat(invalid(Map.of("lon", "2.17")).getInvalidParams()).extracting(InvalidParam::name).containsExactly("lat");
        assertThat(invalid(Map.of("radius", "300")).getInvalidParams()).extracting(InvalidParam::name).containsExactly("radius");
        assertThat(invalid(Map.of("bbox", "2.1,41.3,2.2,41.4", "lat", "41.38", "lon", "2.17")).getInvalidParams())
                .extracting(InvalidParam::name).contains("bbox");
    }

    @Test
    @DisplayName("bbox malformada o con mínimos mayores que máximos se rechaza")
    void invalidBbox() {
        assertThat(invalid(Map.of("bbox", "2.1,41.3,2.2")).getInvalidParams()).hasSize(1);
        assertThat(invalid(Map.of("bbox", "2.2,41.3,2.1,41.4")).getInvalidParams()).hasSize(1);
        assertThat(invalid(Map.of("bbox", "a,b,c,d")).getInvalidParams()).hasSize(1);
    }

    @Test
    @DisplayName("Valores por defecto: radio 500 y límite 50; los filtros se conservan para los enlaces")
    void defaultsAndFilters() {
        var parsed = SearchParamsParser.parse(Map.of("lat", "41.3805", "lon", "2.188", "district_id", "01"));

        assertThat(parsed.criteria().radius()).isEqualTo(500);
        assertThat(parsed.criteria().limit()).isEqualTo(50);
        assertThat(parsed.criteria().isProximity()).isTrue();
        assertThat(parsed.filters()).containsExactly(Map.entry("lat", "41.3805"), Map.entry("lon", "2.188"),
                Map.entry("district_id", "01"));
    }

    @Test
    @DisplayName("limit=2000 es el máximo permitido y bbox válida se acepta")
    void maxLimit() {
        var parsed = SearchParamsParser.parse(Map.of("limit", "2000", "bbox", "2.17,41.37,2.20,41.39"));

        assertThat(parsed.criteria().limit()).isEqualTo(2000);
        assertThat(parsed.criteria().bbox().maxLat()).isEqualTo(41.39);
    }

    @Test
    @DisplayName("Un cursor ajeno se rechaza con 400")
    void foreignCursor() {
        assertThatThrownBy(() -> SearchParamsParser.parse(Map.of("cursor", "no-es-un-cursor")))
                .isInstanceOf(InvalidRequestException.class);
    }
}
