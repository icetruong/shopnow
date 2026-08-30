package com.ice.reviewservice.Repository;

import com.ice.reviewservice.Entity.ProductRatingSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductRatingSummaryRepo extends JpaRepository<ProductRatingSummary, UUID> {
}
