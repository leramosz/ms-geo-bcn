package com.geobcn.service.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest.ApiDtos.InvalidParamDto;
import com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest.ApiDtos.ProblemDto;
import com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest.MediaTypes;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/** Escribe un Problem JSON desde un filtro (fuera del alcance de @RestControllerAdvice). */
final class ProblemResponses {

    private ProblemResponses() { }

    static void write(ObjectMapper mapper, HttpServletResponse response, int status, String slug, String title,
                      String detail, List<InvalidParamDto> params) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaTypes.PROBLEM_JSON.toString());
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(),
                new ProblemDto("/problems/" + slug, title, status, detail, params));
    }
}
