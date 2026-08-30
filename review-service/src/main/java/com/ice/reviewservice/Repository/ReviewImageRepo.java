package com.ice.reviewservice.Repository;

import com.ice.reviewservice.Entity.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewImageRepo extends JpaRepository<ReviewImage, UUID> {
}
