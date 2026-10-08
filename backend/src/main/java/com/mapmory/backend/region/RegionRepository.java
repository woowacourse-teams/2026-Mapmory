package com.mapmory.backend.region;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByParentIsNullAndRegionTypeAndRegionCode(
            RegionType regionType,
            String regionCode
    );

    @EntityGraph(attributePaths = {"parent", "root"})
    Optional<Region> findByParentIdAndRegionTypeAndRegionCode(
            Long parentId,
            RegionType regionType,
            String regionCode
    );

    boolean existsByRegionTypeAndRegionCode(RegionType regionType, String regionCode);

    // 주소로 찾은 지역을 트랜잭션 밖에서 응답으로 만들 때 국가와 시·도까지 읽는다.
    @EntityGraph(attributePaths = {"parent", "root"})
    List<Region> findByParentIdAndRegionType(Long parentId, RegionType regionType);
}
