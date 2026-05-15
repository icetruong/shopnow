package com.ice.productservice.Repository;

import com.ice.productservice.Entity.ProductAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductAttributeRepo extends JpaRepository<ProductAttribute, UUID> {
}
