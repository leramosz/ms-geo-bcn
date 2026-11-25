package com.geobcn.service.fountains.drinkingwater.domain;

public record SearchCriteria(
        Double lat,
        Double lon,
        int radius,
        BoundingBox bbox,
        String districtId,
        String neighbourhoodId,
        int limit,
        Cursor cursor) {

    public boolean isProximity() {
        return lat != null && lon != null;
    }
}
