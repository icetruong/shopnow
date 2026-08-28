package com.ice.shippingservice.Repository;

import com.ice.shippingservice.Entity.LocationMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LocationMappingRepo extends JpaRepository<LocationMapping, UUID> {
    Optional<LocationMapping> findByProvinceNameAndDistrictNameAndWardName(String provinceName, String districtName, String wardName);

    Optional<LocationMapping> findByProvinceNameNormalizedAndDistrictNameNormalizedAndWardNameNormalized(String provinceNameNormalized, String districtNameNormalized, String wardNameNormalized);
}
