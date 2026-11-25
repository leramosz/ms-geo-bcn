package com.geobcn.service.fountains.drinkingwater.domain;

import java.util.List;

public record DistrictWithNeighbourhoods(String id, String name, List<Fountain.Neighbourhood> neighbourhoods) { }
