package com.mapmory.backend.region.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.region.domain.RegionBoundary;
import com.mapmory.backend.region.domain.RegionRepository;
import com.mapmory.backend.region.domain.RegionType;
import com.mapmory.backend.region.exception.RegionErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegionBoundaryService {

    private final RegionRepository regionRepository;
    private final RegionBoundaryReader boundaryReader;

    public RegionBoundaryService(RegionRepository regionRepository, RegionBoundaryReader boundaryReader) {
        this.regionRepository = regionRepository;
        this.boundaryReader = boundaryReader;
    }

    // 지역 목록의 원천은 region 테이블이다. 테이블에 없는 나라는 경계 파일이 있어도 주지 않는다.
    @Transactional(readOnly = true)
    public RegionBoundary countryBoundary(String countryCode) {
        if (!regionRepository.existsByRegionTypeAndRegionCode(RegionType.COUNTRY, countryCode)) {
            throw new BusinessException(RegionErrorCode.REGION_NOT_FOUND);
        }
        return boundaryReader.read(countryCode)
                .orElseThrow(() -> new BusinessException(RegionErrorCode.REGION_BOUNDARY_NOT_FOUND));
    }
}
