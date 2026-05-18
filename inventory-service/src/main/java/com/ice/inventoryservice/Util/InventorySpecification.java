package com.ice.inventoryservice.Util;

import com.ice.inventoryservice.Entity.Inventory;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

public class InventorySpecification {
    public static Specification<Inventory> hasVariantId(UUID variantId)
    {
        return (root, query, cb) ->
                variantId == null || variantId.isEmpty()
                        ? null
                        : root.get("variantId");
    }

}
