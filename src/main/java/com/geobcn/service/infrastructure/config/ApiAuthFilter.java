package com.geobcn.service.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParseException;
import java.util.Date;

/**
 * Autenticación: cabecera X-Api-Key o Authorization: Bearer (JWT HS256 con "exp" vigente).
 * Sin OAuth2: el JWT se verifica con un secreto compartido. La especificación OpenAPI es pública.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiAuthFilter extends OncePerRequestFilter {

    private final SecurityProperties properties;
    private final ObjectMapper mapper;

    public ApiAuthFilter(SecurityProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().endsWith("/openapi.yaml");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (validApiKey(request.getHeader("X-Api-Key")) || validJwt(request.getHeader("Authorization"))) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("WWW-Authenticate", "Bearer");
        ProblemResponses.write(mapper, response, 401, "unauthorized", "Unauthorized",
                "Unauthorized to access the resource.", null);
    }

    private boolean validApiKey(String key) {
        if (key == null || properties.apiKeys() == null) {
            return false;
        }
        byte[] given = key.getBytes(StandardCharsets.UTF_8);
        return properties.apiKeys().stream()
                .anyMatch(k -> MessageDigest.isEqual(k.getBytes(StandardCharsets.UTF_8), given));
    }

    private boolean validJwt(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        try {
            SignedJWT jwt = SignedJWT.parse(authorization.substring(7).trim());
            Date exp = jwt.getJWTClaimsSet().getExpirationTime();
            return JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    && jwt.verify(new MACVerifier(properties.jwtSecret().getBytes(StandardCharsets.UTF_8)))
                    && exp != null && exp.after(new Date());
        } catch (ParseException | JOSEException e) {
            return false;
        }
    }
}
