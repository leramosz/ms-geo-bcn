package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest;

public class NotAcceptableException extends RuntimeException {

    public NotAcceptableException(String detail) {
        super(detail);
    }
}
