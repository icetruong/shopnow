package com.ice.inventoryservice.Service;

import com.ice.inventoryservice.DTO.Request.Admin.AdjustQuantityOrderRequest;
import com.ice.inventoryservice.DTO.Request.Admin.ImportQuantityOrderRequest;
import com.ice.inventoryservice.DTO.Request.Inventory.*;
import com.ice.inventoryservice.DTO.Response.Admin.*;
import com.ice.inventoryservice.DTO.Response.Inventory.*;
import com.ice.inventoryservice.Entity.Inventory;
import com.ice.inventoryservice.Entity.StockReservation;
import com.ice.inventoryservice.Enum.ErrorCode;
import com.ice.inventoryservice.Enum.StockStatus;
import com.ice.inventoryservice.Enum.StockTransactionType;
import com.ice.inventoryservice.Exception.InsufficientStockException;
import com.ice.inventoryservice.Exception.InventoryExistException;
import com.ice.inventoryservice.Exception.ResourceNotFoundException;
import com.ice.inventoryservice.Repository.InventoryRepo;
import com.ice.inventoryservice.Util.InventorySpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
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
    private final KafkaProducerService kafkaProducerService;
    private final StockTransactionService stockTransactionService;

    public InventoryResponse addInventory(InsertInventoryRequest request)
    {
        if(inventoryRepo.existsByVariantId(UUID.fromString(request.getVariantId())))
            throw new InventoryExistException("Inventory cho variantId này đã tồn tại.");

        Inventory inventory = Inventory.builder()
                .variantId(UUID.fromString(request.getVariantId()))
                .sku(request.getSku())
                .stockQty(request.getStockQty())
                .build();

        inventoryRepo.save(inventory);

        return toInventoryResponse(inventory);
    }

    @Transactional
    public ListInventoryResponse addAllInventory(ListItemStockRequest request)
    {
        List<UUID> allIds = request.getItems().stream()
                .map(i -> UUID.fromString(i.getVariantId()))
                .toList();

        List<String> conflicts = inventoryRepo.findAllByVariantIdIn(allIds).stream()
                .map(i -> i.getVariantId().toString())
                .toList();

        if (!conflicts.isEmpty())
            throw new InventoryExistException("Một số variantId đã tồn tại trong inventory.", conflicts);


        List<Inventory> inventories = new ArrayList<>();
        request.getItems().forEach(
                item -> {
                    inventories.add(Inventory.builder()
                            .variantId(UUID.fromString(item.getVariantId()))
                            .sku(item.getSku())
                            .stockQty(item.getStockQty())
                            .build()
                    );
                }
        );

        inventoryRepo.saveAll(inventories);

        return new ListInventoryResponse(inventories.stream().map(
                this::toInventoryResponse
        ).toList());
    }

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



        ReserveResponseSuccess response = new ReserveResponseSuccess(
                true,
                request.getOrderId(),
                now.toInstant(ZoneOffset.UTC),       // Instant
                expiresAt.toInstant(ZoneOffset.UTC)
        );

        kafkaProducerService.publishStockChangeEvent(variantIds.stream().map(
                UUID::toString
        ).toList());
        return response;
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

        ReleaseResponse response = new ReleaseResponse(
                true,
                request.getOrderId(),
                Instant.now()
        );

        kafkaProducerService.publishStockChangeEvent(variantIds.stream().map(
                UUID::toString
        ).toList());
        return response;
    }

    @Transactional
    public DeductResponse deductOrder(DeductRequest request)
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
            inventory.setSoldQty(inventory.getSoldQty() + qty);
            inventory.setStockQty(inventory.getStockQty() - qty);

            String note = "Trừ hàng đơn" + request.getOrderId();
            stockTransactionService.addStockTransaction(
                    StockTransactionType.DEDUCT,
                    note,
                    qty,
                    inventory.getStockQty() + qty,
                    inventory
            );
        }
        stockReservationService.updateStatusDeduct(stockReservations);
        inventoryRepo.saveAll(inventories);
        for(Inventory inventory : inventories) {
            if(inventory.getStockQty() <= inventory.getLowStockThreshold())
                kafkaProducerService.publishLowWarningEvent(inventory);
        }
        kafkaProducerService.publishStockChangeEvent(variantIds.stream().map(
                UUID::toString
        ).toList());
        return new DeductResponse(
                true,
                request.getOrderId(),
                Instant.now()
        );
    }

    @Transactional
    public ReturnResponse returnOrder(ReturnRequest request) {
        List<StockReservation> stockReservations = stockReservationService.getAllByOrderIdWithStatusDEDUCT(request.getOrderId());

        if (stockReservations.isEmpty())
            throw new ResourceNotFoundException("no reserved stock found for orderId", ErrorCode.RESERVATION_NOT_FOUND);

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
            inventory.setSoldQty(inventory.getSoldQty() - qty);
            inventory.setStockQty(inventory.getStockQty() + qty);

            String note = "Trả lại đơn sau khi deduct " + request.getOrderId();
            stockTransactionService.addStockTransaction(
                    StockTransactionType.RELEASE,
                    note,
                    qty,
                    inventory.getStockQty() - qty,
                    inventory
            );
        }
        stockReservationService.updateStatusRelease(stockReservations);
        inventoryRepo.saveAll(inventories);

        kafkaProducerService.publishStockChangeEvent(variantIds.stream().map(
                UUID::toString
        ).toList());

        return new ReturnResponse(
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


    public PageStockResponse<StockResponse> getStockForAdmin(Integer page, Integer size, UUID variantId, UUID productId, StockStatus status)
    {
        Specification<Inventory> specification = Specification.where(InventorySpecification.hasVariantId(variantId))
                .and(InventorySpecification.hasStatus(status));

        Page<Inventory> inventoryPage = inventoryRepo.findAll(specification, PageRequest.of(page, size));

        List<StockResponse> stockResponses = inventoryPage.get().map(
                inventory -> new StockResponse(
                        inventory.getVariantId().toString(),
                        inventory.getSku(),
                        null,
                        null,
                        null,
                        inventory.getStockQty(),
                        inventory.getReservedQty(),
                        inventory.getAvailableQty(),
                        inventory.getSoldQty(),
                        inventory.getAvailableQty() > 10 ? StockStatus.IN_STOCK : inventory.getAvailableQty() > 0 ? StockStatus.LOW_STOCK : StockStatus.OUT_OF_STOCK,
                        inventory.getUpdatedAt().toInstant(ZoneOffset.UTC)
                )
        ).toList();

        return new PageStockResponse<StockResponse>(
                stockResponses,
                inventoryPage.getNumber(),
                inventoryPage.getSize(),
                inventoryPage.getTotalElements(),
                inventoryPage.getTotalPages()
        );
    }

    @Transactional
    public ImportQuantityOrderResponse importQuantity(UUID variantId,ImportQuantityOrderRequest request)
    {
        Inventory inventory = inventoryRepo.findByVariantId(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("inventory not found", ErrorCode.INVENTORY_NOT_FOUND));

        Integer beforeQty = inventory.getStockQty();
        inventory.setStockQty(request.getQty() + inventory.getStockQty());
        stockTransactionService.addStockTransaction(
                StockTransactionType.IMPORT,
                request.getNote(),
                request.getQty(),
                beforeQty,
                inventory
                );

        inventoryRepo.save(inventory);
        kafkaProducerService.publishStockChangeEvent(List.of(variantId.toString()));
        return new ImportQuantityOrderResponse(
                inventory.getVariantId().toString(),
                beforeQty,
                request.getQty(),
                inventory.getStockQty(),
                inventory.getUpdatedAt().toInstant(ZoneOffset.UTC)
        );
    }

    @Transactional
    public AdjustQuantityOrderResponse adjustQuantity(UUID variantId, AdjustQuantityOrderRequest request)
    {
        Inventory inventory = inventoryRepo.findByVariantId(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("inventory not found", ErrorCode.INVENTORY_NOT_FOUND));

        Integer beforeQty = inventory.getStockQty();
        inventory.setStockQty(request.getNewQty());
        stockTransactionService.addStockTransaction(
                StockTransactionType.ADJUSTMENT,
                request.getNote(),
                request.getNewQty() - beforeQty,
                beforeQty,
                inventory
        );

        inventoryRepo.save(inventory);
        kafkaProducerService.publishStockChangeEvent(List.of(variantId.toString()));
        return new AdjustQuantityOrderResponse(
                inventory.getVariantId().toString(),
                beforeQty,
                inventory.getStockQty(),
                inventory.getStockQty() - beforeQty,
                inventory.getUpdatedAt().toInstant(ZoneOffset.UTC)
        );
    }
}
