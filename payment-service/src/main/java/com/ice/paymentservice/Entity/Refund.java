package com.ice.paymentservice.Entity;

import com.ice.paymentservice.Enum.RefundStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refunds", indexes = {
        @Index(name = "idx_refunds_order_id", columnList = "order_id", unique = true),
        @Index(name = "idx_refunds_payment_id", columnList = "payment_id"),
        @Index(name = "idx_refunds_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    /** Quan hệ chỉ đọc tới Payment, dùng cùng cột payment_id (không tạo thêm FK write) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", insertable = false, updatable = false)
    private Payment payment;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /** Số tiền hoàn */
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "reason", nullable = false, length = 50)
    private String reason;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefundStatus status = RefundStatus.REFUNDING;

    /** Mã refund từ cổng */
    @Column(name = "gateway_refund_id", length = 100)
    private String gatewayRefundId;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
