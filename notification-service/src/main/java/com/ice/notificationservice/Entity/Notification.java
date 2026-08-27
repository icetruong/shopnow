package com.ice.notificationservice.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(
        name = "shipments",
        indexes = {
                @Index(name = "idx_shipments_order_id", columnList = "order_id", unique = true),
                @Index(name = "idx_shipments_tracking_code", columnList = "tracking_code", unique = true),
                @Index(name = "idx_shipments_status", columnList = "status"),
                @Index(name = "idx_shipments_carrier", columnList = "carrier"),
                @Index(name = "idx_shipments_user_id", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
}
