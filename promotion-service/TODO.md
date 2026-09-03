Việc bạn làm tiếp

promotion-service (bên bạn đang code)

1. flash_sales / flash_sale_items — entity + repo (đã có entity FlashSale, FlashSaleItem, FlashSalePurchase rồi, chỉ thiếu repo).
2. POST /admin/flash-sales — tạo (chỉ DB).
3. POST /admin/flash-sales/{id}/warmup — 1 Feign/RestClient gọi sang inventory /admin/flash-sale-stock + set is_warmed.
4. POST /internal/flash-sales/purchase — gọi inventory /irve, map lỗi passthrough, gắn flashPrice.
5. POST /internal/flash-sales/rollback — gọi inventory /internal/stock/flash-sale/release.
6. GET /flash-sales/active — đọc từ DB.
7. Kafka consumer flash.purchased → ghi flash_sale_purchases + sold_qty, dedup processed:event:{eventId}.
8. Tạo client (Feign InventoryClient) + cấu hình X-Intern

inventory-service (sửa lại phần đã viết)

1. FlashSaleService.reserve() → thay 3 op rời bằng Lua sc resources), nhận thêm limitPerUser.
2. Đổi flash:user từ SET "1" → counter INCRBY trong Lua; key path thêm :{variantId}.
3. Thêm flash:done:{orderId}:{variantId} SETNX cho idempo
4. Thêm reserve request field limitPerUser + FlashSaleReserveRequest.
5. Thêm endpoint + service POST /internal/stock/flash-sala).
6. FlashSaleReservedEvent → đổi tên/eventType thành flash.purchased, bỏ field thừa, đúng envelope KafkaEvent<T>.
7. Thêm exception + FLASH_SALE_NOT_ACTIVE vào ErrorCode.