package com.ice.shippingservice.Specification;

import com.ice.shippingservice.Entity.Shipment;
import com.ice.shippingservice.Enum.ShipmentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.UUID;

public class ShipmentSpecification {

    public static Specification<Shipment> hasStatus(ShipmentStatus status)
    {
        return (root, query, cb) ->
                status == null
                        ? null
                        : cb.equal(root.get("status"), status);
    }

    public static Specification<Shipment> hasCarrier(String carrier)
    {
        return (root, query, cb) ->
                carrier == null
                        ? null
                        : cb.equal(root.get("carrier"), carrier);
    }

    public static Specification<Shipment> hasFailureReason(String failureReason)
    {
        return (root, query, cb) ->
                failureReason == null
                        ? null
                        : cb.equal(root.get("failureReason"), failureReason);
    }

    public static Specification<Shipment> hasKeyword(String keyword)
    {
        return (root, query, cb) -> {
            if (keyword == null) return null;
            String like = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("trackingCode")), like),
                    cb.like(cb.lower(root.get("orderCode")), like)
            );
        };
    }

    public static Specification<Shipment> hasOrderId(UUID orderId)
    {
        return (root, query, cb) ->
                orderId == null
                        ? null
                        : cb.equal(root.get("orderId"), orderId);
    }

    public static Specification<Shipment> hasUserId(UUID userId)
    {
        return (root, query, cb) ->
                userId == null
                        ? null
                        : cb.equal(root.get("userId"), userId);
    }

    public static Specification<Shipment> betweenDays(LocalDateTime start, LocalDateTime end)
    {
        return (root, query, cb) ->
                start == null || end == null
                        ? null
                        : cb.between(root.get("createdAt"), start, end);
    }
}
