package com.geobcn.service.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Credenciales de acceso: claves API y secreto HS256 del JWT (valores de desarrollo por defecto). */
@ConfigurationProperties("geobcn.security")
public record SecurityProperties(List<String> apiKeys, String jwtSecret) { }
