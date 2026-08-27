package com.ice.shippingservice.Repository;

import com.ice.shippingservice.Entity.ProcessedShippingWebhook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedShippingWebhookRepo extends JpaRepository<ProcessedShippingWebhook, UUID> {
}
