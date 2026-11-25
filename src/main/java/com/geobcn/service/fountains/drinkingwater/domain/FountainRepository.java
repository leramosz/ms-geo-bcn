package com.geobcn.service.fountains.drinkingwater.domain;

import java.util.List;
import java.util.Optional;

/** Puerto de salida: consultas sobre fuentes y catálogo de distritos y barrios. */
public interface FountainRepository {

    /** Devuelve hasta {@code limit} fuentes en el orden de la página y si hay más en esa dirección. */
    Slice search(SearchCriteria criteria);

    Optional<Fountain> findById(String id);

    List<DistrictWithNeighbourhoods> listDistricts();

    boolean districtExists(String districtId);

    boolean neighbourhoodExists(String neighbourhoodId);

    /** Hay al menos una fuente de ese barrio en ese distrito (derivado del dataset). */
    boolean neighbourhoodInDistrict(String neighbourhoodId, String districtId);

    record Slice(List<Fountain> items, boolean more) { }
}
