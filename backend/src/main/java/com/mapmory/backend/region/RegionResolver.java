package com.mapmory.backend.region;

import com.mapmory.backend.common.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RegionResolver {

    private final RegionRepository regionRepository;

    public RegionResolver(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    /**
     * 국가 > 시·도 > 시·군·구 코드 중 가장 작은 단위의 지역을 찾는다. 하위 코드는 상위 코드와 함께 와야 한다.
     */
    public Region resolve(String countryCode, String provinceCode, String districtCode) {
        if ((countryCode == null && provinceCode != null) || (provinceCode == null && districtCode != null)) {
            throw new BusinessException(RegionErrorCode.INVALID_REGION_HIERARCHY);
        }
        Region country = findCountry(countryCode);

        if (provinceCode == null) {
            return country;
        }

        Region province = findChild(country, RegionType.PROVINCE, provinceCode);

        if (districtCode == null) {
            return province;
        }

        return findChild(province, RegionType.DISTRICT, districtCode);
    }

    /**
     * 코드가 하나도 없으면 빈 값이다. 목록 필터처럼 지역을 고르지 않아도 되는 곳에서 쓴다.
     */
    public Optional<Region> resolveIfPresent(String countryCode, String provinceCode, String districtCode) {
        if (countryCode == null && provinceCode == null && districtCode == null) {
            return Optional.empty();
        }
        return Optional.of(resolve(countryCode, provinceCode, districtCode));
    }

    public List<Region> children(Region parent, RegionType regionType) {
        return regionRepository.findByParentIdAndRegionType(parent.getId(), regionType);
    }

    private Region findCountry(String countryCode) {
        return regionRepository.findByParentIsNullAndRegionTypeAndRegionCode(
                RegionType.COUNTRY,
                countryCode
        ).orElseThrow(() -> new BusinessException(RegionErrorCode.REGION_NOT_FOUND));
    }

    private Region findChild(Region parent, RegionType regionType, String regionCode) {
        return regionRepository.findByParentIdAndRegionTypeAndRegionCode(
                parent.getId(),
                regionType,
                regionCode
        ).orElseGet(() -> {
            if (regionRepository.existsByRegionTypeAndRegionCode(regionType, regionCode)) {
                throw new BusinessException(RegionErrorCode.INVALID_REGION_HIERARCHY);
            }
            throw new BusinessException(RegionErrorCode.REGION_NOT_FOUND);
        });
    }
}
