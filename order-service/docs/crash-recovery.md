# Recovery khi service crash — Giải thích & cách làm

> Tài liệu cho `order-service` (ShopNow). Viết theo kiểu học: giải thích khái niệm trước,
> rồi soi vào code hiện tại, rồi đưa code giải pháp cụ thể.
>
> **Bản này chỉ tập trung 3 việc sẽ làm lần này** (B, D, E ở phần 3). Hai việc còn lại
> (A, C) là refactor lớn — ghi lại ở [phần 8](#8-chưa-làm-lần-này) để làm sau.

---

## Mục lục

1. [Crash recovery là gì và tại sao phải quan tâm](#1-crash-recovery-là-gì)
2. [Vấn đề gốc: Dual-write problem](#2-vấn-đề-gốc-dual-write-problem)
3. [Các lỗ hổng — và lần này làm cái nào](#3-các-lỗ-hổng--và-lần-này-làm-cái-nào)
4. [Việc 1 (D) — Idempotency bằng Redis key *(hướng của bạn)*](#4-việc-1-d--idempotency-bằng-redis-key)
5. [Việc 2 (E) — Log & nuốt lỗi trong listener *(hướng của bạn)*](#5-việc-2-e--log--nuốt-lỗi-trong-listener)
6. [Việc 3 (B) — Saga Recovery Scheduler *(hướng của tôi — chỉ việc làm)*](#6-việc-3-b--saga-recovery-scheduler)
7. [Thứ tự triển khai & checklist](#7-thứ-tự-triển-khai--checklist)
8. [Chưa làm lần này (A, C, Outbox, DLT)](#8-chưa-làm-lần-này)
9. [Tóm tắt 1 dòng](#9-tóm-tắt-1-dòng)

---

## 1. Crash recovery là gì

"Crash" = tiến trình Java của service bị **dừng đột ngột** giữa chừng: `kill -9`, hết RAM,
server reboot, deploy lại, mất điện, Docker OOM... Khác với exception (bạn `catch` được),
crash thì **code sau điểm crash không bao giờ chạy**, không có `finally`, không có
rollback thủ công.

Câu hỏi của recovery: **"Nếu service tắt ngay tại dòng này, dữ liệu có bị kẹt ở trạng thái
nửa vời không? Khi bật lại có tự sửa được không?"**

Trong một luồng đặt hàng (Saga), một request đụng vào **nhiều nơi lưu trạng thái**:

- DB của `order-service` (bảng `orders`, `saga_state`, ...)
- DB của `inventory-service` (qua REST call)
- DB của `payment-service` (qua REST call)
- Kafka (event `order.created`, `order.confirmed`, ...)

Crash nguy hiểm vì các nơi này **không commit cùng lúc**. Commit chỗ này xong, crash trước
khi kịp chỗ kia → lệch trạng thái, và không ai biết để đi sửa.

---

## 2. Vấn đề gốc: Dual-write problem

"Dual write" = trong **một thao tác nghiệp vụ**, bạn phải ghi vào **hai hệ thống khác nhau**
mà không có transaction chung.

Ví dụ kinh điển trong `OrderService.createOrder()`:

```java
orderRepo.save(saveOrder);          // (1) ghi vào PostgreSQL
sageStateRepo.save(sagaState);       // (2) ghi vào PostgreSQL  -> (1)(2) cùng 1 transaction, OK
kafkaProducerService.publishOrderCreatedEvent(...);   // (3) ghi vào Kafka  <-- HỆ THỐNG KHÁC
```

`@Transactional` của Spring **chỉ bao được (1) và (2)** (cùng một DB). Kafka nằm ngoài
transaction đó. Nên có các kịch bản hỏng:

| Thứ tự thực tế | Hậu quả |
|---|---|
| (1)(2) commit xong → **crash** → (3) chưa chạy | DB có order + saga, nhưng **event `order.created` không bao giờ được gửi**. Đơn kẹt vĩnh viễn. |
| (3) gửi Kafka xong → **crash** → transaction (1)(2) **rollback** | Event đã bay đi báo "order X created", nhưng trong DB **không có order X**. |
| `kafkaTemplate.send()` là **bất đồng bộ** — code hiện tại không `.get()`, không callback | Broker Kafka kẹt/mạng lỗi → `send()` fail âm thầm, **không ai biết**, không retry. |

> **Cách sửa gốc rễ** là **Transactional Outbox** (biến "gửi Kafka" thành "ghi thêm 1 dòng
> DB" chung transaction). Đây là refactor lớn — **để sau**, xem [phần 8](#8-chưa-làm-lần-này).
> Lần này ta xử lý phần **hậu quả xảy ra khi đã lỡ crash**: đơn kẹt thì có job quét và cứu
> (việc B), message trùng thì xử lý lại không sao (việc D), message lỗi thì không làm nghẽn
> partition (việc E).

---

## 3. Các lỗ hổng — và lần này làm cái nào

| Mã | Lỗ hổng | Mức độ | Lần này? | Hướng đi |
|----|---------|--------|----------|----------|
| A | Mất event do dual-write (`kafkaTemplate.send` bắn-rồi-quên) | 🔴 Cao | ❌ Chưa (cần Outbox) | [phần 8](#8-chưa-làm-lần-này) — tạm thời việc B gánh đỡ |
| **B** | **Không có job phục hồi saga bị kẹt** | 🔴 Cao | ✅ **Làm** | Scheduler quét `saga_state` cũ → đẩy tiếp / bồi hoàn / FAILED *(hướng của tôi)* |
| C | REST call nằm trong `@Transactional` của `createOrder` | 🟠 Vừa | ❌ Chưa (refactor lớn) | [phần 8](#8-chưa-làm-lần-này) |
| **D** | **Message bị xử lý lại gây kẹt (poison message)** | 🟠 Vừa | ✅ **Làm** | Idempotency: lưu `eventId` vào **Redis key**, message trùng thì bỏ qua *(hướng của bạn)* |
| **E** | **`@KafkaListener` không có error handler → retry churn** | 🟠 Vừa | ✅ **Làm** | Trong listener: **log đầy đủ rồi nuốt lỗi**, không ném lại → không retry vô hạn *(hướng của bạn)* |

### Chi tiết 3 lỗ hổng sẽ làm

#### B — Không có job phục hồi saga bị kẹt

**Ở đâu:** `SageStateRepo` chỉ có `findByOrderId()`. Không có query kiểu "tìm saga đang
`STARTED` quá lâu". Không có `@Scheduled` nào (chưa `@EnableScheduling`).

**Kịch bản (thanh toán online):**

```
t0  createOrder(): order.status = PENDING, saga.currentStep = STOCK_RESERVED, saga = STARTED
t1  publish order.created -> Kafka OK
t2  payment-service NHẬN được nhưng CRASH trước khi publish payment.processed
    (hoặc payment.processed bị mất)
```

`order-service` ngồi chờ `payment.processed` **mãi mãi**. Đơn kẹt `PENDING`. Reservation ở
inventory sẽ tự expire sau 15 phút và nhả kho, **nhưng order thì không tự chuyển `CANCELLED`**.

**Cần:** một scheduler quét `saga_state` theo `updatedAt` cũ + `sagaStatus IN (STARTED,
COMPENSATING)`, rồi quyết định *đẩy tiếp* hay *bồi hoàn (compensate)* hay *đánh dấu `FAILED`
để người xử lý tay*. **Bằng chứng người thiết kế đã định làm:** `SagaState` có sẵn field
`retryCount`, `failureReason`.

> Vì lần này **chưa làm Outbox (A)**, scheduler này gánh luôn việc phát hiện đơn kẹt do
> **mất event** `order.created` / `order.confirmed`, không chỉ do payment-service chết.

#### D — Message bị xử lý lại gây kẹt (poison message)

**Ở đâu:** `PaymentProcessedListener.handlePaymentProcessed()` — cả method là `@Transactional`.

Kafka giao **at-least-once**: nếu listener chạy xong phần việc nhưng service crash **trước
khi commit offset**, Kafka sẽ **gửi lại** message đó khi bật lại.

**Kịch bản:**

```
t0  listener nhận payment.processed (SUCCESS)
t1  inventoryClient.deduct(...) -> inventory-service COMMIT: reservation RESERVED -> DEDUCTED
t2  >>> CRASH <<< (trước orderRepo.save)  -> transaction order-service rollback, offset CHƯA commit
t3  service bật lại -> Kafka gửi lại payment.processed
t4  listener chạy lại: order vẫn PENDING -> qua được guard "if (status == CONFIRMED...)"
t5  inventoryClient.deduct(...) lần 2 -> inventory không còn reservation RESERVED
    -> ném ResourceNotFoundException
t6  (mặc định Spring Kafka) retry -> lại fail -> ... -> đơn kẹt PENDING
```

**Hướng xử lý (hướng của bạn):** trước khi xử lý, tra `eventId` trong **Redis**. Đã thấy →
message trùng → bỏ qua. Chưa thấy → xử lý xong mới ghi key. Xem [phần 4](#4-việc-1-d--idempotency-bằng-redis-key).

> Redis key chặn tốt các loại **trùng do rebalance / replay tay / publish trùng**. Riêng
> đúng cái khe crash ở `t2` (inventory đã commit, order-service chưa) thì Redis không cứu
> được một mình — chỗ đó **việc E** (nuốt lỗi, không loop) + **việc B** (scheduler dọn đơn
> kẹt) lo. Ba việc bọc cho nhau.

#### E — `@KafkaListener` không có error handler

Nếu một message lỗi (parse hỏng, bug logic, service phụ thuộc đang chết), Spring Kafka bản
mới **mặc định thử lại ~10 lần sát nhau rồi mới bỏ qua** (tài liệu cũ hay nói "vô hạn" —
không còn đúng). Vẫn có 2 vấn đề:

1. 10 lần retry sát nhau vẫn **làm nghẽn partition** một lúc — message sau phải chờ.
2. Message hỏng cuối cùng **bị bỏ âm thầm**, log mặc định khó thấy.

**Hướng xử lý (hướng của bạn):** trong listener tự `try/catch`, **log ở mức ERROR kèm toàn
bộ nội dung message** rồi **return bình thường (không ném lại)**. Listener return êm →
Spring Kafka commit offset → **bỏ ngay, không retry, không nghẽn**, và log to để mình chủ
động replay tay. Xem [phần 5](#5-việc-2-e--log--nuốt-lỗi-trong-listener).

### Phần ĐÃ ổn (không phải làm gì)

- **Reservation mồ côi** khi crash trước lúc order commit → `inventory-service`
  `SchedulerStockReserve.autoExpireReservations()` chạy mỗi 60s, tự expire sau 15 phút.
- **Guard idempotency cơ bản** trong `PaymentProcessedListener` / `StockEventListener`
  (check `order.getStatus()`) — đúng hướng, việc D sẽ củng cố thêm.

---

## 4. Việc 1 (D) — Idempotency bằng Redis key

### Ý tưởng

"Idempotent" = gọi 1 lần hay 10 lần **kết quả cuối giống nhau**.

Mỗi `KafkaEvent` đã có sẵn field `eventId` (chuỗi UUID, do service gửi sinh ra 1 lần). Ta
dùng nó làm khóa:

```
listener nhận message
   │
   ├─ Redis GET "order-service:processed-event:<eventId>"  ──> có? => message trùng, RETURN
   │
   ├─ xử lý nghiệp vụ (@Transactional) ...
   │
   └─ xử lý XONG XUÔI => Redis SET key, TTL 7 ngày   (đánh dấu "đã xử lý")
```

Đánh dấu **sau khi** transaction nghiệp vụ commit xong, không phải trước — để nếu xử lý
ném lỗi thì key **không** được ghi, message vẫn được coi là "chưa xử lý".

### 4.1. Thêm dependency Redis

`order-service` **chưa có** Redis. Thêm vào `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

`application.properties`:

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

Spring Boot tự tạo bean `StringRedisTemplate` — không cần viết `@Configuration`.

### 4.2. Hằng số dùng chung

Tạo `com/ice/orderservice/Kafka/Idempotency.java`:

```java
package com.ice.orderservice.Kafka;

import java.time.Duration;

public final class Idempotency {
    private Idempotency() {}

    public static final String PROCESSED_KEY_PREFIX = "order-service:processed-event:";
    public static final Duration PROCESSED_TTL = Duration.ofDays(7);

    public static String key(String eventId) {
        return PROCESSED_KEY_PREFIX + eventId;
    }
}
```

> TTL 7 ngày: đủ lâu để chặn mọi lần redeliver/replay hợp lý, mà Redis không phình mãi.

### 4.3. Tách phần nghiệp vụ ra service riêng

`@Transactional` **không có tác dụng khi gọi method cùng class** (self-invocation bỏ qua
proxy). Nên đưa toàn bộ thân xử lý hiện tại của `PaymentProcessedListener` sang một bean
mới, listener chỉ còn lo Kafka + Redis + nuốt lỗi.

`com/ice/orderservice/Service/PaymentProcessedHandler.java`:

```java
package com.ice.orderservice.Service;

import com.ice.orderservice.Client.InventoryClient;
import com.ice.orderservice.DTO.Event.Cosume.PaymentProcessedPayload;
import com.ice.orderservice.DTO.Event.Publish.KafkaEvent;
import com.ice.orderservice.DTO.Event.Publish.OrderCancelledPayload;
import com.ice.orderservice.DTO.Request.Inventory.DeductRequest;
import com.ice.orderservice.DTO.Request.Inventory.ReleaseRequest;
import com.ice.orderservice.Entity.Order;
import com.ice.orderservice.Entity.SagaState;
import com.ice.orderservice.Enum.*;
import com.ice.orderservice.Exception.ResourceNotFoundException;
import com.ice.orderservice.Repository.OrderRepo;
import com.ice.orderservice.Repository.SageStateRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentProcessedHandler {

    private final OrderRepo orderRepo;
    private final SageStateRepo sageStateRepo;
    private final InventoryClient inventoryClient;
    private final KafkaProducerService kafkaProducerService;
    private final OrderEventPayloadFactory payloadFactory;   // xem phần 6.4

    @Transactional
    public void handle(KafkaEvent<PaymentProcessedPayload> event) {
        PaymentProcessedPayload payload = event.getPayload();
        UUID orderId = UUID.fromString(payload.getOrderId());

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy order " + orderId));
        SagaState saga = sageStateRepo.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy saga cho order " + orderId));

        // Guard lớp DB (độc lập với Redis): saga đã đi qua bước này rồi -> bỏ.
        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.CANCELLED
                || saga.getCompletedSteps().contains("STOCK_DEDUCTED")
                || saga.getCompletedSteps().contains("ORDER_CONFIRMED")) {
            log.info("Order {} đã xử lý payment.processed rồi (status={}), bỏ qua",
                    order.getId(), order.getStatus());
            return;
        }

        if (payload.getStatus() == PaymentGatewayStatus.SUCCESS) {
            saga.getCompletedSteps().add("PAYMENT_PROCESSED");
            saga.setCurrentStep(CurrentStep.PAYMENT_PROCESSED);
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setTransactionId(payload.getTransactionId());

            inventoryClient.deduct(new DeductRequest(order.getId().toString()));

            saga.getCompletedSteps().add("STOCK_DEDUCTED");
            saga.getCompletedSteps().add("ORDER_CONFIRMED");
            saga.setCurrentStep(CurrentStep.ORDER_CONFIRMED);
            order.setStatus(OrderStatus.CONFIRMED);
            saga.setSagaStatus(SagaStatus.COMPLETED);

            orderRepo.save(order);
            sageStateRepo.save(saga);

            kafkaProducerService.publishOrderConfirmEvent(payloadFactory.buildConfirmPayload(order));
        } else if (payload.getStatus() == PaymentGatewayStatus.FAILED) {
            saga.setSagaStatus(SagaStatus.COMPENSATED);
            inventoryClient.release(new ReleaseRequest(order.getId().toString(), ReasonRelease.PAYMENT_FAILED));
            order.setStatus(OrderStatus.CANCELLED);

            orderRepo.save(order);
            sageStateRepo.save(saga);

            kafkaProducerService.publishOrderCancelledEvent(
                    payloadFactory.buildCancelledPayload(order, "PAYMENT_FAILED"));
        }
    }
}
```

> Đây gần như là copy nguyên thân method cũ, chỉ khác: dùng `payloadFactory` để dựng
> payload (dùng lại được ở scheduler việc B) và guard mạnh hơn theo `completedSteps`.

### 4.4. Listener mới — mỏng, có Redis + nuốt lỗi

`PaymentProcessedListener.java` viết lại:

```java
package com.ice.orderservice.Listener;

import com.ice.orderservice.DTO.Event.Cosume.PaymentProcessedPayload;
import com.ice.orderservice.DTO.Event.Publish.KafkaEvent;
import com.ice.orderservice.Kafka.Idempotency;
import com.ice.orderservice.Service.PaymentProcessedHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentProcessedListener {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final PaymentProcessedHandler handler;

    @KafkaListener(topics = "payment.processed", groupId = "order-service")
    public void handlePaymentProcessed(String message) {
        try {
            KafkaEvent<PaymentProcessedPayload> event = objectMapper.readValue(
                    message, new TypeReference<KafkaEvent<PaymentProcessedPayload>>() {});

            String dedupKey = Idempotency.key(event.getEventId());
            if (Boolean.TRUE.equals(redisTemplate.hasKey(dedupKey))) {
                log.info("Event {} đã xử lý rồi -> bỏ qua (duplicate)", event.getEventId());
                return;
            }

            handler.handle(event);   // @Transactional ở bean khác

            // chỉ đánh dấu khi CHẮC CHẮN transaction trên đã commit xong
            redisTemplate.opsForValue().set(dedupKey, "1", Idempotency.PROCESSED_TTL);

        } catch (Exception ex) {
            // hướng E: log to, KHÔNG ném lại -> Kafka commit offset, không retry vô hạn
            log.error("[KAFKA-DROP] topic=payment.processed message={} -- bỏ qua message sau lỗi",
                    message, ex);
        }
    }
}
```

### 4.5. Các listener khác

- `StockEventListener` (`stock.released`): đã idempotent sẵn theo `order.getStatus() ==
  CANCELLED`. Chỉ cần thêm phần **nuốt lỗi** (việc E, phần 5). Có thể thêm Redis key theo
  cùng mẫu nếu muốn chắc hơn — không bắt buộc vì nó chỉ đổi status, không gọi service ngoài.
- `PaymentRefundListener`: nếu handler gọi service ngoài / không idempotent → áp **đúng mẫu
  Redis key** như `PaymentProcessedListener`.

---

## 5. Việc 2 (E) — Log & nuốt lỗi trong listener

### Ý tưởng

Mọi `@KafkaListener` bọc thân xử lý trong `try/catch`. Vào `catch`:

1. `log.error(...)` kèm **topic + toàn bộ chuỗi message + stacktrace**.
2. **`return` bình thường** — KHÔNG `throw` lại.

Listener return êm → container commit offset → message coi như "đã tiêu thụ" → **không
retry, không nghẽn partition**.

### Đánh đổi (phải hiểu rõ)

- Message lỗi bị **BỎ HẲN** — không retry, không đẩy sang Dead Letter Topic. Bạn dựa vào:
  - Log `[KAFKA-DROP]` đủ to để thấy và **replay tay** (gửi lại message từ log).
  - **Việc B** (Saga Recovery Scheduler) dọn cái đơn bị kẹt hậu quả.
  - **Việc D** (Redis idempotency) để lần replay tay không xử lý trùng.
- Lỗi **tạm thời** (inventory-service chết 3 giây) cũng bị bỏ luôn. Nếu muốn giảm rủi ro
  này: thêm **thử lại có giới hạn ngay trong method** (2–3 lần, nghỉ ngắn) rồi mới bỏ —
  vẫn hữu hạn, vẫn không nghẽn. Xem `SafeConsumer.runWithRetry` bên dưới (tùy chọn).

### 5.1. Helper dùng chung

`com/ice/orderservice/Kafka/SafeConsumer.java`:

```java
package com.ice.orderservice.Kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SafeConsumer {

    /**
     * Chạy {@code body}. Nếu ném lỗi -> log ERROR đầy đủ rồi NUỐT (không ném lại)
     * => Spring Kafka commit offset, không retry vô hạn, không nghẽn partition.
     * Đánh đổi: message lỗi bị BỎ. Dựa vào log + SagaRecoveryScheduler để cứu.
     */
    public void run(String topic, String rawMessage, Runnable body) {
        try {
            body.run();
        } catch (Exception ex) {
            log.error("[KAFKA-DROP] topic={} message={} -- bỏ qua sau lỗi", topic, rawMessage, ex);
        }
    }

    /** (Tùy chọn) thử lại tối đa {@code maxAttempts} lần, nghỉ {@code sleepMs} giữa các lần. */
    public void runWithRetry(String topic, String rawMessage, int maxAttempts, long sleepMs, Runnable body) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                body.run();
                return;
            } catch (Exception ex) {
                if (attempt == maxAttempts) {
                    log.error("[KAFKA-DROP] topic={} message={} -- bỏ sau {} lần thử",
                            topic, rawMessage, maxAttempts, ex);
                    return;
                }
                log.warn("topic={} lỗi lần {}/{}, thử lại", topic, attempt, maxAttempts, ex);
                try { Thread.sleep(sleepMs); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
```

### 5.2. Áp vào `StockEventListener`

```java
@KafkaListener(topics = "stock.released", groupId = "order-service")
public void handleStockRelease(String message) {
    safeConsumer.run("stock.released", message, () -> stockReleaseHandler.handle(message));
}
```

Trong đó `stockReleaseHandler.handle(...)` là bean `@Service` chứa nguyên thân
`@Transactional` hiện tại của `StockEventListener` (tách ra vì lý do proxy như phần 4.3).

> `PaymentProcessedListener` ở phần 4.4 đã tự `try/catch` nuốt lỗi rồi nên không cần bọc
> thêm `SafeConsumer`. Dùng cái nào cũng được, miễn **không để exception thoát ra khỏi
> method listener**.

---

## 6. Việc 3 (B) — Saga Recovery Scheduler

> Đây là phần **theo hướng của tôi, bạn chỉ việc làm theo**. Code bên dưới khớp với entity
> / enum hiện có (`SagaState`, `SagaStatus`, `CurrentStep`, `OrderStatus`).

### Ý tưởng

Một vòng lặp nền, mỗi phút 1 lần:

1. Tìm các `saga_state` có `sagaStatus IN (STARTED, COMPENSATING)` và `updatedAt` cũ hơn
   `STUCK_MINUTES` phút → coi là "kẹt".
2. Với mỗi saga kẹt, nhìn `currentStep` để chọn hành động:
   - Kẹt ở bước **chưa có gì không thể hoàn tác** (`ORDER_CREATED`, `STOCK_RESERVED`) →
     **compensate**: huỷ đơn + publish `order.cancelled` (inventory nhả kho).
   - Kẹt ở `PAYMENT_PROCESSED` → nghi **đã trừ tiền** → **KHÔNG tự huỷ**, set `FAILED` +
     log `ERROR` để người đối soát.
   - Đã tới `STOCK_DEDUCTED` / `ORDER_CONFIRMED` mà saga chưa đóng → nghiệp vụ xong rồi,
     chỉ là event/status chưa chốt → **re-publish `order.confirmed`** + đóng saga `COMPLETED`.
   - `sagaStatus == COMPENSATING` (đang bồi hoàn dở) → chạy lại compensate.
3. Mỗi lần đụng vào một saga thì `retryCount++`. Quá `MAX_RETRY` → `FAILED` + log `ERROR`,
   **thôi không tự sửa nữa**.

> **Nguyên tắc:** chưa mất mát gì không hoàn tác được → compensate (an toàn nhất). Đã có
> bước không hoàn tác (trừ tiền, trừ kho) → đẩy tiếp cho xong, không rollback. Bí quá →
> `FAILED` cho người vào.

### 6.1. Bật scheduling

`OrderServiceApplication.java`:

```java
package com.ice.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // <-- thêm
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

### 6.2. Query tìm saga kẹt

Thêm vào `SageStateRepo.java`:

```java
package com.ice.orderservice.Repository;

import com.ice.orderservice.Entity.SagaState;
import com.ice.orderservice.Enum.SagaStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SageStateRepo extends JpaRepository<SagaState, UUID> {

    Optional<SagaState> findByOrderId(UUID orderId);

    @Query("""
           SELECT s FROM SagaState s
           WHERE s.sagaStatus IN :statuses
             AND s.updatedAt < :threshold
           ORDER BY s.updatedAt ASC
           """)
    List<SagaState> findStuck(@Param("statuses") List<SagaStatus> statuses,
                              @Param("threshold") LocalDateTime threshold,
                              Pageable pageable);
}
```

### 6.3. Scheduler

`com/ice/orderservice/Scheduler/SagaRecoveryScheduler.java`:

```java
package com.ice.orderservice.Scheduler;

import com.ice.orderservice.Entity.Order;
import com.ice.orderservice.Entity.SagaState;
import com.ice.orderservice.Enum.OrderStatus;
import com.ice.orderservice.Enum.SagaStatus;
import com.ice.orderservice.Repository.SageStateRepo;
import com.ice.orderservice.Service.KafkaProducerService;
import com.ice.orderservice.Service.OrderEventPayloadFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class SagaRecoveryScheduler {

    private static final int STUCK_MINUTES = 10;   // saga không nhúc nhích 10 phút = nghi kẹt
    private static final int MAX_RETRY = 5;
    private static final int BATCH_SIZE = 50;

    private final SageStateRepo sageStateRepo;
    private final KafkaProducerService kafkaProducerService;
    private final OrderEventPayloadFactory payloadFactory;

    @Scheduled(fixedDelay = 60_000)   // 1 phút/lần
    @Transactional
    public void recoverStuckSagas() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_MINUTES);
        List<SagaState> stuckList = sageStateRepo.findStuck(
                List.of(SagaStatus.STARTED, SagaStatus.COMPENSATING),
                threshold,
                PageRequest.of(0, BATCH_SIZE));

        for (SagaState saga : stuckList) {
            try {
                recoverOne(saga);
            } catch (Exception ex) {
                log.error("Recovery lỗi cho saga {} (order {})",
                        saga.getId(), saga.getOrder().getId(), ex);
            }
        }
    }

    private void recoverOne(SagaState saga) {
        Order order = saga.getOrder();

        if (saga.getRetryCount() >= MAX_RETRY) {
            saga.setSagaStatus(SagaStatus.FAILED);
            saga.setFailureReason("Recovery quá " + MAX_RETRY + " lần vẫn kẹt ở "
                    + saga.getCurrentStep() + " / " + saga.getSagaStatus());
            log.error("[SAGA-FAILED] order={} step={} -- cần xử lý tay",
                    order.getId(), saga.getCurrentStep());
            sageStateRepo.save(saga);
            return;
        }
        saga.setRetryCount(saga.getRetryCount() + 1);

        // Đang bồi hoàn dở -> chạy lại compensate, bất kể currentStep.
        if (saga.getSagaStatus() == SagaStatus.COMPENSATING) {
            compensate(order, saga, "RESUME_COMPENSATION");
            sageStateRepo.save(saga);
            return;
        }

        switch (saga.getCurrentStep()) {
            case ORDER_CREATED, STOCK_RESERVED ->
                    compensate(order, saga, "SAGA_TIMEOUT_" + saga.getCurrentStep());

            case PAYMENT_PROCESSED -> {
                // Tiền có thể đã bị trừ mà saga chưa hoàn tất -> KHÔNG tự huỷ, báo người.
                saga.setSagaStatus(SagaStatus.FAILED);
                saga.setFailureReason("Kẹt ở PAYMENT_PROCESSED - nghi đã trừ tiền, cần đối soát payment-service");
                log.error("[SAGA-FAILED] order={} kẹt sau khi payment xử lý -- cần đối soát tiền", order.getId());
            }

            case STOCK_DEDUCTED, ORDER_CONFIRMED -> {
                // Nghiệp vụ đã xong, chỉ event/status chưa chốt -> re-publish + đóng saga.
                if (order.getStatus() != OrderStatus.CONFIRMED) {
                    order.setStatus(OrderStatus.CONFIRMED);
                }
                kafkaProducerService.publishOrderConfirmEvent(payloadFactory.buildConfirmPayload(order));
                saga.setSagaStatus(SagaStatus.COMPLETED);
                log.warn("Recovery: order {} re-publish order.confirmed, đóng saga COMPLETED", order.getId());
            }
        }
        sageStateRepo.save(saga);
    }

    private void compensate(Order order, SagaState saga, String reason) {
        order.setStatus(OrderStatus.CANCELLED);
        saga.setSagaStatus(SagaStatus.COMPENSATED);
        kafkaProducerService.publishOrderCancelledEvent(
                payloadFactory.buildCancelledPayload(order, reason));
        log.warn("Recovery: order {} kẹt ở {} -> huỷ đơn + publish order.cancelled (reason={})",
                order.getId(), saga.getCurrentStep(), reason);
    }
}
```

### 6.4. Helper dựng payload (dùng chung listener + scheduler)

`com/ice/orderservice/Service/OrderEventPayloadFactory.java` — tách logic dựng payload đang
lặp trong `PaymentProcessedListener` / `StockEventListener` / `OrderService`:

```java
package com.ice.orderservice.Service;

import com.ice.orderservice.DTO.Event.Publish.OrderCancelledPayload;
import com.ice.orderservice.DTO.Event.Publish.OrderConfirmPayload;
import com.ice.orderservice.DTO.Event.Publish.OrderItemEvent;
import com.ice.orderservice.DTO.Event.Publish.ShippingAddressEvent;
import com.ice.orderservice.Entity.Order;
import com.ice.orderservice.Entity.OrderShippingAddress;
import com.ice.orderservice.Exception.ResourceNotFoundException;
import com.ice.orderservice.Repository.OrderShippingAddressRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderEventPayloadFactory {

    private final OrderShippingAddressRepo orderShippingAddressRepo;

    public OrderConfirmPayload buildConfirmPayload(Order order) {
        OrderShippingAddress addr = orderShippingAddressRepo.findByOrderId(order.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy địa chỉ giao hàng cho order " + order.getId()));
        return new OrderConfirmPayload(
                order.getId().toString(),
                order.getOrderCode(),
                order.getUserId().toString(),
                new ShippingAddressEvent(
                        addr.getFullName(), addr.getPhone(), addr.getProvince(),
                        addr.getDistrict(), addr.getWard(), addr.getStreetDetail()),
                order.getOrderItems().stream()
                        .map(i -> new OrderItemEvent(i.getVariantId().toString(), i.getQty()))
                        .toList());
    }

    public OrderCancelledPayload buildCancelledPayload(Order order, String reason) {
        // VERIFY: tham số boolean thứ 3 của OrderCancelledPayload nghĩa là gì (refund?),
        // code hiện tại luôn truyền false -> giữ nguyên.
        return new OrderCancelledPayload(
                order.getId().toString(),
                reason,
                false,
                order.getOrderItems().stream()
                        .map(i -> new OrderItemEvent(i.getVariantId().toString(), i.getQty()))
                        .toList());
    }
}
```

### 6.5. Phụ thuộc cần biết

- Scheduler **re-publish** `order.cancelled` / `order.confirmed`. Consumer bên
  `inventory-service` / `shipping-service` phải **idempotent theo `orderId`** (gọi lại
  `release` / tạo lại đơn giao thì trả OK thay vì lỗi). Việc đó nằm ở service kia —
  không thuộc phạm vi order-service lần này, nhưng phải nhớ.
- `@Scheduled` + `@Transactional` + query lại từ đầu mỗi lần chạy ⇒ tự an toàn với crash.
- Đang chạy **1 instance** order-service. Nếu sau này ≥ 2 instance, `findStuck` cần
  `SELECT ... FOR UPDATE SKIP LOCKED` để 2 instance không cùng cứu một saga.

---

## 7. Thứ tự triển khai & checklist

Làm từ trên xuống, mỗi bước test xong mới sang bước sau.

### Bước 1 — Idempotency bằng Redis (việc D) — ✅ ĐÃ LÀM

Cách đã làm (hơi khác kế hoạch ban đầu, vẫn ổn):
- [x] Thêm `spring-boot-starter-data-redis` + `spring.data.redis.*`
- [x] `Service/IdempotencyService.java` — `isProcessed(eventId)` / `markProcessed(eventId)`
      (key `processed:event:<eventId>`, TTL 24h)
- [x] Mỗi listener: `if (idempotencyService.isProcessed(eventId)) return;` đầu handler,
      `idempotencyService.markProcessed(eventId)` ở cuối
- [ ] **Test:** gửi lại tay 1 message cũ (cùng `eventId`) → bỏ qua, không xử lý lần 2;
      `eventId` mới → xử lý bình thường
- ⚠️ Còn nợ: `markProcessed` đang chạy **trong** transaction, trước khi commit. Nếu commit
      fail sau đó thì Redis đã đánh dấu nhưng DB chưa lưu. Với việc E (nuốt lỗi) thì message
      bị bỏ luôn → đơn kẹt, chờ **việc B** dọn. Chấp nhận ở scope học; muốn chặt thì chuyển
      `markProcessed` ra listener, gọi **sau khi** `handler.handle(...)` return.

### Bước 2 — Log & nuốt lỗi (việc E) — ✅ ĐÃ LÀM

- [x] `Kafka/SafeConsumer.java` — `run(topic, message, Runnable)`: chạy body, lỗi thì
      `log.error("[KAFKA-DROP] ...")` rồi **nuốt** (không ném lại)
- [x] Tách thân `@Transactional` của cả 3 listener ra bean `@Service` riêng:
      `PaymentProcessedHandler`, `StockReleaseHandler`, `PaymentRefundHandler`
      *(bắt buộc: nếu try/catch nằm trong chính method `@Transactional` thì nuốt lỗi =
      Spring commit dữ liệu ghi dở thay vì rollback)*
- [x] 3 listener giờ chỉ còn 1 dòng: `safeConsumer.run("<topic>", message, () -> handler.handle(message))`
- [x] `./mvnw compile` — BUILD SUCCESS
- [ ] **Test:** đẩy 1 message JSON rác vào `stock.released` → log `[KAFKA-DROP]`, listener
      **vẫn xử lý được message hợp lệ tiếp theo** (partition không nghẽn); đẩy message hợp lệ
      nhưng ép `inventory` lỗi → thấy `[KAFKA-DROP]`, DB order **không** đổi (đã rollback)

### Bước 3 — Saga Recovery Scheduler (việc B)
- [ ] `@EnableScheduling` trên `OrderServiceApplication`
- [ ] `SageStateRepo.findStuck(...)`
- [ ] `OrderEventPayloadFactory` + sửa listener dùng lại factory (bỏ code lặp)
- [ ] `SagaRecoveryScheduler`
- [ ] **Test:** tạo đơn online, cố tình không cho payment-service phản hồi → sau
      `STUCK_MINUTES` phút scheduler huỷ đơn + publish `order.cancelled`; ép lỗi liên tục
      → sau `MAX_RETRY` lần thì saga = `FAILED` + log `[SAGA-FAILED]`

---

## 8. Chưa làm lần này

Ghi lại để làm sau, **không thuộc phạm vi đợt này**:

### A — Mất event do dual-write → **Transactional Outbox**
Cách sửa gốc rễ của [phần 2](#2-vấn-đề-gốc-dual-write-problem): trong `createOrder()` /
listener, thay `kafkaProducerService.publishXxx(...)` bằng "ghi 1 dòng `outbox_event`"
(cùng transaction với `orders` / `saga_state`); một `@Scheduled` đọc dòng `PENDING` → gửi
Kafka (`.get()` chờ broker xác nhận) → đánh dấu `SENT`. Kèm `spring.kafka.producer.acks=all`
+ `enable.idempotence=true`.
→ Tạm thời **việc B** gánh đỡ: đơn kẹt do mất event sẽ bị scheduler phát hiện và cứu, chỉ
là chậm (tối đa `STUCK_MINUTES` phút) thay vì tức thì.

### C — REST call trong `@Transactional` của `createOrder`
`inventoryClient.reserve()` / `paymentClient.createPayment()` gây side effect ở service
khác **ngay**, trong khi transaction DB của `order-service` chưa commit. Nếu commit fail →
kho đã reserve + payment đã tạo nhưng **order không tồn tại**.
→ Chuẩn: **đưa REST call ra khỏi `@Transactional`** — commit order `PENDING` trước, rồi gọi
inventory/payment ở bước sau (đúng tinh thần Saga từng bước). Refactor lớn, làm sau khi đã
có Outbox + Saga recovery. Trước mắt: thêm job đối soát payment "mồ côi".

### Dead Letter Topic (DLT) cho Kafka
Thay vì "nuốt lỗi" (việc E), cách bài bản hơn là `DefaultErrorHandler` +
`DeadLetterPublishingRecoverer`: retry N lần rồi đẩy message hỏng sang `<topic>.DLT` và đi
tiếp. Ưu điểm: **không mất message**. Nhược: phải dựng + theo dõi các topic `.DLT`. Cân
nhắc nâng cấp việc E lên DLT khi có thời gian.

### Những thứ vẫn được phép bỏ qua ở scope học
- **Kafka exactly-once / transactional producer**: Outbox + idempotency đã đủ.
- **Distributed transaction / 2PC**: không ai làm với microservice nữa.
- **Chạy nhiều instance `order-service`**: khi nào ≥ 2 instance thì mới cần
  `FOR UPDATE SKIP LOCKED` trong scheduler.

---

## 9. Tóm tắt 1 dòng

> Nguồn gốc rắc rối là **ghi DB và gửi Kafka không cùng transaction**. Đợt này chưa sửa gốc
> (Outbox — [phần 8](#8-chưa-làm-lần-này)) mà xử lý hậu quả sau crash: **Redis idempotency**
> (xử lý trùng không sao — [D](#4-việc-1-d--idempotency-bằng-redis-key)), **log & nuốt lỗi**
> (message hỏng không nghẽn partition — [E](#5-việc-2-e--log--nuốt-lỗi-trong-listener)),
> **Saga Recovery Scheduler** (quét đơn kẹt, tự huỷ / đẩy tiếp / báo người —
> [B](#6-việc-3-b--saga-recovery-scheduler)). Ba cái bọc cho nhau.
