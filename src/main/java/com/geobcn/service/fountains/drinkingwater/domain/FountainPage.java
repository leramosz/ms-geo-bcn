package com.geobcn.service.fountains.drinkingwater.domain;

import java.util.List;

public record FountainPage(List<Fountain> items, boolean hasPrevious, boolean hasNext) { }
