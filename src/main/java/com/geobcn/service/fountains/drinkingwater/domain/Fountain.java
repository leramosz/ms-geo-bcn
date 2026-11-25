package com.geobcn.service.fountains.drinkingwater.domain;

/** Fuente de agua potable. {@code distance} (metros enteros) solo se informa en búsquedas por cercanía. */
public record Fountain(
        String id,
        String name,
        Status status,
        String street,
        String streetNumber,
        double latitude,
        double longitude,
        District district,
        Neighbourhood neighbourhood,
        Integer distance) {

    public enum Status { UNKNOWN, OPERATIONAL, OUT_OF_SERVICE }

    public record District(String id, String name) { }

    public record Neighbourhood(String id, String name) { }
}
