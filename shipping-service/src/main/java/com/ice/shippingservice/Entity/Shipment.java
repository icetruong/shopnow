package com.ice.shippingservice.Entity;

import com.ice.shippingservice.Enum.ShipmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

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
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_code", nullable = false, length = 20)
    private String orderCode;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "carrier", nullable = false, length = 20)
    private String carrier;

    @Column(name = "service_id", length = 50)
    private String serviceId;

    @Column(name = "tracking_code", length = 100)
    private String trackingCode;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ShipmentStatus status = ShipmentStatus.PENDING;

    @Column(name = "to_name", length = 100, nullable = false)
    private String toName;

    @Column(name = "to_phone", length = 15, nullable = false)
    private String toPhone;

    @Column(name = "to_address", nullable = false, columnDefinition = "TEXT")
    private String toAddress;

    /** Snapshot tên (từ payload). */
    @Column(name = "to_province", nullable = false, length = 100)
    private String toProvince;

    @Column(name = "to_district", nullable = false, length = 100)
    private String toDistrict;

    @Column(name = "to_ward", nullable = false, length = 100)
    private String toWard;

    /** Mã GHN đã resolve - NULL nếu map fail. */
    @Column(name = "to_province_id")
    private Integer toProvinceId;

    @Column(name = "to_district_id")
    private Integer toDistrictId;

    @Column(name = "to_ward_code", length = 20)
    private String toWardCode;

    /** gram (ước lượng). */
    @Column(name = "weight", nullable = false)
    private Integer weight;

    /** Phí ship - lấy từ pricing.shippingFee của Order Service. */
    @Column(name = "shipping_fee", nullable = false)
    private Long shippingFee;

    /** paymentMethod == COD ? pricing.total : 0. */
    @Builder.Default
    @Column(name = "cod_amount", nullable = false)
    private Long codAmount = 0L;

    /** = pricing.total. */
    @Builder.Default
    @Column(name = "insurance_value", nullable = false)
    private Long insuranceValue = 0L;

    /** Snapshot từ Order Service (VNPAY / MOMO / COD). */
    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    /** Ghi chú giao hàng (từ orders.note). */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "estimated_date")
    private LocalDate estimatedDate;

    /** Link PDF label. */
    @Column(name = "shipping_label_url", columnDefinition = "TEXT")
    private String shippingLabelUrl;

    /** ADDRESS_MAPPING_FAILED / CARRIER_API_ERROR khi status = PENDING. */
    @Column(name = "failure_reason", length = 50)
    private String failureReason;

    /** Số lần retry job đã thử tạo đơn. */
    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

