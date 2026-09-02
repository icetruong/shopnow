package com.ice.reviewservice.Repository;

import com.ice.reviewservice.Entity.Review;
import com.ice.reviewservice.Enum.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepo extends JpaRepository<Review, UUID>, JpaSpecificationExecutor<Review> {
    boolean existsByUserIdAndOrderIdAndVariantId(UUID userId, UUID orderId, UUID variantId);

    @Query("""
           SELECT count(distinct ri.review.id)
           FROM ReviewImage ri
           WHERE ri.review.productId = :productId
           AND ri.review.status = :status
""")
    long countReviewsWithImage(@Param("productId") UUID productId,
                               @Param("status") ReviewStatus status);

    List<Review> findAllByUserId(UUID userId);

    Optional<Review> findByIdAndUserId(UUID id, UUID userId);
}
