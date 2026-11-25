package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geobcn.service.fountains.drinkingwater.domain.Cursor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

/** Cursor opaco: base64url sin relleno de {"d":distancia,"id":"NN-NNN","b":1}. */
public final class CursorCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern ID = Pattern.compile("^(?!00)\\d{2}-(?!000)\\d{3}$");

    private CursorCodec() { }

    public static String encode(Cursor cursor) {
        StringBuilder json = new StringBuilder("{");
        if (cursor.distance() != null) {
            json.append("\"d\":").append(cursor.distance()).append(',');
        }
        json.append("\"id\":\"").append(cursor.id()).append('"');
        if (cursor.backward()) {
            json.append(",\"b\":1");
        }
        json.append('}');
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** @throws IllegalArgumentException si el cursor no es uno emitido por esta API */
    public static Cursor decode(String value) {
        try {
            JsonNode node = MAPPER.readTree(Base64.getUrlDecoder().decode(value));
            JsonNode id = node.get("id");
            if (id == null || !id.isTextual() || !ID.matcher(id.asText()).matches()) {
                throw new IllegalArgumentException("invalid cursor");
            }
            JsonNode d = node.get("d");
            if (d != null && !d.canConvertToInt()) {
                throw new IllegalArgumentException("invalid cursor");
            }
            return new Cursor(d == null ? null : d.asInt(), id.asText(), node.has("b"));
        } catch (java.io.IOException | RuntimeException e) {
            throw new IllegalArgumentException("invalid cursor", e);
        }
    }
}
