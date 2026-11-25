package com.geobcn.service.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Una ruta con segmentos vacíos ("//geo-bcn/v2/...") no existe: se responde 404 Problem JSON.
 * Sin este filtro Spring lanza un 500 al no poder casar el context path con la ruta.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class EmptyPathSegmentFilter extends OncePerRequestFilter {

    private final ObjectMapper mapper;

    public EmptyPathSegmentFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().contains("//")) {
            ProblemResponses.write(mapper, response, 404, "not-found", "Resource not found",
                    "Resource does not exist.", null);
            return;
        }
        chain.doFilter(request, response);
    }
}
