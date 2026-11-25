package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest;

import com.geobcn.service.fountains.drinkingwater.domain.DistrictWithNeighbourhoods;
import com.geobcn.service.fountains.drinkingwater.domain.Fountain;
import com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest.ApiDtos.*;

import java.util.List;

public final class FountainMapper {

    /** Marcador del dataset para "sin número". */
    private static final String NO_NUMBER = "SN";

    private FountainMapper() { }

    public static FountainDto toDto(Fountain f) {
        return new FountainDto(f.id(), f.name(), f.status().name(), address(f),
                new LocationDto(f.latitude(), f.longitude()), district(f), neighbourhood(f), f.distance());
    }

    /** GeoJSON (RFC 7946): coordenadas [lon, lat]. */
    public static FeatureDto toFeature(Fountain f) {
        return new FeatureDto("Feature", f.id(),
                new PointDto("Point", List.of(f.longitude(), f.latitude())),
                new FeaturePropertiesDto(f.name(), f.status().name(), address(f), district(f), neighbourhood(f),
                        f.distance()));
    }

    public static DistrictListDto toDto(List<DistrictWithNeighbourhoods> districts) {
        return new DistrictListDto(districts.stream()
                .map(d -> new DistrictWithNeighbourhoodsDto(d.id(), d.name(),
                        d.neighbourhoods().stream().map(n -> new NeighbourhoodDto(n.id(), n.name())).toList()))
                .toList());
    }

    static AddressDto address(Fountain f) {
        String street = blankToNull(f.street());
        String number = blankToNull(f.streetNumber());
        if (NO_NUMBER.equalsIgnoreCase(number)) {
            number = null;
        }
        String formatted = street == null ? null : number == null ? street : street + ", " + number;
        return new AddressDto(street, number, formatted);
    }

    private static DistrictDto district(Fountain f) {
        return new DistrictDto(f.district().id(), f.district().name());
    }

    private static NeighbourhoodDto neighbourhood(Fountain f) {
        return f.neighbourhood() == null ? null : new NeighbourhoodDto(f.neighbourhood().id(), f.neighbourhood().name());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
