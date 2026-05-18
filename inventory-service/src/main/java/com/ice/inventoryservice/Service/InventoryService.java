package com.ice.inventoryservice.Service;

import com.ice.inventoryservice.DTO.Request.Inventory.GetInventoryByVariantRequest;
import com.ice.inventoryservice.DTO.Request.Inventory.ItemReserveRequest;
import com.ice.inventoryservice.DTO.Request.Inventory.ReleaseRequest;
import com.ice.inventoryservice.DTO.Request.Inventory.ReserveRequest;
import com.ice.inventoryservice.DTO.Response.Inventory.*;
import com.ice.inventoryservice.Entity.Inventory;
import com.ice.inventoryservice.Entity.StockReservation;
import com.ice.inventoryservice.Enum.ErrorCode;
import com.ice.inventoryservice.Enum.StockStatus;
import com.ice.inventoryservice.Exception.InsufficientStockException;
import com.ice.inventoryservice.Exception.ResourceNotFoundException;
import com.ice.inventoryservice.Repository.InventoryRepo;
import com.ice.inventoryservice.Repository.StockReservationRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepo inventoryRepo;
    private final StockReservationService stockReservationService;

    public InventoryResponse getInventory(UUID variantId)
    {
        Inventory inventory = inventoryRepo.findByVariantId(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("inventory not found", ErrorCode.INVENTORY_NOT_FOUND));


        return toInventoryResponse(inventory);
    }

    public GetInventoryByVariantResponse getByVariants(GetInventoryByVariantRequest request)
    {
        List<Inventory> inventories = inventoryRepo.findAllByVariantIdIn(request.getVariantIds().stream().map(UUID::fromString).toList());

        return new GetInventoryByVariantResponse(inventories.stream().map(
                this::toInventoryResponse
        ).toList());
    }

    @Transactional
    public ReserveResponseSuccess reserveOrder(ReserveRequest request)
    {
        Map<UUID, Integer> itemsMap = new HashMap<>();
        List<UUID> variantIds = new ArrayList<>();
        for(ItemReserveRequest item: request.getItems())
        {
            UUID variantId = UUID.fromString(item.getVariantId());
            itemsMap.put(variantId, item.getQty());
            variantIds.add(variantId);
        }

        List<Inventory> inventories = inventoryRepo.findAllByVariantIdInOrderByVariantId(variantIds);

        if(inventories.size() != variantIds.size())
            throw new ResourceNotFoundException("has variantIds not found in inventory", ErrorCode.INVENTORY_NOT_FOUND);

        List<ItemReserveResponseFail> itemReserveResponseFails = new ArrayList<>();

        inventories.forEach(inventory -> {
                if(inventory.getAvailableQty() < itemsMap.get(inventory.getVariantId()))
                    itemReserveResponseFails.add(new ItemReserveResponseFail(
                            inventory.getVariantId().toString(),
                            inventory.getSku(),
                            itemsMap.get(inventory.getVariantId()),
                            inventory.getAvailableQty()
                    ));
        });
        if(!itemReserveResponseFails.isEmpty())
            throw new InsufficientStockException("insufficient stock", "INSUFFICIENT_STOCK", itemReserveResponseFails);


        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);  // thêm ZoneOffset.UTC
        LocalDateTime expiresAt = now.plusMinutes(15);

        for(Inventory inventory : inventories)
        {
            Integer qty = itemsMap.get(inventory.getVariantId());
            inventory.setReservedQty(inventory.getReservedQty() + qty);

            stockReservationService.insertStockReservation(request.getOrderId(), inventory.getVariantId().toString(), qty, expiresAt);
        }
        inventoryRepo.saveAll(inventories);

        return new ReserveResponseSuccess(
                true,
                request.getOrderId(),
                now.toInstant(ZoneOffset.UTC),       // Instant
                expiresAt.toInstant(ZoneOffset.UTC)
        );
    }

    @Transactional
    public ReleaseResponse releaseOrder(ReleaseRequest request)
    {
        List<StockReservation> stockReservations = stockReservationService.getAllByOrderIdWithStatusRESERVED(request.getOrderId());

        if (stockReservations.isEmpty())
            throw new ResourceNotFoundException("no reserved stock found for orderId", ErrorCode.INVENTORY_NOT_FOUND);

        Map<UUID, Integer> itemsMap = new HashMap<>();
        List<UUID> variantIds = new ArrayList<>();
        for(StockReservation item: stockReservations)
        {
            itemsMap.put(item.getVariantId(), item.getQty());
            variantIds.add(item.getVariantId());
        }

        List<Inventory> inventories = inventoryRepo.findAllByVariantIdInOrderByVariantId(variantIds);

        for(Inventory inventory : inventories)
        {
            Integer qty = itemsMap.get(inventory.getVariantId());
            inventory.setReservedQty(inventory.getReservedQty() - qty);
        }
        stockReservationService.updateStatusRelease(stockReservations);
        inventoryRepo.saveAll(inventories);

        return new ReleaseResponse(
                true,
                request.getOrderId(),
                Instant.now()
        );
    }

    private InventoryResponse toInventoryResponse(Inventory inventory)
    {
        return new InventoryResponse(
            inventory.getVariantId().toString(),
            inventory.getSku(),
            inventory.getStockQty(),
            inventory.getReservedQty(),
            inventory.getAvailableQty(),
            inventory.getAvailableQty() > 10 ? StockStatus.IN_STOCK : inventory.getAvailableQty() > 0 ? StockStatus.LOW_STOCK : StockStatus.OUT_OF_STOCK
        );
    }


}
