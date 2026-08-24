package com.ice.orderservice.Specification;

import com.ice.orderservice.Entity.Order;
import com.ice.orderservice.Enum.OrderStatus;
import com.ice.orderservice.Enum.PaymentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.UUID;

public class OrderSpecification {

    public static Specification<Order> hasStatus(OrderStatus status)
    {
        return (root, query, cb) ->
                status == null
                        ? null
                        : cb.equal(root.get("status"), status);
    }

    public static Specification<Order> hasPaymentStatus(PaymentStatus paymentStatus)
    {
        return (root, query, cb) ->
                paymentStatus == null
                        ? null
                        : cb.equal(root.get("paymentStatus"), paymentStatus);
    }

    public static Specification<Order> hasKeyword(String keyword)
    {
        return (root, query, cb) ->
                keyword == null
                        ? null
                        : cb.like(cb.lower(root.get("orderCode")), "%" + keyword.toLowerCase() + "%");
    }

    public static Specification<Order> hasUserId(UUID userId)
    {
        return (root, query, cb) ->
                userId == null
                        ? null
                        : cb.equal(root.get("userId"), userId);
    }

    public static Specification<Order> betweenDays(LocalDateTime start, LocalDateTime end)
    {
        return (root, query, cb) ->
                start == null || end == null
                        ? null
                        : cb.between(root.get("createdAt"), start, end);
    }

}
