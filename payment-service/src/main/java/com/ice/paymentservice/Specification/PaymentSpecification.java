package com.ice.paymentservice.Specification;

import com.ice.paymentservice.Entity.Payment;
import com.ice.paymentservice.Enum.PaymentMethod;
import com.ice.paymentservice.Enum.PaymentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class PaymentSpecification {
    public static Specification<Payment> hasStatus(PaymentStatus status)
    {
        return (root, query, cb) ->
                status == null
                        ? null
                        : cb.equal(root.get("status"), status);
    }

    public static Specification<Payment> hasMethod(PaymentMethod method)
    {
        return (root, query, cb) ->
                method == null
                        ? null
                        : cb.equal(root.get("method"), method);
    }

    public static Specification<Payment> betweenDays(LocalDateTime start, LocalDateTime end)
    {
        return (root, query, cb) ->
                start == null || end == null
                        ? null
                        : cb.between(root.get("createdAt"), start, end);
    }
}
