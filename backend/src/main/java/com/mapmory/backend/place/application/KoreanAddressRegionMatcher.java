package com.mapmory.backend.place.application;

import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionResolver;
import com.mapmory.backend.region.RegionType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 주소의 시·도와 시·군·구 이름을 DB 지역과 맞춘다.
 *
 * <p>제공자마다 시·도 이름이 다를 수 있어("강원도"/"강원특별자치도") 줄임 이름으로 비교한다.
 * 일반구가 있는 시는 시 단위로 저장하므로(V18) "성남시 분당구"는 "성남시"로 맞춘다.
 */
@Component
public class KoreanAddressRegionMatcher {

    private static final String KOREA = "KR";
    private static final Map<String, String> PROVINCE_SHORT_NAMES = Map.of(
            "충청북", "충북", "충청남", "충남", "전라북", "전북",
            "전라남", "전남", "경상북", "경북", "경상남", "경남");

    private final RegionResolver regionResolver;

    public KoreanAddressRegionMatcher(RegionResolver regionResolver) {
        this.regionResolver = regionResolver;
    }

    /**
     * areaNames의 첫 값은 시·도, 나머지는 큰 단위부터 작은 단위 순서다. 시·군·구까지 찾지 못하면 빈 값이다.
     * 시·도는 첫 값으로만 비교한다. "광주시"(경기도)처럼 시·군·구 이름이 다른 시·도와 겹칠 수 있기 때문이다.
     */
    public Optional<Region> match(List<String> areaNames) {
        List<String> names = areaNames.stream().filter(Objects::nonNull).map(String::strip).toList();
        if (names.isEmpty()) {
            return Optional.empty();
        }
        String provinceName = names.getFirst();
        Region country = regionResolver.resolve(KOREA, null, null);
        return regionResolver.children(country, RegionType.PROVINCE).stream()
                .filter(province -> sameProvince(province.getName(), provinceName))
                .findFirst()
                .flatMap(province -> district(province, names.subList(1, names.size())));
    }

    private Optional<Region> district(Region province, List<String> names) {
        List<Region> districts = regionResolver.children(province, RegionType.DISTRICT);
        if (districts.size() == 1) {
            // 세종특별자치시는 시·군·구가 하나뿐이다.
            return Optional.of(districts.getFirst());
        }
        for (int i = names.size() - 1; i >= 0; i--) {
            String name = names.get(i);
            Optional<Region> match = districts.stream()
                    .filter(district -> district.getName().equals(name))
                    .findFirst();
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private static boolean sameProvince(String regionName, String addressName) {
        return shortName(regionName).equals(shortName(addressName));
    }

    private static String shortName(String provinceName) {
        String name = provinceName.replaceAll("(특별자치도|특별자치시|특별시|광역시|도|시)$", "");
        return PROVINCE_SHORT_NAMES.getOrDefault(name, name);
    }
}
