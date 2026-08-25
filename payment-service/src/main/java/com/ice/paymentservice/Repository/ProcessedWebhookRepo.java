package com.ice.paymentservice.Repository;

import com.ice.paymentservice.Entity.ProcessedWebhook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedWebhookRepo extends JpaRepository<ProcessedWebhook, UUID> {
    boolean existsByIdempotencyKey(String idempotencyKey);
}
