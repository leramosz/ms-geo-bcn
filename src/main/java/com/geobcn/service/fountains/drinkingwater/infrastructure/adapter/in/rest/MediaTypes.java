package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

import java.util.List;

/** Negociación mínima: application/json por defecto, application/geo+json si el cliente lo prefiere. */
public final class MediaTypes {

    public static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");
    public static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

    private MediaTypes() { }

    /** @return el tipo elegido entre {@code supported}, o null si ninguno es aceptable */
    public static MediaType negotiate(String acceptHeader, List<MediaType> supported) {
        if (acceptHeader == null || acceptHeader.isBlank()) {
            return supported.get(0);
        }
        List<MediaType> accepted;
        try {
            accepted = MediaType.parseMediaTypes(acceptHeader);
        } catch (InvalidMediaTypeException e) {
            return null;
        }
        MediaType.sortBySpecificityAndQuality(accepted);
        for (MediaType a : accepted) {
            if (a.getQualityValue() == 0) {
                continue;
            }
            for (MediaType s : supported) {
                if (a.isCompatibleWith(s)) {
                    return s;
                }
            }
        }
        return null;
    }
}
