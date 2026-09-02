package com.ice.reviewservice.Repository;

import com.ice.reviewservice.Entity.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReviewImageRepo extends JpaRepository<ReviewImage, UUID> {
    List<ReviewImage> findByReviewId(UUID reviewId);

    List<ReviewImage> findByReviewIdIn(Collection<UUID> reviewIds);
}
