package com.mapmory.backend.region.application;

import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionAddress;
import com.mapmory.backend.region.domain.RegionType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 한국 주소의 시·도와 시·군·구 이름을 DB 지역과 맞춘다.
 *
 * <p>제공자마다 시·도 이름이 다를 수 있어("강원도"/"강원특별자치도") 줄임 이름으로 비교한다.
 * 일반구가 있는 시는 시 단위로 저장하므로(V18) "성남시 분당구"는 "성남시"로 맞춘다.
 *
 * <p>Google은 일부 장소에 시·도를 주지 않는다(대천해수욕장: 보령시, 대한민국만 온다).
 * 그때는 시·군·구 이름을 전국에서 찾고, 한 곳만 맞을 때만 쓴다. "중구"처럼 여러 시·도에 있는 이름은 고르지 않는다.
 */
@Component
class KoreanAddressRegionMatcher {

    private static final Map<String, String> PROVINCE_SHORT_NAMES = Map.of(
            "충청북", "충북",
            "충청남", "충남",
            "전라북", "전북",
            "전라남", "전남",
            "경상북", "경북",
            "경상남", "경남"
    );

    private final RegionResolver regionResolver;

    public KoreanAddressRegionMatcher(RegionResolver regionResolver) {
        this.regionResolver = regionResolver;
    }

    /**
     * 시·도를 찾지 못하면 빈 값이다. DB에 대한민국이 없어도 빈 값이다. 시·군·구를 찾지 못하면("부산광역시"만 있는 주소) 시·도를 돌려준다.
     * 시·군·구는 시·도 안에서만 찾는다. "광주시"(경기도)처럼 시·군·구 이름이 다른 시·도와 겹칠 수 있기 때문이다.
     * 주소에 시·도가 없을 때만 시·군·구를 전국에서 찾는다.
     */
    public Optional<Region> match(RegionAddress address) {
        Optional<Region> country = regionResolver.findCountryIfPresent(Region.KOREA);
        if (address.province() == null) {
            return country.flatMap(korea -> uniqueDistrictInKorea(korea, address.subAreas()));
        }
        return country
                .flatMap(korea -> regionResolver.children(korea, RegionType.PROVINCE).stream()
                        .filter(province -> sameProvince(province.getName(), address.province()))
                        .findFirst())
                .flatMap(province -> district(province, address.subAreas()));
    }

    // 작은 단위 이름부터 본다. 이름이 여러 시·도에 있으면 다음 이름으로 넘어가고, 끝까지 하나로 정해지지 않으면 빈 값이다.
    private Optional<Region> uniqueDistrictInKorea(Region korea, List<String> names) {
        if (names.isEmpty()) {
            return Optional.empty();
        }
        List<Region> districts = regionResolver.children(korea, RegionType.PROVINCE).stream()
                .flatMap(province -> regionResolver.children(province, RegionType.DISTRICT).stream())
                .toList();
        for (int i = names.size() - 1; i >= 0; i--) {
            String name = names.get(i);
            List<Region> matches = districts.stream()
                    .filter(district -> district.getName().equals(name))
                    .toList();
            if (matches.size() == 1) {
                return Optional.of(matches.getFirst());
            }
        }
        return Optional.empty();
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
        return Optional.of(province);
    }

    private static boolean sameProvince(String regionName, String addressName) {
        return shortName(regionName).equals(shortName(addressName));
    }

    private static String shortName(String provinceName) {
        String name = provinceName.replaceAll("(특별자치도|특별자치시|특별시|광역시|도|시)$", "");
        return PROVINCE_SHORT_NAMES.getOrDefault(name, name);
    }
}
