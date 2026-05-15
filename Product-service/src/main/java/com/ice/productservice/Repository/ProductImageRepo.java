package com.ice.productservice.Repository;

import com.ice.productservice.Entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductImageRepo extends JpaRepository<ProductImage, UUID> {
}
