package com.ice.inventoryservice.Scheduler;

import com.ice.inventoryservice.DTO.Request.Inventory.ItemReserveRequest;
import com.ice.inventoryservice.DTO.Response.Inventory.ReleaseResponse;
import com.ice.inventoryservice.Entity.Inventory;
import com.ice.inventoryservice.Entity.StockReservation;
import com.ice.inventoryservice.Enum.StockReservationStatus;
import com.ice.inventoryservice.Repository.InventoryRepo;
import com.ice.inventoryservice.Repository.StockReservationRepo;
import com.ice.inventoryservice.Service.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SchedulerStockReserve {
    private final StockReservationRepo stockReservationRepo;
    private final InventoryRepo inventoryRepo;
    private final KafkaProducerService kafkaProducerService;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void autoExpireReservations()
    {
        List<StockReservation> stockReservations = stockReservationRepo.findAllByStatusAndExpiresAtLessThan(
                StockReservationStatus.RESERVED, LocalDateTime.now(ZoneOffset.UTC));

        if (stockReservations.isEmpty())
            return;

        // Accumulate total qty per variantId across multiple orders
        Map<UUID, Integer> itemsMap = new HashMap<>();
        List<UUID> variantIds = new ArrayList<>();

        for (StockReservation stockReservation : stockReservations)
        {
            UUID variantId = stockReservation.getVariantId();
            if (!itemsMap.containsKey(variantId))
                variantIds.add(variantId);
            itemsMap.merge(variantId, stockReservation.getQty(), Integer::sum);
            stockReservation.setStatus(StockReservationStatus.EXPIRED);
        }

        List<Inventory> inventories = inventoryRepo.findAllByVariantIdInOrderByVariantId(variantIds);

        for (Inventory inventory : inventories)
        {
            Integer qty = itemsMap.get(inventory.getVariantId());
            inventory.setReservedQty(inventory.getReservedQty() - qty);
        }

        stockReservationRepo.saveAll(stockReservations);
        inventoryRepo.saveAll(inventories);

        // Publish one stock.released event per expired order
        Instant now = Instant.now();
        Map<UUID, List<StockReservation>> byOrder = stockReservations.stream()
                .collect(Collectors.groupingBy(StockReservation::getOrderId));

        for (Map.Entry<UUID, List<StockReservation>> entry : byOrder.entrySet())
        {
            String orderId = entry.getKey().toString();
            List<ItemReserveRequest> orderItems = entry.getValue().stream()
                    .map(r -> new ItemReserveRequest(r.getVariantId().toString(), r.getQty()))
                    .toList();
            kafkaProducerService.publishReleaseEvent(
                    new ReleaseResponse(true, orderId, now),
                    orderItems,
                    "RESERVATION_EXPIRED"
            );
        }
    }
}
