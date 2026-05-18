package com.ice.inventoryservice.Util;

import com.ice.inventoryservice.Entity.Inventory;
import com.ice.inventoryservice.Enum.StockStatus;
import jakarta.persistence.criteria.Expression;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class InventorySpecification {
    public static Specification<Inventory> hasVariantId(UUID variantId)
    {
        return (root, query, cb) ->
                variantId == null
                        ? null
                        : cb.equal(root.get("variantId"), variantId);
    }

    public static Specification<Inventory> hasStatus(StockStatus status)
    {
        return (root, query, cb) ->
        {
            if(status == null)
                return null;

            Expression<Integer> available = cb.diff(
                    root.<Integer>get("stockQty"),
                    root.<Integer>get("reservedQty")
            );


            return switch (status) {
                case IN_STOCK -> cb.greaterThan(available, 10);
                case LOW_STOCK -> cb.and(
                        cb.greaterThan(available, 0),
                        cb.lessThanOrEqualTo(available, 10)
                );
                case OUT_OF_STOCK -> cb.equal(available, 0);
            };
        };

    }

    // product làm sau



}
