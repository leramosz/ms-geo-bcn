package com.geobcn.service.fountains.drinkingwater.domain;

/**
 * Clave de la última fuente vista: (distance, id) en búsquedas por cercanía, solo id en el resto.
 * {@code backward} pide la página anterior a esa fuente.
 */
public record Cursor(Integer distance, String id, boolean backward) { }
