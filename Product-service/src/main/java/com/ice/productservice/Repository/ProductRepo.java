package com.ice.productservice.Repository;

import com.ice.productservice.Entity.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepo extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    @EntityGraph(attributePaths = {"productImages", "productVariants", "category"})
    @NonNull
    List<Product> findAll();
    @EntityGraph(attributePaths = {"productImages", "category"})
    @NonNull
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    boolean existsByCategory_Id(UUID id);

    @EntityGraph(attributePaths = {"productAttributes", "productImages", "productVariants", "category"})
    Optional<Product> findBySlugAndIsDeleteFalse(String slug);

    @EntityGraph(attributePaths = {"category"})
    @NonNull
    Optional<Product> findById(UUID id);

    boolean existsBySlug(String slug);
}
