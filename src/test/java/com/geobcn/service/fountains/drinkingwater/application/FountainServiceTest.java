package com.geobcn.service.fountains.drinkingwater.application;

import com.geobcn.service.fountains.drinkingwater.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FountainServiceTest {

    @Mock
    FountainRepository repository;
    FountainService service;

    @BeforeEach
    void setUp() {
        service = new FountainService(repository);
    }

    private static SearchCriteria criteria(String district, String neighbourhood, Cursor cursor) {
        return new SearchCriteria(null, null, 500, null, district, neighbourhood, 10, cursor);
    }

    private static Fountain fountain(String id) {
        return new Fountain(id, "n", Fountain.Status.UNKNOWN, "C X", "1", 41.38, 2.17,
                new Fountain.District("01", "Ciutat Vella"), null, null);
    }

    @Test
    @DisplayName("KAN-9 CA4: un distrito inexistente es 400, no una lista vacía")
    void unknownDistrict() {
        when(repository.districtExists("09")).thenReturn(false);

        assertThatThrownBy(() -> service.search(criteria("09", null, null)))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getInvalidParams()).extracting(InvalidParam::name).containsExactly("district_id"));
        verify(repository, never()).search(any());
    }

    @Test
    @DisplayName("KAN-9 CA4: un barrio inexistente es 400")
    void unknownNeighbourhood() {
        when(repository.neighbourhoodExists("99")).thenReturn(false);

        assertThatThrownBy(() -> service.search(criteria(null, "99", null)))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getInvalidParams()).extracting(InvalidParam::name).containsExactly("neighbourhood_id"));
    }

    @Test
    @DisplayName("KAN-9 CA3: barrio de otro distrito es 400 con Problem que lo indica")
    void neighbourhoodOutsideDistrict() {
        when(repository.districtExists("01")).thenReturn(true);
        when(repository.neighbourhoodExists("05")).thenReturn(true);
        when(repository.neighbourhoodInDistrict("05", "01")).thenReturn(false);

        assertThatThrownBy(() -> service.search(criteria("01", "05", null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Neighbourhood 05 does not belong to district 01.");
    }

    @Test
    @DisplayName("KAN-9 CA1/CA2: con filtros válidos se consulta el repositorio")
    void validFilters() {
        when(repository.districtExists("01")).thenReturn(true);
        when(repository.neighbourhoodExists("03")).thenReturn(true);
        when(repository.neighbourhoodInDistrict("03", "01")).thenReturn(true);
        when(repository.search(any())).thenReturn(new FountainRepository.Slice(List.of(fountain("01-004")), false));

        assertThat(service.search(criteria("01", "03", null)).items()).hasSize(1);
    }

    @Test
    @DisplayName("Paginación: primera página sin anterior; con cursor hacia delante hay anterior; hacia atrás siempre hay siguiente")
    void pageFlags() {
        when(repository.search(any())).thenReturn(new FountainRepository.Slice(List.of(fountain("01-004")), true));

        FountainPage first = service.search(criteria(null, null, null));
        FountainPage forward = service.search(criteria(null, null, new Cursor(null, "01-003", false)));
        FountainPage backward = service.search(criteria(null, null, new Cursor(null, "01-009", true)));

        assertThat(first).extracting(FountainPage::hasPrevious, FountainPage::hasNext).containsExactly(false, true);
        assertThat(forward).extracting(FountainPage::hasPrevious, FountainPage::hasNext).containsExactly(true, true);
        assertThat(backward).extracting(FountainPage::hasPrevious, FountainPage::hasNext).containsExactly(true, true);
    }

    @Test
    @DisplayName("KAN-7 CA2: id inexistente lanza FountainNotFoundException (404)")
    void notFound() {
        when(repository.findById("99-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("99-999"))
                .isInstanceOf(FountainNotFoundException.class)
                .hasMessage("Drinking water fountain 99-999 does not exist.");
    }
}
