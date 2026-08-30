package com.ice.reviewservice.Repository;

import com.ice.reviewservice.Entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewRepo extends JpaRepository<Review, UUID> {
}
