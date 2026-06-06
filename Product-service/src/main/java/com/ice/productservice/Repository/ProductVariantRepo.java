package com.ice.productservice.Repository;

import com.ice.productservice.Entity.ProductVariant;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepo extends JpaRepository<ProductVariant, UUID> {
    @EntityGraph(attributePaths = {"product"})
    Optional<ProductVariant> findByIdAndProduct_Id(UUID id, UUID productId);
    boolean existsBySku(String sku);

    @EntityGraph(attributePaths = {"product"})
    @NonNull
    Optional<ProductVariant> findById(UUID id);

    @EntityGraph(attributePaths = {"product"})
    List<ProductVariant> findAllByIdIn(List<UUID> variantIds);
}
