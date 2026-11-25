package com.geobcn.service.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthAndFlowIdFiltersTest {

    private static final String SECRET = "unit-test-secret-with-at-least-32-bytes!!";
    private final ApiAuthFilter auth = new ApiAuthFilter(new SecurityProperties(List.of("k1", "k2"), SECRET), new ObjectMapper());
    private final FlowIdFilter flowId = new FlowIdFilter(new ObjectMapper());

    private static String jwt(String secret, Date exp) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().subject("test");
        if (exp != null) {
            claims.expirationTime(exp);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private MockHttpServletResponse call(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        auth.doFilter(request, response, chain);
        return response;
    }

    private static MockHttpServletRequest request(String header, String value) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/geo-bcn/v2/drinking-water-fountains");
        if (header != null) {
            r.addHeader(header, value);
        }
        return r;
    }

    @Test
    @DisplayName("API key válida pasa; inválida o ausente es 401 Problem JSON")
    void apiKey() throws Exception {
        assertThat(call(request("X-Api-Key", "k2")).getStatus()).isEqualTo(200);
        MockHttpServletResponse bad = call(request("X-Api-Key", "nope"));
        assertThat(bad.getStatus()).isEqualTo(401);
        assertThat(bad.getContentType()).startsWith("application/problem+json");
        assertThat(call(request(null, null)).getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("JWT HS256 vigente pasa; caducado, sin exp, firmado con otro secreto o basura es 401")
    void bearer() throws Exception {
        Date future = new Date(System.currentTimeMillis() + 60_000);
        assertThat(call(request("Authorization", "Bearer " + jwt(SECRET, future))).getStatus()).isEqualTo(200);
        assertThat(call(request("Authorization", "Bearer " + jwt(SECRET, new Date(System.currentTimeMillis() - 1000)))).getStatus()).isEqualTo(401);
        assertThat(call(request("Authorization", "Bearer " + jwt(SECRET, null))).getStatus()).isEqualTo(401);
        assertThat(call(request("Authorization", "Bearer " + jwt("another-secret-with-at-least-32-bytes!!!", future))).getStatus()).isEqualTo(401);
        assertThat(call(request("Authorization", "Bearer abc.def.ghi")).getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("El contrato openapi.yaml es público")
    void specIsPublic() throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/geo-bcn/v2/openapi.yaml");
        assertThat(call(r).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("X-Flow-ID: se repite si es válido, se genera si falta y 400 si es inválido")
    void flowIdHeader() throws Exception {
        MockHttpServletResponse ok = new MockHttpServletResponse();
        flowId.doFilter(request("X-Flow-ID", "abc-123_=/+"), ok, new MockFilterChain());
        assertThat(ok.getHeader("X-Flow-ID")).isEqualTo("abc-123_=/+");

        MockHttpServletResponse generated = new MockHttpServletResponse();
        flowId.doFilter(request(null, null), generated, new MockFilterChain());
        assertThat(generated.getHeader("X-Flow-ID")).hasSize(36);

        MockHttpServletResponse bad = new MockHttpServletResponse();
        flowId.doFilter(request("X-Flow-ID", "no válido!"), bad, new MockFilterChain());
        assertThat(bad.getStatus()).isEqualTo(400);
        assertThat(bad.getHeader("X-Flow-ID")).isNotBlank();
    }

    @Test
    @DisplayName("Una ruta con segmentos vacíos (//) es 404 Problem JSON, no 500")
    void doubleSlashIsNotFound() throws Exception {
        EmptyPathSegmentFilter filter = new EmptyPathSegmentFilter(new ObjectMapper());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "//geo-bcn/v2/drinking-water-fountains"), response, chain);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(chain.getRequest()).isNull();

        MockHttpServletResponse ok = new MockHttpServletResponse();
        MockFilterChain okChain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/geo-bcn/v2/drinking-water-fountains"), ok, okChain);
        assertThat(okChain.getRequest()).isNotNull();
    }
}
