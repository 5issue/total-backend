package com.kurly.oms.domain.fulfillment;

import java.util.Optional;

public interface TamRegionRepository {

    Optional<TamRegion> findById(Long id);

    Optional<TamRegion> findByRegionCodeAndStatus(String regionCode, RegionStatus status);
}