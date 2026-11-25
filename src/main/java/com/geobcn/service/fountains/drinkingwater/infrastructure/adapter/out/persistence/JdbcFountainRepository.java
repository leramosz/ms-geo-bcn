package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.out.persistence;

import com.geobcn.service.fountains.drinkingwater.domain.*;
import com.geobcn.service.fountains.drinkingwater.domain.Fountain.District;
import com.geobcn.service.fountains.drinkingwater.domain.Fountain.Neighbourhood;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/** Adaptador PostGIS. Geometría de trabajo en EPSG:25831 (metros); el punto de búsqueda llega en WGS84. */
@Repository
public class JdbcFountainRepository implements FountainRepository {

    private static final String SELECT = """
            SELECT f.id, f.name, f.street, f.street_number, f.status, f.latitude, f.longitude,
                   f.district_id, d.name AS district_name,
                   f.neighbourhood_id, n.name AS neighbourhood_name,
                   %s AS distance
            FROM drinking_water_fountain f
            JOIN district d ON d.id = f.district_id
            LEFT JOIN neighbourhood n ON n.id = f.neighbourhood_id
            """;
    private static final String POINT_25831 =
            "ST_Transform(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326), 25831)";

    private final JdbcClient jdbc;

    public JdbcFountainRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Slice search(SearchCriteria c) {
        Map<String, Object> params = new HashMap<>();
        List<String> where = new ArrayList<>();
        String distance = "NULL::int";
        if (c.isProximity()) {
            distance = "round(ST_Distance(f.geom, " + POINT_25831 + "))::int";
            where.add("ST_DWithin(f.geom, " + POINT_25831 + ", :radius)");
            params.put("lat", c.lat());
            params.put("lon", c.lon());
            params.put("radius", c.radius());
        }
        if (c.bbox() != null) {
            // && usa el índice GiST; los predicados lat/lon dejan la caja exacta en WGS84.
            where.add("f.geom && ST_Transform(ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326), 25831)");
            where.add("f.longitude BETWEEN :minLon AND :maxLon AND f.latitude BETWEEN :minLat AND :maxLat");
            params.put("minLon", c.bbox().minLon());
            params.put("minLat", c.bbox().minLat());
            params.put("maxLon", c.bbox().maxLon());
            params.put("maxLat", c.bbox().maxLat());
        }
        if (c.districtId() != null) {
            where.add("f.district_id = :districtId");
            params.put("districtId", c.districtId());
        }
        if (c.neighbourhoodId() != null) {
            where.add("f.neighbourhood_id = :neighbourhoodId");
            params.put("neighbourhoodId", c.neighbourhoodId());
        }

        boolean backward = c.cursor() != null && c.cursor().backward();
        String cmp = backward ? "<" : ">";
        String dir = backward ? "DESC" : "ASC";
        String outer = "";
        if (c.cursor() != null) {
            if (c.isProximity()) {
                outer = "WHERE (t.distance, t.id) " + cmp + " (:cursorDistance, :cursorId)";
                params.put("cursorDistance", c.cursor().distance() == null ? 0 : c.cursor().distance());
            } else {
                outer = "WHERE t.id " + cmp + " :cursorId";
            }
            params.put("cursorId", c.cursor().id());
        }
        String order = c.isProximity() ? "t.distance " + dir + ", t.id " + dir : "t.id " + dir;
        String sql = "SELECT t.* FROM (" + SELECT.formatted(distance)
                + (where.isEmpty() ? "" : "WHERE " + String.join(" AND ", where))
                + ") t " + outer + " ORDER BY " + order + " LIMIT :fetch";
        params.put("fetch", c.limit() + 1);

        List<Fountain> rows = new ArrayList<>(jdbc.sql(sql).params(params)
                .query((rs, i) -> map(rs)).list());
        boolean more = rows.size() > c.limit();
        if (more) {
            rows.remove(rows.size() - 1);
        }
        if (backward) {
            Collections.reverse(rows);
        }
        return new Slice(rows, more);
    }

    @Override
    public Optional<Fountain> findById(String id) {
        return jdbc.sql(SELECT.formatted("NULL::int") + "WHERE f.id = :id")
                .param("id", id).query((rs, i) -> map(rs)).optional();
    }

    @Override
    public List<DistrictWithNeighbourhoods> listDistricts() {
        Map<String, String> districts = new LinkedHashMap<>();
        jdbc.sql("SELECT id, name FROM district ORDER BY id")
                .query((rs, i) -> districts.put(rs.getString("id"), rs.getString("name"))).list();
        Map<String, List<Neighbourhood>> byDistrict = new HashMap<>();
        jdbc.sql("""
                SELECT DISTINCT f.district_id, n.id, n.name
                FROM drinking_water_fountain f JOIN neighbourhood n ON n.id = f.neighbourhood_id
                ORDER BY f.district_id, n.id
                """).query((rs, i) -> byDistrict.computeIfAbsent(rs.getString("district_id"), k -> new ArrayList<>())
                .add(new Neighbourhood(rs.getString("id"), rs.getString("name")))).list();
        return districts.entrySet().stream()
                .map(e -> new DistrictWithNeighbourhoods(e.getKey(), e.getValue(),
                        byDistrict.getOrDefault(e.getKey(), List.of())))
                .toList();
    }

    @Override
    public boolean districtExists(String districtId) {
        return exists("SELECT EXISTS (SELECT 1 FROM district WHERE id = :id)", "id", districtId);
    }

    @Override
    public boolean neighbourhoodExists(String neighbourhoodId) {
        return exists("SELECT EXISTS (SELECT 1 FROM neighbourhood WHERE id = :id)", "id", neighbourhoodId);
    }

    @Override
    public boolean neighbourhoodInDistrict(String neighbourhoodId, String districtId) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM drinking_water_fountain
                               WHERE neighbourhood_id = :n AND district_id = :d)
                """).param("n", neighbourhoodId).param("d", districtId).query(Boolean.class).single();
    }

    private boolean exists(String sql, String name, String value) {
        return jdbc.sql(sql).param(name, value).query(Boolean.class).single();
    }

    private static Fountain map(ResultSet rs) throws SQLException {
        String neighbourhoodId = rs.getString("neighbourhood_id");
        int distanceValue = rs.getInt("distance");
        Integer distance = rs.wasNull() ? null : distanceValue;
        return new Fountain(
                rs.getString("id"),
                rs.getString("name"),
                Fountain.Status.valueOf(rs.getString("status")),
                rs.getString("street"),
                rs.getString("street_number"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                new District(rs.getString("district_id"), rs.getString("district_name")),
                neighbourhoodId == null ? null : new Neighbourhood(neighbourhoodId, rs.getString("neighbourhood_name")),
                distance);
    }
}
