package com.geobcn.service.fountains.drinkingwater.application;

import com.geobcn.service.fountains.drinkingwater.domain.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FountainService {

    private final FountainRepository repository;

    public FountainService(FountainRepository repository) {
        this.repository = repository;
    }

    public FountainPage search(SearchCriteria criteria) {
        validateCatalogReferences(criteria.districtId(), criteria.neighbourhoodId());
        FountainRepository.Slice slice = repository.search(criteria);
        Cursor cursor = criteria.cursor();
        if (cursor == null) {
            return new FountainPage(slice.items(), false, slice.more());
        }
        return cursor.backward()
                ? new FountainPage(slice.items(), slice.more(), true)
                : new FountainPage(slice.items(), true, slice.more());
    }

    public Fountain get(String id) {
        return repository.findById(id).orElseThrow(() -> new FountainNotFoundException(id));
    }

    public List<DistrictWithNeighbourhoods> listDistricts() {
        return repository.listDistricts();
    }

    /**
     * Códigos inexistentes y barrio ajeno al distrito son 400, no una lista vacía.
     * La pertenencia se deriva de los datos: los barrios 18, 19, 29 y 37 aparecen en dos distritos
     * y se aceptan con ambos hasta que se decida la regla de negocio.
     */
    private void validateCatalogReferences(String districtId, String neighbourhoodId) {
        if (districtId != null && !repository.districtExists(districtId)) {
            throw invalid("District " + districtId + " does not exist.", "district_id", "unknown district code.");
        }
        if (neighbourhoodId != null && !repository.neighbourhoodExists(neighbourhoodId)) {
            throw invalid("Neighbourhood " + neighbourhoodId + " does not exist.", "neighbourhood_id",
                    "unknown neighbourhood code.");
        }
        if (districtId != null && neighbourhoodId != null
                && !repository.neighbourhoodInDistrict(neighbourhoodId, districtId)) {
            throw invalid("Neighbourhood " + neighbourhoodId + " does not belong to district " + districtId + ".",
                    "neighbourhood_id", "does not belong to district_id " + districtId + ".");
        }
    }

    private static InvalidRequestException invalid(String detail, String name, String reason) {
        return new InvalidRequestException(detail, List.of(new InvalidParam(name, reason)));
    }
}
