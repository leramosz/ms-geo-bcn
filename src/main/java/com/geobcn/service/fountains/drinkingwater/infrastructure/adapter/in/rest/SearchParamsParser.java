package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest;

import com.geobcn.service.fountains.drinkingwater.domain.*;

import java.util.*;
import java.util.regex.Pattern;

/** Valida los parámetros de búsqueda y construye el criterio. Reúne todos los errores, no solo el primero. */
public final class SearchParamsParser {

    public static final int DEFAULT_RADIUS = 500;
    public static final int MAX_RADIUS = 5000;
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 2000;
    private static final Pattern DISTRICT = Pattern.compile("^(0[1-9]|10)$");
    private static final Pattern NEIGHBOURHOOD = Pattern.compile("^\\d{2}$");
    private static final String INVALID_COMBINATION = "Invalid combination of spatial filters.";
    private static final String INVALID_PARAMS = "One or more parameters are invalid.";

    /** Criterio y filtros tal como los envió el cliente (para reconstruir los enlaces). */
    public record Parsed(SearchCriteria criteria, Map<String, String> filters) { }

    private SearchParamsParser() { }

    public static Parsed parse(Map<String, String> raw) {
        List<InvalidParam> errors = new ArrayList<>();
        boolean combination = false;

        String latRaw = raw.get("lat");
        String lonRaw = raw.get("lon");
        String radiusRaw = raw.get("radius");
        String bboxRaw = raw.get("bbox");

        Double lat = number("lat", latRaw, -90, 90, errors);
        Double lon = number("lon", lonRaw, -180, 180, errors);
        if (latRaw != null && lonRaw == null) {
            errors.add(new InvalidParam("lon", "lon is required when lat is present."));
            combination = true;
        }
        if (lonRaw != null && latRaw == null) {
            errors.add(new InvalidParam("lat", "lat is required when lon is present."));
            combination = true;
        }
        Integer radius = integer("radius", radiusRaw, 1, MAX_RADIUS, errors);
        if (radiusRaw != null && (latRaw == null || lonRaw == null)) {
            errors.add(new InvalidParam("radius", "radius requires lat and lon."));
            combination = true;
        }
        BoundingBox bbox = bboxRaw == null ? null : bbox(bboxRaw, errors);
        if (bboxRaw != null && (latRaw != null || lonRaw != null)) {
            errors.add(new InvalidParam("bbox", "bbox cannot be combined with lat/lon."));
            combination = true;
        }
        String district = pattern("district_id", raw.get("district_id"), DISTRICT, "must be a district code between 01 and 10.", errors);
        String neighbourhood = pattern("neighbourhood_id", raw.get("neighbourhood_id"), NEIGHBOURHOOD, "must be a two-digit code.", errors);
        Integer limit = integer("limit", raw.get("limit"), 1, MAX_LIMIT, errors);
        Cursor cursor = null;
        String cursorRaw = raw.get("cursor");
        if (cursorRaw != null) {
            try {
                cursor = CursorCodec.decode(cursorRaw);
            } catch (IllegalArgumentException e) {
                errors.add(new InvalidParam("cursor", "is not a valid cursor."));
            }
        }

        if (!errors.isEmpty()) {
            throw new InvalidRequestException(combination ? INVALID_COMBINATION : INVALID_PARAMS, errors);
        }

        Map<String, String> filters = new LinkedHashMap<>();
        for (String key : List.of("lat", "lon", "radius", "bbox", "district_id", "neighbourhood_id", "limit")) {
            if (raw.get(key) != null) {
                filters.put(key, raw.get(key).trim());
            }
        }
        SearchCriteria criteria = new SearchCriteria(lat, lon, radius == null ? DEFAULT_RADIUS : radius, bbox,
                district, neighbourhood, limit == null ? DEFAULT_LIMIT : limit, cursor);
        return new Parsed(criteria, filters);
    }

    private static Double number(String name, String value, double min, double max, List<InvalidParam> errors) {
        if (value == null) {
            return null;
        }
        try {
            double d = Double.parseDouble(value.trim());
            if (!Double.isFinite(d)) {
                throw new NumberFormatException();
            }
            if (d < min || d > max) {
                errors.add(new InvalidParam(name, "must be between " + (int) min + " and " + (int) max + "."));
                return null;
            }
            return d;
        } catch (NumberFormatException e) {
            errors.add(new InvalidParam(name, "must be a number."));
            return null;
        }
    }

    private static Integer integer(String name, String value, int min, int max, List<InvalidParam> errors) {
        if (value == null) {
            return null;
        }
        try {
            int i = Integer.parseInt(value.trim());
            if (i < min || i > max) {
                errors.add(new InvalidParam(name, "must be between " + min + " and " + max + "."));
                return null;
            }
            return i;
        } catch (NumberFormatException e) {
            errors.add(new InvalidParam(name, "must be an integer."));
            return null;
        }
    }

    private static String pattern(String name, String value, Pattern pattern, String reason, List<InvalidParam> errors) {
        if (value == null) {
            return null;
        }
        if (!pattern.matcher(value.trim()).matches()) {
            errors.add(new InvalidParam(name, reason));
            return null;
        }
        return value.trim();
    }

    private static BoundingBox bbox(String value, List<InvalidParam> errors) {
        String[] parts = value.split(",", -1);
        if (parts.length != 4) {
            errors.add(new InvalidParam("bbox", "must be min_lon,min_lat,max_lon,max_lat."));
            return null;
        }
        List<InvalidParam> local = new ArrayList<>();
        Double minLon = number("bbox", parts[0], -180, 180, local);
        Double minLat = number("bbox", parts[1], -90, 90, local);
        Double maxLon = number("bbox", parts[2], -180, 180, local);
        Double maxLat = number("bbox", parts[3], -90, 90, local);
        if (!local.isEmpty()) {
            errors.add(new InvalidParam("bbox", "must be four WGS84 numbers: min_lon,min_lat,max_lon,max_lat."));
            return null;
        }
        if (minLon > maxLon || minLat > maxLat) {
            errors.add(new InvalidParam("bbox", "minimum values must not exceed maximum values."));
            return null;
        }
        return new BoundingBox(minLon, minLat, maxLon, maxLat);
    }
}
