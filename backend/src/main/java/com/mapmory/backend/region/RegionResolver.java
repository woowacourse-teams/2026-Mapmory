package com.mapmory.backend.region;

import com.mapmory.backend.common.exception.BusinessException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RegionResolver {

    private final RegionRepository regionRepository;

    public RegionResolver(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    public Region resolve(String countryCode, String provinceCode, String districtCode) {
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
