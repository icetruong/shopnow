package com.ice.inventoryservice.Service;

import com.ice.inventoryservice.Entity.StockReservation;
import com.ice.inventoryservice.Repository.StockReservationRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockReservationService {
    private final StockReservationRepo reservationRepo;

    public void insertStockReservation(String orderId, String variantId, Integer qty, LocalDateTime expiresAt)
    {
        StockReservation stockReservation = StockReservation.builder()
                .orderId(UUID.fromString(orderId))
                .variantId(UUID.fromString(variantId))
                .qty(qty)
                .expiresAt(expiresAt)
                .build();

        reservationRepo.save(stockReservation);
    }
}
