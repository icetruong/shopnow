package com.ice.orderservice.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_order_items_order_id", columnList = "order_id"),
        @Index(name = "idx_order_items_variant_id", columnList = "variant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", length = 255, nullable = false)
    private String productName;

    @Column(name = "sku", length = 100, nullable = false)
    private String sku;

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "size", length = 20)
    private String size;

    @Column(name = "thumbnail", columnDefinition = "TEXT")
    private String thumbnail;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "qty", nullable = false)
    private Integer qty;

    @Column(name = "subtotal", nullable = false)
    private Long subtotal;
}
