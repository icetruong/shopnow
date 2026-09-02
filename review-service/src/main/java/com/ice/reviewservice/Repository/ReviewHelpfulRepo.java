package com.ice.reviewservice.Repository;

import com.ice.reviewservice.Entity.ReviewHelpful;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewHelpfulRepo extends JpaRepository<ReviewHelpful, UUID> {
    Optional<ReviewHelpful> findByReviewIdAndUserId(UUID reviewId, UUID userId);
}
