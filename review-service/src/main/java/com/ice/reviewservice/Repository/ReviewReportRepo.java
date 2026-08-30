package com.ice.reviewservice.Repository;

import com.ice.reviewservice.Entity.ReviewReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewReportRepo extends JpaRepository<ReviewReport, UUID> {
}
