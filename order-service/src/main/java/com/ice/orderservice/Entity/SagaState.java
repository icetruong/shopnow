package com.ice.orderservice.Entity;

import com.ice.orderservice.Enum.CurrentStep;
import com.ice.orderservice.Enum.SagaStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "saga_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SagaState {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "order_id", unique = true)
    private Order order;

    @Column(name = "saga_status", length = 20, nullable = false)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private SagaStatus sagaStatus = SagaStatus.STARTED;

    @Column(name = "current_step", length = 30, nullable = false)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private CurrentStep currentStep = CurrentStep.ORDER_CREATED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completed_steps", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> completedSteps = new ArrayList<>();

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
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
