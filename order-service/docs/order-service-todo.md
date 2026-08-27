# order-service — Những gì còn thiếu / cần làm để hoàn thiện

> Rà soát toàn bộ `order-service` (không tính phần crash recovery — cái đó đã có file
> riêng: [`crash-recovery.md`](./crash-recovery.md)).
>
> Đối chiếu **code hiện tại** ↔ **`orderServiceApiSpec.md`** (spec do chính bạn viết) ↔
> **`Project_context.md`** (convention chung của ShopNow).
>
> Mỗi mục có mức độ: 🔴 CRITICAL (sai/hỏng, phải sửa) · 🟠 HIGH (thiếu quan trọng) ·
> 🟡 MEDIUM (nên làm) · ⚪ LOW (dọn dẹp).

---

## Mục lục

1. [Bug logic — sai hoặc sẽ hỏng](#1-bug-logic--sai-hoặc-sẽ-hỏng)
2. [Tính năng thiếu so với spec của bạn](#2-tính-năng-thiếu-so-với-spec-của-bạn)
3. [Resilience & vận hành](#3-resilience--vận-hành)
4. [Config & hạ tầng](#4-config--hạ-tầng)
5. [Chất lượng code / nợ kỹ thuật](#5-chất-lượng-code--nợ-kỹ-thuật)
6. [Test](#6-test)
7. [Bảng tổng hợp theo thứ tự nên làm](#7-bảng-tổng-hợp-theo-thứ-tự-nên-làm)

---

## 1. Bug logic — sai hoặc sẽ hỏng

### 1.1. 🔴 Hủy đơn online lúc đang chờ thanh toán → mất tiền của khách

**File:** `OrderService.doCancelOrder()` + `PaymentProcessedListener.handlePaymentProcessed()`

**Kịch bản:**
```
1. Khách đặt đơn online (VNPAY), order = PENDING, completedSteps = [ORDER_CREATED, STOCK_RESERVED]
2. Khách đang ở trang VNPay thì bấm "Hủy đơn" ở app
   -> doCancelOrder: completedSteps KHÔNG có PAYMENT_PROCESSED
   -> nhánh else: order = CANCELLED, release stock. KHÔNG refund (đúng, vì tiền chưa trừ)
3. Nhưng khách vẫn bấm thanh toán trên VNPay -> thành công
   -> payment.processed (SUCCESS) tới order-service
   -> listener: order.getStatus() == CANCELLED -> log "đã xử lý rồi" -> return
   -> KHÔNG refund, KHÔNG báo ai
=> Tiền đã bị trừ, đơn đã hủy, không ai hoàn tiền.
```

**Cách sửa:** trong `PaymentProcessedListener`, nhánh `SUCCESS` phải xử lý riêng trường hợp
order đã `CANCELLED`:
```java
if (payload.getStatus() == PaymentGatewayStatus.SUCCESS && order.getStatus() == OrderStatus.CANCELLED) {
    // Khách đã hủy trước khi tiền về -> phải hoàn tiền
    order.setStatus(OrderStatus.REFUNDING);
    order.setPaymentStatus(PaymentStatus.PAID);
    PaymentInternalResponse payment = paymentClient.getPaymentByOrderId(order.getId().toString());
    paymentClient.refundPayment(new RefundPaymentRequest(order.getId().toString(),
            order.getTotalAmount(), "CANCELLED_BEFORE_PAYMENT"), payment.getPaymentId());
    orderRepo.save(order);
    return;
}
```
Hoặc chặn hẳn: không cho hủy đơn online đang `PENDING` (bắt khách chờ payment resolve
hoặc chờ reservation expire). Đơn giản hơn nhưng UX kém.

---

### 1.2. 🔴 `order_status_history.changed_by` chỉ dài 20 ký tự nhưng đang nhét UUID (36 ký tự)

**File:** `Entity/OrderStatusHistory.java` (`@Column(name = "changed_by", length = 20)`)
+ mọi chỗ gọi `.changedBy(userId)` trong `OrderService`.

`userId` là UUID string (36 ký tự). Nhét vào cột `VARCHAR(20)`:
- PostgreSQL: **ném lỗi `value too long for type character varying(20)`** → cả `createOrder`
  rollback.
- (Chỉ chưa lộ ra vì có thể bạn chưa chạy full flow với user thật.)

**Spec (`orderServiceApiSpec.md` dòng 586) nói `changed_by` = `SYSTEM / USER / ADMIN`** —
tức là **vai trò**, không phải id. Đang lưu sai thứ.

**Cách sửa:** quyết định lưu gì:
- Nếu muốn audit "ai bấm": tách 2 cột — `changed_by_role VARCHAR(10)` + `changed_by_id UUID`.
- Nếu theo spec: chỉ lưu role, nới cột `VARCHAR(10)`, truyền `"USER"` / `"ADMIN"` / `"SYSTEM"`
  thay vì `userId`.

---

### 1.3. 🟠 `generateOrderCode()` — trùng mã + không đúng format spec

**File:** `OrderService.generateOrderCode()`
```java
return "ORD" + System.currentTimeMillis();
```
- 2 request trong cùng 1 mili-giây → trùng `order_code` → vi phạm `UNIQUE` → 500.
- Spec (dòng 501, 911) muốn `SN{yyMMdd}{counter}` (vd `SN240115001`), đếm tăng dần theo
  ngày bằng Redis key `order:code:counter:{date}`.

**Cách sửa (bản đơn giản, chưa cần Redis):** dùng sequence DB theo ngày, hoặc
`SN + yyMMdd + %04d` với một bảng counter. Bản chuẩn: Redis `INCR order:code:counter:{yyyyMMdd}`
với TTL hết ngày.

---

### 1.4. 🟠 Lỗi từ service khác bị nuốt mất, client không biết vì sao

**File:** `Client/InventoryClient.java`, `Client/PaymentClient.java`

```java
.onStatus(HttpStatusCode::isError, (req, res) -> {
    ApiResponse<?> error = objectMapper.readValue(res.getBody(), ApiResponse.class);  // parse ra
    throw new InventoryReserveFailedException("Sản phẩm trong đơn không đủ hàng để đặt");  // rồi vứt đi
});
```

`error` được đọc ra nhưng **không dùng**. Inventory trả về danh sách `ItemReserveResponseFail`
(sku nào thiếu, còn bao nhiêu) → mất sạch. Client chỉ nhận message chung chung.

**Cách sửa:** đưa `error.getMessage()` / `error.getErrorCode()` / danh sách item thiếu vào
exception, rồi `HandlerGlobalException` trả về cho client. Spec `POST /orders` (409) nên
liệt kê được item nào hết hàng.

---

### 1.5. 🟠 Race condition: 2 luồng cùng sửa 1 order → lost update

**File:** `Entity/Order.java`, `Entity/SagaState.java` — không có `@Version` (optimistic lock).

Luồng HTTP `cancel` (thread web) và luồng `payment.processed` (thread Kafka listener) có thể
chạy **song song** trên cùng một order:
```
T1 (cancel):   load order (PENDING) ...                    ... set CANCELLED, save
T2 (listener): load order (PENDING) ... set CONFIRMED, save ...
=> tùy thứ tự commit: order CONFIRMED nhưng stock đã release, hoặc CANCELLED nhưng đã publish order.confirmed
```
Kafka chỉ đảm bảo thứ tự *giữa các message trong cùng partition*, không đảm bảo với thread HTTP.

**Cách sửa:** thêm `@Version private Long version;` vào `Order` (và `SagaState`), bắt
`OptimisticLockingFailureException` để retry hoặc báo lỗi "đơn đang được xử lý, thử lại".
Hoặc khóa dòng khi đọc: `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)` trong repo).

---

### 1.6. 🟡 `doCancelOrder` gọi refund REST *trước khi* lưu trạng thái `REFUNDING`

**File:** `OrderService.doCancelOrder()` (dòng ~344–371)

```java
order.setStatus(OrderStatus.REFUNDING);      // mới set trong bộ nhớ
paymentClient.refundPayment(...);            // REST -> tiền đã hoàn ở payment-service
...
orderRepo.save(order);                        // giờ mới commit
```
Nếu crash giữa `refundPayment` và `save` → payment-service đã hoàn tiền, order vẫn `CONFIRMED`.
(Cùng họ với vấn đề ở `crash-recovery.md` mục C — ghi ra đây để nhớ sửa chung.)

**Cách sửa:** lưu `REFUNDING` ở một transaction ngắn *trước*, rồi mới gọi `refundPayment`,
rồi `payment.refunded` listener đưa về `REFUNDED`. Idempotency lo phần gọi refund 2 lần.

---

### 1.7. 🟡 Đọc order ngoài transaction — dựa vào Open-Session-In-View

**File:** `OrderService.getOrderDetail()`, `getOrders()`, `getOrderDetailInternal()` —
không có `@Transactional`.

`buildOrderDetailResponse` truy cập `order.getOrderItems()`, `order.getOrderStatusHistories()`
(lazy). Hiện chạy được **chỉ vì Spring Boot bật `open-in-view=true` mặc định** (anti-pattern,
giữ session mở tới tận lúc render response). Nếu tắt OSIV → `LazyInitializationException`.

**Cách sửa:** thêm `@Transactional(readOnly = true)` cho các method đọc; dùng
`JOIN FETCH` / `@EntityGraph` để lấy items + history trong 1 query; đặt
`spring.jpa.open-in-view=false` trong `application.properties`.

---

### 1.8. 🟡 `CreatedOrderRequest.note` đang bắt buộc (`@NotBlank`)

**File:** `DTO/Request/Order/CreatedOrderRequest.java`
```java
@NotBlank(message = "not is not empty")   // <- còn sai chính tả "not"
private String note;
```
Khách không ghi ghi chú thì không đặt được hàng — gần như chắc chắn sai. Bỏ `@NotBlank`,
để `note` optional. Sửa luôn message các field khác (`"not is not empty"`,
`"address Id is not empty"` → tiếng Việt cho nhất quán).

---

## 2. Tính năng thiếu so với spec của bạn

### 2.1. 🟠 `ShipmentUpdatedListener` — consume `shipment.updated` (CHƯA CÓ)

**Spec:** `orderServiceApiSpec.md` mục "Tiến trình logistics" (dòng 695–738) đặc tả rất kỹ,
nhưng **không có dòng code nào**. Không có `ShipmentUpdatedPayload` DTO, không có listener.

Hệ quả: `CONFIRMED → PROCESSING → SHIPPING → DELIVERED` **chỉ chuyển được bằng tay** qua
`PATCH /admin/orders/{orderId}/status`. Không có luồng tự động từ shipping-service.

**Cần làm:**
- DTO `ShipmentUpdatedPayload` (orderId, shipmentId, trackingCode, carrier, status, ...)
- `@KafkaListener(topics = "shipment.updated", groupId = "order-service")`
- Map `shipment.status` → `order.status` theo bảng dòng 722–729
- Chỉ nhận transition đúng chiều; event trễ/trùng/lùi → bỏ qua, không lỗi
- `DELIVERED` + COD → gọi `paymentClient.confirmCod(...)`
- `FAILED` / `RETURNED` → ghi history + log cảnh báo (xem mục 2.5)
- `changed_by = SYSTEM`
- Không đụng `saga_state`

> Phụ thuộc: `shipping-service` hiện mới chỉ là boilerplate (chỉ có class Application rỗng).
> Có thể viết listener trước, test bằng cách tự bắn message vào topic.

---

### 2.2. 🟠 Idempotency cho Kafka consumer (CHƯA CÓ)

**Spec:** dòng 790–802 — mỗi event có `eventId`, trước khi xử lý check Redis
`processed:event:{eventId}` (TTL 24h).

**Hiện tại:** cả 3 listener (`PaymentProcessedListener`, `PaymentRefundListener`,
`StockEventListener`) chỉ chống trùng bằng cách so `order.getStatus()` — không đủ (xem
`crash-recovery.md` mục D: message tới lại khi status chưa kịp đổi → xử lý 2 lần).

**Cần làm:** thêm Redis (chưa có trong `pom.xml`!) + helper:
```java
boolean firstTime = redisTemplate.opsForValue()
        .setIfAbsent("processed:event:" + eventId, "1", Duration.ofHours(24));
if (!firstTime) return;   // đã xử lý rồi
```
Hoặc bản không cần Redis: bảng `processed_event(event_id PK, processed_at)`, `INSERT` trước
khi xử lý, dính khóa trùng → skip.

---

### 2.3. 🟠 Saga recovery scheduler (CHƯA CÓ)

**Spec:** dòng 806–824 — job chạy mỗi 2 phút quét `saga_state` `STARTED` quá 15 phút.

Đã mô tả chi tiết cách làm trong [`crash-recovery.md`](./crash-recovery.md) mục 5. Ghi lại
đây để không quên: **order-service chưa có `@EnableScheduling`**.

---

### 2.4. 🟡 `DELIVERED → COMPLETED` tự động sau 7 ngày + endpoint khách tự xác nhận

**Spec:** dòng 440, 457 — "User xác nhận đã nhận, hoặc auto sau 7 ngày".

**Hiện tại:** chỉ `PATCH /admin/orders/{orderId}/status` (admin làm tay) đưa được
`DELIVERED → COMPLETED`. Thiếu:
- `POST /orders/{orderId}/complete` (hoặc tương tự) cho khách tự xác nhận
- Scheduled job auto-complete đơn `DELIVERED` quá 7 ngày

---

### 2.5. 🟡 Không có luồng xử lý giao hàng thất bại / hoàn hàng

`OrderStatus.FAILED` có trong enum nhưng **không chỗ nào dùng**. Spec dòng 728 nói
`shipment.status = FAILED/RETURNED` → "alert admin xử lý tay" nhưng không định nghĩa
order sẽ ở status nào, admin thao tác qua đâu.

**Cần chốt:** khi giao thất bại thì order về đâu (`FAILED`? giữ `SHIPPING` + cờ riêng?),
admin có nút gì để re-ship hoặc chuyển hoàn tiền. `ALLOWED_STATUS_TRANSITIONS` hiện không
có đường nào ra khỏi `SHIPPING` ngoài `DELIVERED`.

---

### 2.6. ⚪ `PaymentRefundListener` — TODO publish notification hoàn tiền

**File:** `PaymentRefundListener.java` dòng 63 — `// TODO: publish notification hoàn tiền`.
Đơn giản: publish một event/notification để Notification Service gửi email "Đã hoàn tiền".

---

### 2.7. ⚪ Snapshot giá lấy từ cart, không phải từ Product Service

**Spec `Project_context.md` dòng 119:** `Order Service → Product Service GET /internal/products/{id}`
để "Snapshot giá lúc đặt".

**Hiện tại:** `createOrder` tin toàn bộ `unitPrice` / `subtotal` từ `CartCheckoutTokenResponse`.
Nếu checkout token của cart-service không phải nguồn giá đáng tin (ký + hạn) thì có rủi ro
sai giá / chỉnh giá. Cần xác nhận: cart checkout token đã "chốt giá" chưa? Nếu rồi thì OK,
ghi rõ; nếu chưa thì phải gọi Product Service verify.

---

## 3. Resilience & vận hành

### 3.1. 🟠 `RestClient` không có timeout

**File:** cả 4 client (`CartClient`, `UserClient`, `InventoryClient`, `PaymentClient`).
`RestClient.builder().baseUrl(...)` — không set connect/read timeout. Một downstream treo
(cổng thanh toán chậm) → thread `createOrder` treo vô hạn → cạn thread pool → cả service đơ.

**Cách sửa:**
```java
ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
        .withConnectTimeout(Duration.ofSeconds(2))
        .withReadTimeout(Duration.ofSeconds(5));
RestClient.builder().baseUrl(url)
        .requestFactory(ClientHttpRequestFactories.get(settings))
        .build();
```

### 3.2. 🟡 Không có retry / circuit breaker

`Project_context.md` liệt kê **Resilience4j** trong tech stack — chưa dùng ở đâu. Một blip
mạng khi `reserve` → khách đặt hàng fail ngay. Nên bọc các REST call idempotent bằng
`@Retry` (2–3 lần, backoff) + `@CircuitBreaker`. **Chỉ retry sau khi đã có idempotency**
(mục 2.2) nếu không sẽ reserve/deduct nhiều lần.

### 3.3. 🟠 Không có Actuator / health check

`pom.xml` không có `spring-boot-starter-actuator`. Docker/K8s không có `/actuator/health`
để biết service sống hay chết. `Project_context.md` yêu cầu Micrometer + Prometheus +
Actuator.

### 3.4. 🟡 Không có distributed tracing

Không có Micrometer Tracing / Zipkin. Một saga đi qua order → inventory → payment → shipping;
khi lỗi sẽ rất khó lần vết nếu không có trace id xuyên suốt REST + Kafka. `Project_context.md`
liệt kê Zipkin.

### 3.5. 🟡 Kafka consumer không có `DefaultErrorHandler` + DLT

Xem [`crash-recovery.md`](./crash-recovery.md) mục 7. Message lỗi hiện retry vô hạn, nghẽn
partition.

### 3.6. ⚪ Producer chưa set `acks=all` / `enable.idempotence`

Xem [`crash-recovery.md`](./crash-recovery.md) mục 4.6.

---

## 4. Config & hạ tầng

### 4.1. 🟠 Thiếu Flyway — vi phạm convention chung

`Project_context.md` dòng 18, 186: mọi service dùng **Flyway** + `ddl-auto: validate`,
migration trong `src/main/resources/db/migration`. `order-service` **không có** thư mục đó,
`application.properties` không set `ddl-auto` (đang chạy default). Schema hiện "mọc" từ
`@Entity` — dễ lệch, không version được.

**Cần làm:** thêm `spring-boot-starter-flyway` (hoặc `flyway-core` + `flyway-database-postgresql`),
viết `V1__init.sql` từ schema trong `orderServiceApiSpec.md` PHẦN 2, set
`spring.jpa.hibernate.ddl-auto=validate`.

### 4.2. 🟡 `application.properties` sơ sài

Thiếu nhiều thứ nên có: `spring.jpa.hibernate.ddl-auto`, `spring.jpa.open-in-view=false`,
datasource pool (`spring.datasource.hikari.*`), Kafka `spring.kafka.listener.ack-mode`,
`spring.kafka.consumer.properties.spring.json.trusted.packages`, `spring.kafka.producer.acks`.
Datasource URL/user/pass đang không thấy ở đâu (chắc trong `.env` — kiểm tra lại đủ chưa).

### 4.3. 🟡 Không tách profile (`dev` / `docker` / `prod`)

Tất cả URL hardcode `localhost`. Khi chạy Docker Compose (service gọi nhau bằng tên
container) sẽ sai hết. Nên có `application-docker.properties` + biến môi trường.

### 4.4. ⚪ `.env` đã được `.gitignore` (OK) nhưng nên có `.env.example`

`.env` không bị commit (đã check) — tốt. Nên thêm `.env.example` liệt kê tên biến (không
giá trị) để người khác biết cần set gì.

---

## 5. Chất lượng code / nợ kỹ thuật

### 5.1. 🟡 `createOrder()` quá dài + làm quá nhiều việc trong 1 `@Transactional`

~195 dòng, gọi 4 REST + Kafka, giữ transaction DB mở suốt thời gian đó (giữ connection +
lock). Vi phạm "hàm < 50 dòng" trong rule của bạn, và là rủi ro cạn connection pool khi tải cao.

**Cách sửa:** tách thành các bước riêng (`buildOrder`, `reserveStock`, `createPayment`,
`confirmCod`), rút ngắn phạm vi `@Transactional` xuống chỉ phần ghi DB. Xem thêm
`crash-recovery.md` mục C + bước 5.

### 5.2. ⚪ `KafkaProducerService` — lặp code 3 method gần y hệt nhau

3 method `publishXxxEvent` chỉ khác cái topic + kiểu payload. Gộp thành 1 method generic
`publish(String topic, String key, Object payload)`. (Sẽ làm luôn khi chuyển sang Outbox.)

### 5.3. ⚪ Biến `needReleaseStock` trong `doCancelOrder` luôn `false`, tên gây hiểu nhầm

`boolean needReleaseStock = false;` không có logic set `true`, chỉ để truyền vào event.
Theo spec thì đúng là "gần như luôn false" — nhưng nên xóa biến, truyền thẳng `false` +
comment giải thích, hoặc bỏ hẳn field khỏi payload nếu Notification không dùng.

### 5.4. ⚪ Trộn `order` và `saveOrder` sau `orderRepo.save()`

Trong `createOrder`, sau `Order saveOrder = orderRepo.save(order)` thì code lúc dùng `order`,
lúc dùng `saveOrder` (cùng 1 object nên không sai, nhưng khó đọc). Chọn 1 tên.

### 5.5. ⚪ `Enum/SagaStatus` có `COMPENSATING` nhưng gần như không dùng

`doCancelOrder` set thẳng `COMPENSATED`, bỏ qua `COMPENSATING`. `PaymentProcessedListener`
nhánh FAILED cũng set thẳng. Nếu không định dùng trạng thái trung gian thì bỏ; nếu có
saga recovery (mục 2.3) thì nên set `COMPENSATING` trước khi chạy compensation, `COMPENSATED`
sau khi xong — để recovery biết cái nào đang dở.

### 5.6. ⚪ Package đặt tên hoa `Controller/Service/Entity/...` và typo `Cosume`

`com.ice.orderservice.Controller` (Java convention là chữ thường: `controller`).
Thư mục `DTO/Event/Cosume` — sai chính tả `Consume`. Không ảnh hưởng chạy, nhưng nếu muốn
theo chuẩn thì rename sớm (càng để lâu càng ngại đổi).

### 5.7. ⚪ `HandlerGlobalException` thiếu handler "catch-all"

Có nhiều `@ExceptionHandler` cụ thể nhưng không có `@ExceptionHandler(Exception.class)` để
bắt lỗi ngoài dự kiến → trả 500 với body nhất quán (`ApiResponse.fail`) + log stacktrace.
Hiện lỗi lạ sẽ rơi về trang lỗi mặc định của Spring.

### 5.8. ⚪ `ErrorCode` enum vs chuỗi hardcode

Chỗ dùng `ErrorCode.X.toString()`, chỗ hardcode `"INVALID_REQUEST"`, `"NOT_FOUND"`. Thống nhất
dùng `ErrorCode`.

---

## 6. Test

### 6.1. 🟠 Gần như không có test

Chỉ có `OrderServiceApplicationTests.contextLoads()`. Rule của bạn (`testing.md`) yêu cầu
**80% coverage** + unit + integration. Cần ít nhất:

- **Unit `OrderService`** (Mockito): tạo đơn COD thành công; tạo đơn online; reserve fail →
  không tạo payment; payment create fail → gọi release + ném lỗi; hủy đơn PENDING → release;
  hủy đơn CONFIRMED (đã PAYMENT_PROCESSED) → refund + return; transition sai → ném
  `InvalidStatusTransitionException`.
- **Unit listener**: `PaymentProcessedListener` SUCCESS/FAILED; nhận message trùng → chỉ xử
  lý 1 lần; `StockEventListener` reason ≠ RESERVATION_EXPIRED → bỏ qua.
- **Integration** (`@SpringBootTest` + Testcontainers Postgres + `spring-kafka-test`
  `@EmbeddedKafka`): flow tạo đơn → publish `order.created`; nhận `payment.processed` →
  order `CONFIRMED` + publish `order.confirmed`.
- **State machine**: bảng tham số hóa mọi cặp `(from, to)` hợp lệ / không hợp lệ.

### 6.2. 🟡 Thiếu dependency test

`pom.xml` chưa có `spring-kafka-test`, `testcontainers` (`postgresql`, `junit-jupiter`),
`mockito` (có sẵn theo `spring-boot-starter-test` nhưng project đang dùng các starter test
tách lẻ — kiểm tra `mockito-core` + `assertj` có được kéo vào không).

---

## 7. Bảng tổng hợp theo thứ tự nên làm

| # | Việc | Mức | Ghi chú |
|---|------|-----|---------|
| 1 | Sửa bug hủy đơn online → mất tiền (1.1) | 🔴 | Ít code, hậu quả nặng |
| 2 | Sửa `changed_by` VARCHAR(20) chứa UUID (1.2) | 🔴 | Sẽ làm `createOrder` chết khi chạy thật |
| 3 | Transactional Outbox (`crash-recovery.md` bước 1) | 🔴 | Nền tảng cho mọi thứ sau |
| 4 | Idempotency consumer (2.2) | 🟠 | Cần trước khi thêm retry / recovery |
| 5 | Saga recovery scheduler (2.3) | 🟠 | Cần `@EnableScheduling` |
| 6 | `DefaultErrorHandler` + DLT (3.5) | 🟠 | |
| 7 | `RestClient` timeout (3.1) | 🟠 | 1 dòng config, tránh treo service |
| 8 | Lỗi downstream bị nuốt (1.4) | 🟠 | Trả item hết hàng về client |
| 9 | `@Version` chống race (1.5) | 🟠 | |
| 10 | `ShipmentUpdatedListener` (2.1) | 🟠 | Mở khoá luồng giao hàng tự động |
| 11 | Flyway + `V1__init.sql` (4.1) | 🟠 | Theo convention ShopNow |
| 12 | Actuator health (3.3) | 🟠 | Cần cho Docker |
| 13 | `order_code` đúng format + chống trùng (1.3) | 🟡 | |
| 14 | `@Transactional(readOnly)` + tắt OSIV + fetch join (1.7) | 🟡 | |
| 15 | Bỏ `@NotBlank` khỏi `note` + sửa message (1.8) | 🟡 | Nhanh |
| 16 | refund trước khi save `REFUNDING` (1.6) | 🟡 | Gộp với refactor Outbox |
| 17 | Tách nhỏ `createOrder()` (5.1) | 🟡 | Sau khi có Outbox |
| 18 | Auto-complete + endpoint khách xác nhận (2.4) | 🟡 | |
| 19 | Luồng giao hàng thất bại / hoàn hàng (2.5) | 🟡 | Cần chốt thiết kế |
| 20 | Retry / circuit breaker Resilience4j (3.2) | 🟡 | Sau idempotency |
| 21 | Tracing / Zipkin (3.4) | 🟡 | |
| 22 | Profile dev/docker/prod (4.3) | 🟡 | |
| 23 | Bộ test `OrderService` + listener + integration (6) | 🟠 | Làm song song từng phần khi sửa ở trên |
| 24 | Dọn code: KafkaProducerService, needReleaseStock, packages, catch-all handler (5.2–5.8) | ⚪ | Làm dần |
| 25 | `.env.example`, notification refund TODO, snapshot giá (2.6, 2.7, 4.4) | ⚪ | |

---

## Gợi ý lộ trình

**Đợt 1 — chặn máu (1–2 ngày):** #1, #2, #7, #8, #15
→ những lỗi sai/hỏng nhỏ nhưng nguy hiểm, sửa nhanh.

**Đợt 2 — nền tảng độ tin cậy (làm theo `crash-recovery.md`):** #3, #4, #5, #6, #9
→ Outbox → Idempotency → Recovery → Error handler → `@Version`.

**Đợt 3 — hoàn thiện nghiệp vụ:** #10, #13, #18, #19 + #23 (test cho phần vừa làm)
→ luồng giao hàng, mã đơn, hoàn tất đơn.

**Đợt 4 — chuẩn production ShopNow:** #11, #12, #20, #21, #22
→ Flyway, Actuator, Resilience4j, tracing, profile.

**Đợt 5 — dọn dẹp:** #17, #24, #25.
