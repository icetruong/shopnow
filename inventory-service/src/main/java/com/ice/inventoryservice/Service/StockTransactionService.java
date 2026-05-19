package com.ice.inventoryservice.Service;

import com.ice.inventoryservice.DTO.Response.Admin.HistoryTransactionResponse;
import com.ice.inventoryservice.DTO.Response.Admin.PageStockResponse;
import com.ice.inventoryservice.Entity.Inventory;
import com.ice.inventoryservice.Entity.StockTransaction;
import com.ice.inventoryservice.Enum.StockTransactionType;
import com.ice.inventoryservice.Repository.StockTransactionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockTransactionService {

    private final StockTransactionRepo stockTransactionRepo;

    public void addStockTransaction(StockTransactionType type, String note, Integer qty, Integer beforeQty, Inventory inventory)
    {
        StockTransaction stockTransaction = StockTransaction.builder()
                .variantId(inventory.getVariantId())
                .type(type)
                .qty(qty)
                .qtyBefore(beforeQty)
                .qtyAfter(inventory.getStockQty())
                .note(note)
                .build();

        stockTransactionRepo.save(stockTransaction);
    }

    public PageStockResponse<HistoryTransactionResponse> getHistory(UUID variantId, Integer page, Integer size, LocalDate startDate, LocalDate endDate, StockTransactionType type)
    {
        Page<StockTransaction> stockTransactionPage = stockTransactionRepo.findByDateRangeAndType(
                variantId,
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay(),
                type,
                PageRequest.of(page, size)
        );

        List<HistoryTransactionResponse> historyTransactionResponses = stockTransactionPage.get().map(
                stockTransaction -> new HistoryTransactionResponse(
                        stockTransaction.getId().toString(),
                        stockTransaction.getType(),
                        stockTransaction.getQty(),
                        stockTransaction.getQtyBefore(),
                        stockTransaction.getQtyAfter(),
                        stockTransaction.getOrderId() == null ? null : stockTransaction.getOrderId().toString(),
                        stockTransaction.getNote(),
                        stockTransaction.getCreatedAt().toInstant(ZoneOffset.UTC)
                )
        ).toList();

        return new PageStockResponse<HistoryTransactionResponse>(
                historyTransactionResponses,
                stockTransactionPage.getNumber(),
                stockTransactionPage.getSize(),
                stockTransactionPage.getTotalElements(),
                stockTransactionPage.getTotalPages()
        );
    }

}
