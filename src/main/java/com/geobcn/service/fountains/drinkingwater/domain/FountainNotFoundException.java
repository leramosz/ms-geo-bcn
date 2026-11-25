package com.geobcn.service.fountains.drinkingwater.domain;

public class FountainNotFoundException extends RuntimeException {

    public FountainNotFoundException(String id) {
        super("Drinking water fountain " + id + " does not exist.");
    }
}
