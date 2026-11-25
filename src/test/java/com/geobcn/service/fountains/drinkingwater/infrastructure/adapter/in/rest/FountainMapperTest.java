package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest;

import com.geobcn.service.fountains.drinkingwater.domain.Fountain;
import com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest.ApiDtos.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FountainMapperTest {

    private static Fountain santCarles() {
        return new Fountain("01-004", "Sant Carles", Fountain.Status.UNKNOWN, "C COMTE DE SANTA CLARA", "11",
                41.379145587, 2.188964950, new Fountain.District("01", "Ciutat Vella"),
                new Fountain.Neighbourhood("03", "la Barceloneta"), null);
    }

    @Test
    @DisplayName("KAN-6 CA2: GeoJSON con Point [lon, lat] en WGS84")
    void geoJsonPointIsLonLat() {
        FeatureDto feature = FountainMapper.toFeature(santCarles());

        assertThat(feature.type()).isEqualTo("Feature");
        assertThat(feature.geometry().type()).isEqualTo("Point");
        assertThat(feature.geometry().coordinates()).containsExactly(2.188964950, 41.379145587);
        assertThat(feature.id()).isEqualTo("01-004");
    }

    @Test
    @DisplayName("KAN-7 CA1: la ficha incluye código, nombre, dirección, barrio, distrito y coordenadas")
    void fichaContent() {
        FountainDto dto = FountainMapper.toDto(santCarles());

        assertThat(dto.id()).isEqualTo("01-004");
        assertThat(dto.name()).isEqualTo("Sant Carles");
        assertThat(dto.address().formatted()).isEqualTo("C COMTE DE SANTA CLARA, 11");
        assertThat(dto.neighbourhood().name()).isEqualTo("la Barceloneta");
        assertThat(dto.district().name()).isEqualTo("Ciutat Vella");
        assertThat(dto.location()).isEqualTo(new LocationDto(41.379145587, 2.188964950));
    }

    @Test
    @DisplayName("KAN-7 CA3: sin número de calle la dirección es solo la calle; el estado nunca es vacío (UNKNOWN)")
    void missingData() {
        Fountain f = new Fountain("04-090", null, Fountain.Status.UNKNOWN, "CTRA AIGÜES", null,
                41.389951461, 2.100113086, new Fountain.District("04", "Les Corts"), null, null);

        FountainDto dto = FountainMapper.toDto(f);

        assertThat(dto.address().formatted()).isEqualTo("CTRA AIGÜES");
        assertThat(dto.address().streetNumber()).isNull();
        assertThat(dto.status()).isEqualTo("UNKNOWN");
        assertThat(dto.neighbourhood()).isNull();
    }

    @Test
    @DisplayName("El marcador SN (sin número) y los textos vacíos se devuelven como null")
    void placeholders() {
        Fountain sn = new Fountain("01-001", "x", Fountain.Status.UNKNOWN, "C X", "SN", 1, 1,
                new Fountain.District("01", "d"), null, null);
        Fountain noStreet = new Fountain("08-322", null, Fountain.Status.UNKNOWN, " ", null, 1, 1,
                new Fountain.District("08", "d"), null, null);

        assertThat(FountainMapper.address(sn)).isEqualTo(new AddressDto("C X", null, "C X"));
        assertThat(FountainMapper.address(noStreet)).isEqualTo(new AddressDto(null, null, null));
    }
}
