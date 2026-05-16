package com.ice.productservice.Repository;

import com.ice.productservice.Entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductImageRepo extends JpaRepository<ProductImage, UUID> {
    int countByProduct_Id(UUID productId);
    boolean existsByProduct_IdAndIsPrimaryTrue(UUID productId);

    Optional<ProductImage> findByProduct_IdAndIsPrimaryTrue(UUID productId);
    Optional<ProductImage> findByIdAndProduct_Id(UUID id ,UUID productId);

    List<ProductImage> findAllByProduct_Id(UUID productId);

}
