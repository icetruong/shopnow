package com.ice.productservice.Repository;

import com.ice.productservice.Entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CategoryRepo extends JpaRepository<Category, UUID> {

    @Query(
            """
        SELECT DISTINCT c 
        FROM Category c
        LEFT JOIN FETCH c.parent
        WHERE c.isActive = true
        ORDER BY c.sortOrder
"""
    )
    List<Category> findAllActiveForTree();

    boolean existsByParent_Id(UUID id);
}
