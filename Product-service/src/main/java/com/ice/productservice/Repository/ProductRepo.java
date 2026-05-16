package com.ice.productservice.Repository;

import com.ice.productservice.Entity.Product;
import lombok.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepo extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    @EntityGraph(attributePaths = {"productImages", "category"})
    @NonNull
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    boolean existsByCategory_Id(UUID id);

    @EntityGraph(attributePaths = {"productAttributes", "productImages", "productVariants", "category"})
    Optional<Product> findBySlugAndIsDeleteFalse(String slug);

}
