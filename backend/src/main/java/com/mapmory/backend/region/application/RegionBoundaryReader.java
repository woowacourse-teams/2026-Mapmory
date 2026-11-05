package com.mapmory.backend.region.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mapmory.backend.region.domain.RegionBoundary;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 빌드에 함께 들어가는 나라별 경계 파일을 읽는다.
 *
 * 파일은 tools/region-boundary/generate_region_boundaries.py가 Natural Earth에서 만든다.
 * 경계는 배포 사이에 바뀌지 않으므로, 자주 요청되는 나라만 메모리에 잠시 둔다.
 */
@Component
public class RegionBoundaryReader {

    private static final String DIRECTORY = "region-boundaries/";
    // 캐나다처럼 큰 파일은 1MB를 넘는다. 여행지 상위 몇십 곳만 들고 있으면 충분하다.
    private static final int CACHED_COUNTRIES = 64;

    private final JsonMapper jsonMapper;
    private final String version;
    private final Cache<String, Optional<RegionBoundary>> boundaries = Caffeine.newBuilder()
            .maximumSize(CACHED_COUNTRIES)
            .build();

    public RegionBoundaryReader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.version = readVersion();
    }

    public Optional<RegionBoundary> read(String regionCode) {
        return boundaries.get(regionCode, this::load);
    }

    private Optional<RegionBoundary> load(String regionCode) {
        ClassPathResource resource = new ClassPathResource(DIRECTORY + regionCode + ".json");
        if (!resource.exists()) {
            return Optional.empty();
        }
        try (InputStream input = resource.getInputStream()) {
            BoundaryFile file = jsonMapper.readValue(input, BoundaryFile.class);
            return Optional.of(new RegionBoundary(regionCode, version, file.rings()));
        } catch (IOException e) {
            throw new UncheckedIOException("경계 파일을 읽지 못했습니다: " + regionCode, e);
        }
    }

    private static String readVersion() {
        try (InputStream input = new ClassPathResource(DIRECTORY + "VERSION").getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("경계 데이터 버전 파일이 없습니다", e);
        }
    }

    private record BoundaryFile(List<List<List<Double>>> rings) {
    }
}
