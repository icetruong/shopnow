package com.ice.shippingservice.Service;

import com.ice.shippingservice.Entity.LocationMapping;
import com.ice.shippingservice.Repository.LocationMappingRepo;
import com.ice.shippingservice.Util.AddressNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LocationResolver {
    private final LocationMappingRepo locationMappingRepo;

    public Optional<LocationMapping> resolve(String province, String district, String ward)
    {
        Optional<LocationMapping> exact = locationMappingRepo.findByProvinceNameAndDistrictNameAndWardName(province, district, ward);

        if(exact.isPresent())
            return exact;

        return locationMappingRepo.findByProvinceNameNormalizedAndDistrictNameNormalizedAndWardNameNormalized(
                AddressNormalizer.normalize(province),
                AddressNormalizer.normalize(district),
                AddressNormalizer.normalize(ward)
        );
    }

}
