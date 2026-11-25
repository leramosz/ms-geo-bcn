package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest;

import com.geobcn.service.fountains.drinkingwater.application.FountainService;
import com.geobcn.service.fountains.drinkingwater.domain.FountainPage;
import com.geobcn.service.fountains.drinkingwater.domain.InvalidParam;
import com.geobcn.service.fountains.drinkingwater.domain.InvalidRequestException;
import com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest.ApiDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/drinking-water-fountains")
public class FountainsController {

    static final List<MediaType> SUPPORTED = List.of(MediaType.APPLICATION_JSON, MediaTypes.GEO_JSON);
    static final String NOT_ACCEPTABLE = "Supported media types are application/json and application/geo+json.";
    private static final Pattern ID = Pattern.compile("^(?!00)\\d{2}-(?!000)\\d{3}$");

    private final FountainService service;

    public FountainsController(FountainService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Object> search(@RequestParam Map<String, String> params,
                                         @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
                                         HttpServletRequest request) {
        MediaType type = negotiate(accept);
        SearchParamsParser.Parsed parsed = SearchParamsParser.parse(params);
        FountainPage page = service.search(parsed.criteria());
        String path = request.getContextPath() + "/drinking-water-fountains";
        PageLinks links = PageLinks.of(path, parsed.filters(), params.get("cursor"), page,
                parsed.criteria().isProximity());

        Object body = MediaTypes.GEO_JSON.equals(type)
                ? new FeatureCollectionDto("FeatureCollection", links.self(), links.first(), links.prev(), links.next(),
                page.items().stream().map(FountainMapper::toFeature).toList())
                : new FountainPageDto(links.self(), links.first(), links.prev(), links.next(),
                page.items().stream().map(FountainMapper::toDto).toList());
        return cacheable(type, body);
    }

    @GetMapping("/{drinking_water_fountain_id}")
    public ResponseEntity<Object> get(@PathVariable("drinking_water_fountain_id") String id,
                                      @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        MediaType type = negotiate(accept);
        if (!ID.matcher(id).matches()) {
            throw new InvalidRequestException("One or more parameters are invalid.",
                    List.of(new InvalidParam("drinking_water_fountain_id", "must match NN-NNN, e.g. 01-004.")));
        }
        var fountain = service.get(id);
        Object body = MediaTypes.GEO_JSON.equals(type)
                ? FountainMapper.toFeature(fountain) : FountainMapper.toDto(fountain);
        return cacheable(type, body);
    }

    private static MediaType negotiate(String accept) {
        MediaType type = MediaTypes.negotiate(accept, SUPPORTED);
        if (type == null) {
            throw new NotAcceptableException(NOT_ACCEPTABLE);
        }
        return type;
    }

    static ResponseEntity<Object> cacheable(MediaType type, Object body) {
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).mustRevalidate().cachePrivate())
                .header(HttpHeaders.VARY, "accept, accept-encoding")
                .body(body);
    }
}
