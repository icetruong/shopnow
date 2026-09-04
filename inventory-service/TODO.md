inventory-service (sửa lại phần đã viết)

1. FlashSaleService.reserve() → thay 3 op rời bằng Lua sc resources), nhận thêm limitPerUser.
2. Đổi flash:user từ SET "1" → counter INCRBY trong Lua; key path thêm :{variantId}.
3. Thêm flash:done:{orderId}:{variantId} SETNX cho idempo
4. Thêm reserve request field limitPerUser + FlashSaleReserveRequest.
5. Thêm endpoint + service POST /internal/stock/flash-sala).
6. FlashSaleReservedEvent → đổi tên/eventType thành flash.purchased, bỏ field thừa, đúng envelope KafkaEvent<T>.
7. Thêm exception + FLASH_SALE_NOT_ACTIVE vào ErrorCode.