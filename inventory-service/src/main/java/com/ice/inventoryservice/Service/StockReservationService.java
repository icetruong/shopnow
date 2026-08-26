package com.ice.inventoryservice.Service;

import com.ice.inventoryservice.Entity.StockReservation;
import com.ice.inventoryservice.Enum.StockReservationStatus;
import com.ice.inventoryservice.Repository.StockReservationRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockReservationService {
    private final StockReservationRepo stockReservationRepo;

    public void insertStockReservation(String orderId, String variantId, Integer qty, LocalDateTime expiresAt)
    {
        StockReservation stockReservation = StockReservation.builder()
                .orderId(UUID.fromString(orderId))
                .variantId(UUID.fromString(variantId))
                .qty(qty)
                .expiresAt(expiresAt)
                .build();

        stockReservationRepo.save(stockReservation);
    }

    public List<StockReservation> getAllByOrderIdWithStatusRESERVED(String orderId)
    {
        return stockReservationRepo.findAllByOrderIdAndStatus(UUID.fromString(orderId), StockReservationStatus.RESERVED);
    }

    public List<StockReservation> getAllByOrderIdWithStatusDEDUCT(String orderId)
    {
        return stockReservationRepo.findAllByOrderIdAndStatus(UUID.fromString(orderId), StockReservationStatus.DEDUCTED);
    }

    public void updateStatusRelease(List<StockReservation> stockReservations)
    {
        stockReservations.forEach(stockReservation -> stockReservation.setStatus(StockReservationStatus.RELEASED));
        stockReservationRepo.saveAll(stockReservations);
    }

    public void updateStatusDeduct(List<StockReservation> stockReservations) {
        stockReservations.forEach(stockReservation -> stockReservation.setStatus(StockReservationStatus.DEDUCTED));
        stockReservationRepo.saveAll(stockReservations);
    }
}
