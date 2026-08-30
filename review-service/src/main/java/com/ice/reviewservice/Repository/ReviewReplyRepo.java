package com.ice.reviewservice.Repository;

import com.ice.reviewservice.Entity.ReviewReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewReplyRepo extends JpaRepository<ReviewReply, UUID> {
}
