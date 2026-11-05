package com.mapmory.backend.region.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.region.domain.RegionBoundary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

class RegionBoundaryReaderTest {

    private final RegionBoundaryReader reader = new RegionBoundaryReader(JsonMapper.builder().build());

    @Test
    void 서버_나라_목록의_모든_코드에_경계가_있다() throws IOException {
        // UM(미국령 해외 제도)은 Natural Earth 원본에 경계가 없다.
        Set<String> missing = countryCodes().stream()
                .filter(code -> !code.equals("UM"))
                .filter(code -> reader.read(code).isEmpty())
                .collect(Collectors.toSet());

        assertThat(missing).isEmpty();
    }

    @Test
    void 홍콩_경계는_홍콩_안만_포함한다() {
        RegionBoundary hongKong = reader.read("HK").orElseThrow();

        assertThat(contains(hongKong, 114.1582, 22.2819)).isTrue();
        assertThat(contains(hongKong, 114.0579, 22.5431)).as("선전").isFalse();
        assertThat(hongKong.version()).startsWith("natural-earth-vector v5.1.2");
    }

    @Test
    void 아주_작은_나라도_실제_위치를_포함한다() {
        assertThat(contains(reader.read("VA").orElseThrow(), 12.4534, 41.9029)).as("바티칸").isTrue();
        assertThat(contains(reader.read("MO").orElseThrow(), 113.5439, 22.1987)).as("마카오").isTrue();
        assertThat(contains(reader.read("MC").orElseThrow(), 7.4246, 43.7384)).as("모나코").isTrue();
    }

    @Test
    void 경계가_없는_코드는_비어_있다() {
        assertThat(reader.read("UM")).isEmpty();
        assertThat(reader.read("ZZ")).isEmpty();
    }

    private static Set<String> countryCodes() throws IOException {
        String sql = new ClassPathResource("db/migration/V6__insert_country.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\\('([A-Z]{2})',").matcher(sql);
        return matcher.results().map(result -> result.group(1)).collect(Collectors.toSet());
    }

    private static boolean contains(RegionBoundary boundary, double longitude, double latitude) {
        return boundary.rings().stream().anyMatch(ring -> contains(ring, longitude, latitude));
    }

    private static boolean contains(List<List<Double>> ring, double longitude, double latitude) {
        boolean inside = false;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            double xi = ring.get(i).get(0);
            double yi = ring.get(i).get(1);
            double xj = ring.get(j).get(0);
            double yj = ring.get(j).get(1);
            if ((yi > latitude) != (yj > latitude)
                    && longitude < (xj - xi) * (latitude - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }
}
