package com.ice.orderservice.Entity;

import com.ice.orderservice.Enum.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_status_history", indexes = {
        @Index(name = "idx_order_status_history_order_id", columnList = "order_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "from_status", length = 20)
    @Enumerated(EnumType.STRING)
    private OrderStatus fromStatus;

    @Column(name = "to_status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus toStatus;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
    
    // Vai trò của bên gây ra thay đổi: "USER" | "ADMIN" | "SYSTEM" (không lưu userId).
    @Column(name = "changed_by", length = 10, nullable = false)
    private String changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
