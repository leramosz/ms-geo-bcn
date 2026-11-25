package com.geobcn.service.fountains.drinkingwater.domain;

import java.util.List;

/** Petición inválida (400): lleva el detalle y los parámetros erróneos. */
public class InvalidRequestException extends RuntimeException {

    private final transient List<InvalidParam> invalidParams;

    public InvalidRequestException(String detail, List<InvalidParam> invalidParams) {
        super(detail);
        this.invalidParams = List.copyOf(invalidParams);
    }

    public List<InvalidParam> getInvalidParams() {
        return invalidParams;
    }
}
