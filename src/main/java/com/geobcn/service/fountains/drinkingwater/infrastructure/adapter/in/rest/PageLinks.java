package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest;

import com.geobcn.service.fountains.drinkingwater.domain.Cursor;
import com.geobcn.service.fountains.drinkingwater.domain.Fountain;
import com.geobcn.service.fountains.drinkingwater.domain.FountainPage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Enlaces self/first/prev/next que conservan los filtros aplicados. */
public record PageLinks(String self, String first, String prev, String next) {

    public static PageLinks of(String path, Map<String, String> filters, String requestCursor,
                               FountainPage page, boolean proximity) {
        String base = path + query(filters, null);
        List<Fountain> items = page.items();
        String self = requestCursor == null ? base : path + query(filters, requestCursor);
        String prev = null;
        String next = null;
        if (!items.isEmpty()) {
            if (page.hasPrevious()) {
                prev = path + query(filters, CursorCodec.encode(cursorOf(items.get(0), proximity, true)));
            }
            if (page.hasNext()) {
                next = path + query(filters, CursorCodec.encode(cursorOf(items.get(items.size() - 1), proximity, false)));
            }
        }
        return new PageLinks(self, base, prev, next);
    }

    private static Cursor cursorOf(Fountain f, boolean proximity, boolean backward) {
        return new Cursor(proximity ? f.distance() : null, f.id(), backward);
    }

    private static String query(Map<String, String> filters, String cursor) {
        String q = filters.entrySet().stream()
                .map(e -> e.getKey() + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        if (cursor != null) {
            q = q.isEmpty() ? "cursor=" + cursor : q + "&cursor=" + cursor;
        }
        return q.isEmpty() ? "" : "?" + q;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("%2C", ",");
    }
}
