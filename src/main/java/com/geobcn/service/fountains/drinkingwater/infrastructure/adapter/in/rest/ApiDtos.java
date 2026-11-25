package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Representaciones JSON del contrato openapi.yaml (snake_case lo aplica Jackson). */
public final class ApiDtos {

    private ApiDtos() { }

    public record LocationDto(double latitude, double longitude) { }

    public record DistrictDto(String id, String name) { }

    public record NeighbourhoodDto(String id, String name) { }

    public record AddressDto(String street, String streetNumber, String formatted) { }

    public record FountainDto(
            String id, String name, String status, AddressDto address, LocationDto location,
            DistrictDto district, NeighbourhoodDto neighbourhood,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer distance) { }

    public record FountainPageDto(
            String self,
            @JsonInclude(JsonInclude.Include.NON_NULL) String first,
            @JsonInclude(JsonInclude.Include.NON_NULL) String prev,
            @JsonInclude(JsonInclude.Include.NON_NULL) String next,
            List<FountainDto> items) { }

    public record DistrictWithNeighbourhoodsDto(String id, String name, List<NeighbourhoodDto> neighbourhoods) { }

    public record DistrictListDto(List<DistrictWithNeighbourhoodsDto> items) { }

    public record PointDto(String type, List<Double> coordinates) { }

    public record FeaturePropertiesDto(
            String name, String status, AddressDto address, DistrictDto district, NeighbourhoodDto neighbourhood,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer distance) { }

    public record FeatureDto(String type, String id, PointDto geometry, FeaturePropertiesDto properties) { }

    public record FeatureCollectionDto(
            String type,
            String self,
            @JsonInclude(JsonInclude.Include.NON_NULL) String first,
            @JsonInclude(JsonInclude.Include.NON_NULL) String prev,
            @JsonInclude(JsonInclude.Include.NON_NULL) String next,
            List<FeatureDto> features) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProblemDto(String type, String title, int status, String detail, List<InvalidParamDto> invalidParams) { }

    public record InvalidParamDto(String name, String reason) { }
}
