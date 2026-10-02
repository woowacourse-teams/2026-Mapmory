package com.mapmory.backend.place.infrastructure.region;

import com.mapmory.backend.place.application.port.DistrictLocator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Client map bundle을 서버에서도 읽어 장소 좌표가 속한 시군구를 추천한다. */
@Component
public class KoreanDistrictLocator implements DistrictLocator {

    private static final Logger log = LoggerFactory.getLogger(KoreanDistrictLocator.class);

    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final List<DistrictPolygon> polygons;

    @Autowired
    public KoreanDistrictLocator(JsonMapper jsonMapper) throws IOException {
        this(jsonMapper, new PathMatchingResourcePatternResolver()
                .getResources("classpath:region/korea-districts-*.json"));
    }

    KoreanDistrictLocator(JsonMapper jsonMapper, Resource[] resources) throws IOException {
        if (resources.length != 17) {
            log.warn("국내 시군구 경계 파일이 일부 없거나 추가되었습니다: {}개. 없는 지역은 수동 선택합니다.", resources.length);
        }
        List<DistrictPolygon> loaded = new ArrayList<>();
        for (Resource resource : resources) {
            try (var input = resource.getInputStream()) {
                JsonNode root = jsonMapper.readTree(input);
                String provinceCode = root.path("provinceCode").asText().replace("KR-", "");
                for (JsonNode district : root.path("districts")) {
                    String districtCode = district.path("code").asText();
                    for (JsonNode ring : district.path("rings")) {
                        Coordinate[] coordinates = coordinates(ring);
                        if (coordinates.length < 4) {
                            continue;
                        }
                        LinearRing shell = geometryFactory.createLinearRing(coordinates);
                        Polygon polygon = geometryFactory.createPolygon(shell);
                        loaded.add(new DistrictPolygon(provinceCode, districtCode,
                                PreparedGeometryFactory.prepare(polygon)));
                    }
                }
            }
        }
        polygons = List.copyOf(loaded);
    }

    @Override
    public Optional<DistrictMatch> find(double longitude, double latitude) {
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        DistrictMatch found = null;
        for (DistrictPolygon candidate : polygons) {
            if (!candidate.geometry().getGeometry().getEnvelopeInternal().covers(point.getCoordinate())
                    || !candidate.geometry().covers(point)) {
                continue;
            }
            DistrictMatch match = new DistrictMatch(candidate.provinceCode(), candidate.districtCode());
            if (found != null && !found.equals(match)) {
                return Optional.empty();
            }
            found = match;
        }
        return Optional.ofNullable(found);
    }

    private static Coordinate[] coordinates(JsonNode ring) {
        List<Coordinate> points = new ArrayList<>();
        for (JsonNode coordinate : ring) {
            if (coordinate.size() == 2 && coordinate.get(0).isNumber() && coordinate.get(1).isNumber()) {
                points.add(new Coordinate(coordinate.get(0).asDouble(), coordinate.get(1).asDouble()));
            }
        }
        if (points.size() >= 3 && !points.getFirst().equals2D(points.getLast())) {
            points.add(new Coordinate(points.getFirst()));
        }
        return points.toArray(Coordinate[]::new);
    }

    private record DistrictPolygon(String provinceCode, String districtCode, PreparedGeometry geometry) {
    }
}
