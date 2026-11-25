package com.geobcn.service.fountains.drinkingwater.domain;

/** Caja en WGS84: min_lon, min_lat, max_lon, max_lat. */
public record BoundingBox(double minLon, double minLat, double maxLon, double maxLat) { }
