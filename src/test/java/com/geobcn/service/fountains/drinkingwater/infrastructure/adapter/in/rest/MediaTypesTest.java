package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MediaTypesTest {

    private static final List<MediaType> SUPPORTED = FountainsController.SUPPORTED;

    @Test
    void defaultsToJson() {
        assertThat(MediaTypes.negotiate(null, SUPPORTED)).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(MediaTypes.negotiate("*/*", SUPPORTED)).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void choosesGeoJsonWhenRequested() {
        assertThat(MediaTypes.negotiate("application/geo+json", SUPPORTED)).isEqualTo(MediaTypes.GEO_JSON);
        assertThat(MediaTypes.negotiate("application/json;q=0.5, application/geo+json", SUPPORTED))
                .isEqualTo(MediaTypes.GEO_JSON);
    }

    @Test
    void rejectsUnsupported() {
        assertThat(MediaTypes.negotiate("text/html", SUPPORTED)).isNull();
        assertThat(MediaTypes.negotiate("not a media type", SUPPORTED)).isNull();
    }
}
