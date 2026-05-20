package com.ice.inventoryservice.Exception;

import java.util.ArrayList;
import java.util.List;

public class InventoryExistException extends RuntimeException {
    List<String> variantIds = new ArrayList<>();
    public InventoryExistException(String message) {
        super(message);
    }

    public InventoryExistException(String message, List<String> variantIds)
    {
        super(message);
        this.variantIds = variantIds;
    }
}
