package com.ice.shippingservice.Repository;

import com.ice.shippingservice.Entity.LocationMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LocationMappingRepo extends JpaRepository<LocationMapping, UUID> {
}
