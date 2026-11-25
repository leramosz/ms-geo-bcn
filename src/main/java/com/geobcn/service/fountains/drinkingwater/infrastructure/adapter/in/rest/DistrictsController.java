package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest;

import com.geobcn.service.fountains.drinkingwater.application.FountainService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/districts")
public class DistrictsController {

    private final FountainService service;

    public DistrictsController(FountainService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Object> list(@RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        if (MediaTypes.negotiate(accept, List.of(MediaType.APPLICATION_JSON)) == null) {
            throw new NotAcceptableException("Supported media type is application/json.");
        }
        return FountainsController.cacheable(MediaType.APPLICATION_JSON, FountainMapper.toDto(service.listDistricts()));
    }
}
