package com.ice.productservice.Repository;

import com.ice.productservice.Entity.ProductVariant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepo extends JpaRepository<ProductVariant, UUID> {
    @EntityGraph(attributePaths = {"product"})
    Optional<ProductVariant> findByIdAndProduct_Id(UUID id, UUID productId);
    boolean existsBySku(String sku);
}
