package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.out.persistence;

import com.geobcn.service.fountains.drinkingwater.domain.*;
import com.geobcn.service.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Consultas PostGIS sobre un conjunto sintético y controlado (distancias exactas en metros). */
class JdbcFountainRepositoryIT {

    // Punto de búsqueda de KAN-8: (41.3851, 2.1734)
    private static final double LAT = 41.3851;
    private static final double LON = 2.1734;
    private static JdbcFountainRepository repository;

    @BeforeAll
    static void setUp() throws Exception {
        TestDatabase db = TestDatabase.createWithSchema();
        try (Connection c = db.connect(); Statement s = c.createStatement()) {
            s.execute("""
                    INSERT INTO district VALUES ('01', 'Ciutat Vella'), ('02', 'Eixample');
                    INSERT INTO neighbourhood VALUES ('01', 'el Raval'), ('05', 'el Fort Pienc');
                    CREATE TEMP TABLE p AS SELECT ST_Transform(ST_SetSRID(ST_MakePoint(%s, %s), 4326), 25831) AS g;
                    -- KAN-8 CA1: tres fuentes a 120, 45 y 300 m al norte del punto
                    INSERT INTO drinking_water_fountain (id, name, street, district_id, neighbourhood_id, geom, latitude, longitude)
                    SELECT v.id, v.name, 'C PROVA', '01', '01', ST_Translate(p.g, 0, v.d),
                           ST_Y(ST_Transform(ST_Translate(p.g, 0, v.d), 4326)),
                           ST_X(ST_Transform(ST_Translate(p.g, 0, v.d), 4326))
                    FROM p, (VALUES ('01-001', 'A 120m', 120), ('01-002', 'B 45m', 45), ('01-003', 'C 300m', 300)) AS v(id, name, d);
                    -- KAN-8 CA2: 25 fuentes entre 10 y 490 m (distrito 02) y 5 fuera del radio (a 600+ m)
                    INSERT INTO drinking_water_fountain (id, name, street, district_id, neighbourhood_id, geom, latitude, longitude)
                    SELECT '02-' || lpad(i::text, 3, '0'), NULL, 'C PROVA', '02', '05', ST_Translate(p.g, -d, 0),
                           ST_Y(ST_Transform(ST_Translate(p.g, -d, 0), 4326)),
                           ST_X(ST_Transform(ST_Translate(p.g, -d, 0), 4326))
                    FROM p, (SELECT i, CASE WHEN i <= 25 THEN 10 + (i - 1) * 20 ELSE 600 + i END AS d
                             FROM generate_series(1, 30) i) q(i, d);
                    """.formatted(LON, LAT));
        }
        DriverManagerDataSource ds = new DriverManagerDataSource(db.url(), db.user(), db.password());
        repository = new JdbcFountainRepository(JdbcClient.create(ds));
    }

    private static SearchCriteria proximity(int radius, int limit, Cursor cursor, String district) {
        return new SearchCriteria(LAT, LON, radius, null, district, null, limit, cursor);
    }

    @Test
    @DisplayName("KAN-8 CA1: dadas fuentes a 120, 45 y 300 m, se devuelven en orden 45, 120, 300 con metros enteros")
    void ordersByDistance() {
        var slice = repository.search(proximity(500, 50, null, "01"));

        assertThat(slice.items()).extracting(Fountain::name).containsExactly("B 45m", "A 120m", "C 300m");
        assertThat(slice.items()).extracting(Fountain::distance).containsExactly(45, 120, 300);
    }

    @Test
    @DisplayName("KAN-8 CA2: radio 500 y límite 10 con 25 dentro → 10 más cercanas, hay más páginas y ninguna supera 500 m")
    void radiusAndLimit() {
        var slice = repository.search(proximity(500, 10, null, "02"));

        assertThat(slice.items()).hasSize(10);
        assertThat(slice.more()).isTrue();
        assertThat(slice.items()).allSatisfy(f -> assertThat(f.distance()).isLessThanOrEqualTo(500));
        assertThat(slice.items()).extracting(Fountain::distance).isSorted();
    }

    @Test
    @DisplayName("KAN-8 CA2: el cursor entrega la página siguiente sin repetir ni saltar fuentes, y la anterior vuelve atrás")
    void keysetPagination() {
        var first = repository.search(proximity(500, 10, null, "02"));
        Fountain last = first.items().get(9);
        var second = repository.search(proximity(500, 10, new Cursor(last.distance(), last.id(), false), "02"));
        var third = repository.search(proximity(500, 10,
                new Cursor(second.items().get(9).distance(), second.items().get(9).id(), false), "02"));

        assertThat(second.items()).hasSize(10);
        assertThat(third.items()).hasSize(5);
        assertThat(third.more()).isFalse();
        assertThat(second.items().get(0).distance()).isGreaterThanOrEqualTo(last.distance());

        Fountain firstOfSecond = second.items().get(0);
        var back = repository.search(proximity(500, 10, new Cursor(firstOfSecond.distance(), firstOfSecond.id(), true), "02"));
        assertThat(back.items()).extracting(Fountain::id).containsExactlyElementsOf(first.items().stream().map(Fountain::id).toList());
        assertThat(back.more()).isFalse();
    }

    @Test
    @DisplayName("KAN-8 CA4: un punto sin fuentes en el radio devuelve lista vacía")
    void noResults() {
        var slice = repository.search(new SearchCriteria(48.8566, 2.3522, 500, null, null, null, 50, null));

        assertThat(slice.items()).isEmpty();
        assertThat(slice.more()).isFalse();
    }

    @Test
    @DisplayName("KAN-6: sin filtros, ordena por id y no informa distancia; la caja bbox filtra en WGS84")
    void listAndBbox() {
        var all = repository.search(new SearchCriteria(null, null, 500, null, null, null, 100, null));
        assertThat(all.items()).hasSize(33);
        assertThat(all.items()).extracting(Fountain::id).isSorted();
        assertThat(all.items()).allSatisfy(f -> assertThat(f.distance()).isNull());

        var tiny = new BoundingBox(LON - 0.0003, LAT - 0.0003, LON + 0.0003, LAT + 0.0003);
        var inside = repository.search(new SearchCriteria(null, null, 500, tiny, null, null, 100, null));
        assertThat(inside.items()).extracting(Fountain::name).containsExactly((String) null); // solo la de 10 m al oeste
    }

    @Test
    @DisplayName("KAN-9: catálogo con barrios por distrito y pertenencia derivada de las fuentes")
    void catalog() {
        List<DistrictWithNeighbourhoods> districts = repository.listDistricts();

        assertThat(districts).extracting(DistrictWithNeighbourhoods::id).containsExactly("01", "02");
        assertThat(districts.get(1).neighbourhoods()).extracting(n -> n.id()).containsExactly("05");
        assertThat(repository.neighbourhoodInDistrict("05", "02")).isTrue();
        assertThat(repository.neighbourhoodInDistrict("05", "01")).isFalse();
        assertThat(repository.districtExists("03")).isFalse();
    }
}
