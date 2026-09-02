package com.ice.reviewservice.Repository;

import com.ice.reviewservice.Entity.ReviewReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewReplyRepo extends JpaRepository<ReviewReply, UUID> {
    Optional<ReviewReply> findByReviewId(UUID reviewId);

    List<ReviewReply> findByReviewIdIn(Collection<UUID> reviewIds);
}
