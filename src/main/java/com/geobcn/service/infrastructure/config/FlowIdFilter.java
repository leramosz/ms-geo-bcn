package com.geobcn.service.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest.ApiDtos.InvalidParamDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** X-Flow-ID: se valida, se genera si falta, se escribe en el log (MDC) y se devuelve siempre. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FlowIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Flow-ID";
    private static final Pattern VALID = Pattern.compile("^[a-zA-Z0-9/+_=-]{1,128}$");

    private final ObjectMapper mapper;

    public FlowIdFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String flowId = request.getHeader(HEADER);
        if (flowId == null || flowId.isBlank()) {
            flowId = UUID.randomUUID().toString();
        } else if (!VALID.matcher(flowId).matches()) {
            response.setHeader(HEADER, UUID.randomUUID().toString());
            ProblemResponses.write(mapper, response, 400, "invalid-parameter", "Invalid parameter",
                    "One or more parameters are invalid.",
                    List.of(new InvalidParamDto(HEADER, "must be at most 128 characters from [a-zA-Z0-9/+_=-].")));
            return;
        }
        response.setHeader(HEADER, flowId);
        MDC.put("flowId", flowId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("flowId");
        }
    }
}
