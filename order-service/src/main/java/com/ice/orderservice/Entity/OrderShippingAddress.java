package com.ice.orderservice.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "order_shipping_address", indexes = {
        @Index(name = "idx_order_shipping_order_id", columnList = "order_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderShippingAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "full_name", length = 100, nullable = false)
    private String fullName;

    @Column(name = "phone", length = 15, nullable = false)
    private String phone;

    @Column(name = "province", length = 100, nullable = false)
    private String province;

    @Column(name = "district", length = 100, nullable = false)
    private String district;

    @Column(name = "ward", length = 100, nullable = false)
    private String ward;

    @Column(name = "street_detail", length = 255, nullable = false)
    private String streetDetail;
}
