package com.ice.shippingservice.Entity;

import com.ice.shippingservice.Enum.ShipmentTrackingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "shipment_tracking",
        indexes = {
                @Index(name = "idx_shipment_tracking_shipment_id", columnList = "shipment_id"),
                @Index(name = "idx_shipment_tracking_happened_at", columnList = "happened_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentTracking {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_shipment_tracking_shipment"))
    @OnDelete(action = OnDeleteAction.CASCADE) // khớp với "ON DELETE CASCADE" ở DB
    private Shipment shipment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShipmentTrackingStatus status;

    /** Mô tả (VD: "Đang giao hàng"). */
    @Column(name = "description", length = 255)
    private String description;

    /** Vị trí (VD: "Kho GHN Q3"). */
    @Column(name = "location", length = 255)
    private String location;

    /** Status gốc từ nhà vận chuyển (chưa map). */
    @Column(name = "carrier_status", length = 50)
    private String carrierStatus;

    /** Thời điểm sự kiện xảy ra (từ nhà vận chuyển). */
    @Column(name = "happened_at", nullable = false)
    private LocalDateTime happenedAt;

    /** Thời điểm nhận webhook. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();;
    }
}
