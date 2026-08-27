# Recovery khi service crash — Giải thích & cách làm

> Tài liệu cho `order-service` (ShopNow). Viết theo kiểu học: giải thích khái niệm trước,
> rồi soi vào code hiện tại, rồi đưa code giải pháp cụ thể.

---

## Mục lục

1. [Crash recovery là gì và tại sao phải quan tâm](#1-crash-recovery-là-gì)
2. [Vấn đề gốc: Dual-write problem](#2-vấn-đề-gốc-dual-write-problem)
3. [Soi từng lỗ hổng trong code hiện tại](#3-soi-từng-lỗ-hổng-trong-code-hiện-tại)
4. [Giải pháp 1 — Transactional Outbox (quan trọng nhất)](#4-giải-pháp-1--transactional-outbox)
5. [Giải pháp 2 — Saga Recovery Scheduler](#5-giải-pháp-2--saga-recovery-scheduler)
6. [Giải pháp 3 — Idempotency (chống xử lý trùng)](#6-giải-pháp-3--idempotency)
7. [Giải pháp 4 — Kafka error handler + Dead Letter Topic](#7-giải-pháp-4--kafka-error-handler--dead-letter-topic)
8. [Thứ tự triển khai & checklist](#8-thứ-tự-triển-khai--checklist)
9. [Những thứ được phép bỏ qua ở scope học](#9-những-thứ-được-phép-bỏ-qua)

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
| (1)(2) commit xong → **crash** → (3) chưa chạy | DB có order + saga, nhưng **event `order.created` không bao giờ được gửi**. `payment-service` / `shipping-service` không biết đơn tồn tại. Đơn kẹt vĩnh viễn. |
| (3) gửi Kafka xong → **crash** → transaction (1)(2) **rollback** | Event đã bay đi báo "order X created", nhưng trong DB **không có order X**. Các service khác xử lý một đơn ma. |
| `kafkaTemplate.send()` là **bất đồng bộ** — code hiện tại không `.get()`, không callback | Broker Kafka đang kẹt/mạng lỗi → `send()` fail âm thầm, **không ai biết**, không retry. Mất event ngay cả khi service không crash. |

> **Điểm mấu chốt:** không có cách nào làm cho "ghi DB" và "gửi Kafka" thành nguyên tử
> (atomic) một cách trực tiếp. Giải pháp chuẩn là **Transactional Outbox**: biến "gửi Kafka"
> thành "ghi thêm một dòng vào DB" (nằm chung transaction với dữ liệu nghiệp vụ), rồi có một
> tiến trình nền đọc dòng đó và gửi Kafka sau. Xem [phần 4](#4-giải-pháp-1--transactional-outbox).

---

## 3. Soi từng lỗ hổng trong code hiện tại

### Lỗ hổng A — Mất event do dual-write *(nghiêm trọng nhất)*

**Ở đâu:** `KafkaProducerService` dùng `kafkaTemplate.send(...)` kiểu "bắn rồi quên".
Gọi từ `OrderService.createOrder()`, `PaymentProcessedListener`, `cancelledOrder()`.

**Kịch bản crash (COD):**

```
t0  createOrder(): orderRepo.save + sageStateRepo.save  -> COMMIT thành công
                    order.status = CONFIRMED, saga = COMPLETED trong DB
t1  >>> CRASH <<<  (trước dòng kafkaProducerService.publishOrderCreatedEvent)
t2  service bật lại
```

Kết quả: khách hàng mở app thấy đơn **CONFIRMED**, nhưng `shipping-service` chưa từng nhận
`order.confirmed` → **không có đơn giao hàng nào được tạo**. Không có scheduler, không có
retry nào phát hiện chuyện này.

**Bằng chứng trong code:** `SagaState` đã có sẵn field `retryCount`, `failureReason` —
rõ ràng người thiết kế định làm recovery nhưng chưa viết.

---

### Lỗ hổng B — Không có job phục hồi saga bị kẹt

**Ở đâu:** `SageStateRepo` chỉ có `findByOrderId()`. Không có query kiểu "tìm saga đang
STARTED quá lâu". Không có `@Scheduled` nào trong `order-service` (chưa có `@EnableScheduling`).

**Kịch bản (thanh toán online):**

```
t0  createOrder(): order.status = PENDING, saga.currentStep = STOCK_RESERVED
t1  publish order.created -> Kafka OK
t2  payment-service NHẬN được, nhưng CRASH trước khi publish payment.processed
    (hoặc payment.processed bị mất)
```

`order-service` ngồi chờ `payment.processed` **mãi mãi**. Đơn kẹt `PENDING`, kho vẫn giữ
`reservedQty` (reservation ở inventory sẽ tự expire sau 15 phút và nhả kho — nhưng **order
thì không tự chuyển sang CANCELLED**, vì `stock.released` do expiry có `StockEventListener`
xử lý hay không thì cần kiểm tra riêng).

**Cần:** một scheduler quét `saga_state` theo `updatedAt` cũ + `sagaStatus = STARTED`, rồi
quyết định *đẩy tiếp* hay *bồi hoàn (compensate)* hay *đánh dấu FAILED để người xử lý tay*.

---

### Lỗ hổng C — REST call nằm trong `@Transactional` của `createOrder`

**Ở đâu:** `OrderService.createOrder()` là `@Transactional`. Bên trong nó gọi:

```java
inventoryClient.reserve(...)        // REST -> đổi DB inventory-service NGAY
paymentClient.createPayment(...)    // REST -> đổi DB payment-service NGAY
inventoryClient.deduct(...)         // REST (nhánh COD)
```

Những call này gây **side effect ở service khác ngay lập tức**, nhưng transaction DB của
`order-service` thì **chưa commit** (commit ở cuối method).

**Kịch bản:** `inventoryClient.reserve()` OK, `paymentClient.createPayment()` OK, rồi tới
`orderRepo.save(saveOrder)` / commit **thất bại** (DB rớt kết nối, constraint, deadlock...).

- Nhánh `catch` chỉ chạy khi **REST call ném exception** — commit fail thì không rơi vào
  các `catch` đó.
- Kết quả: payment đã tạo + kho đã reserve, nhưng **order không tồn tại**. Reservation thì
  15 phút nữa scheduler của inventory nhả; còn **payment thì không có gì thu dọn**.

**Hướng xử lý (theo thứ tự dễ → chuẩn):**

1. Ngắn hạn: chấp nhận, vì `createPayment` khi chưa có order thật thì cũng ít hại — nhưng
   nên có job đối soát payment "mồ côi".
2. Chuẩn: **đưa các REST call ra khỏi `@Transactional`** — commit order (PENDING) trước,
   rồi mới gọi inventory/payment ở bước sau (đúng tinh thần Saga từng bước). Đây là refactor
   lớn, làm sau khi đã có Outbox + Saga recovery.

---

### Lỗ hổng D — Message bị xử lý lại gây kẹt (poison message)

**Ở đâu:** `PaymentProcessedListener.handlePaymentProcessed()`.

Kafka giao hàng theo kiểu **at-least-once**: nếu listener chạy xong phần việc nhưng service
crash **trước khi commit offset**, Kafka sẽ **gửi lại** message đó khi bật lại.

**Kịch bản:**

```
t0  listener nhận payment.processed (SUCCESS)
t1  inventoryClient.deduct(...) -> inventory-service COMMIT: reservation RESERVED -> DEDUCTED
t2  >>> CRASH <<< (trước orderRepo.save)  -> transaction order-service rollback, offset CHƯA commit
t3  service bật lại -> Kafka gửi lại payment.processed
t4  listener chạy lại: order vẫn PENDING -> qua được guard `if (status == CONFIRMED...)`
t5  inventoryClient.deduct(...) lần 2 -> inventory tìm reservation RESERVED -> KHÔNG còn
    -> ném ResourceNotFoundException("no reserved stock found")
t6  listener fail -> Kafka lại gửi lại -> lặp vô hạn -> đơn kẹt PENDING
```

**Hai chỗ phải sửa:**

- **Guard của listener** nên dựa trên `sagaState.getCompletedSteps()` (`"STOCK_DEDUCTED"`),
  không chỉ dựa `order.getStatus()`.
- **Inventory `deduct/release/reserve` phải idempotent theo `orderId`**: gọi lần 2 thì trả
  về "OK, đã làm rồi" thay vì ném lỗi. Xem [phần 6](#6-giải-pháp-3--idempotency).

---

### Lỗ hổng E — `@KafkaListener` không có error handler / DLT

Nếu một message lỗi (parse hỏng, bug logic), Spring Kafka mặc định sẽ **retry ngay, vô hạn**,
làm **nghẽn cả partition** — các message sau không được xử lý. Cần `DefaultErrorHandler` +
**Dead Letter Topic** (`payment.processed.DLT`) để sau N lần fail thì đẩy message hỏng sang
chỗ khác và đi tiếp. Xem [phần 7](#7-giải-pháp-4--kafka-error-handler--dead-letter-topic).

---

### Phần ĐÃ ổn (không phải làm gì)

- **Reservation mồ côi** khi crash trước lúc order commit → `inventory-service`
  `SchedulerStockReserve.autoExpireReservations()` chạy mỗi 60s, tự expire sau 15 phút.
- **Guard idempotency cơ bản** trong `PaymentProcessedListener` (check status) — đúng hướng,
  chỉ cần củng cố như mục D.

---

## 4. Giải pháp 1 — Transactional Outbox

### Ý tưởng

Thay vì `order-service` **tự gửi Kafka** trong lúc xử lý (dual write), ta:

1. Khi xử lý nghiệp vụ: **ghi event vào bảng `outbox_event` trong CÙNG transaction** với
   `orders` / `saga_state`. → Atomic: hoặc cả order + event cùng lưu, hoặc không gì cả.
2. Một **tiến trình nền** (`@Scheduled`) đọc các dòng `outbox_event` chưa gửi, **gửi lên
   Kafka**, gửi xong thì đánh dấu `SENT`.
3. Nếu crash giữa chừng: bật lại, scheduler thấy dòng vẫn `PENDING` → gửi lại. **Không mất
   event.** (Đổi lại: có thể gửi **trùng** → consumer phải idempotent, xem phần 6.)

```
createOrder()  ──┐
                 │  1 transaction duy nhất
   orders ───────┤
   saga_state ───┤
   outbox_event ─┘   (status = PENDING)
                          │
                          │  OutboxRelayScheduler (mỗi 2s)
                          ▼
                     Kafka topic  ──> payment-service / shipping-service ...
                          │
                          ▼
                   outbox_event.status = SENT
```

### 4.1. Bảng DB

`order-service` hiện không dùng Flyway. Nếu `spring.jpa.hibernate.ddl-auto=update` thì entity
bên dưới đủ để Hibernate tự tạo bảng. Nếu bạn chạy SQL tay:

```sql
CREATE TABLE outbox_event (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(50)  NOT NULL,   -- 'ORDER'
    aggregate_id    VARCHAR(64)  NOT NULL,   -- orderId, dùng làm Kafka key
    topic           VARCHAR(100) NOT NULL,   -- 'order.created'
    event_type      VARCHAR(100) NOT NULL,   -- 'order.created'
    payload         JSONB        NOT NULL,   -- toàn bộ KafkaEvent<...> đã serialize
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | SENT | FAILED
    retry_count     INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP    NOT NULL DEFAULT now(),
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    sent_at         TIMESTAMP
);

CREATE INDEX idx_outbox_pending
    ON outbox_event (status, next_attempt_at)
    WHERE status = 'PENDING';
```

### 4.2. Entity

```java
package com.ice.orderservice.Entity;

import com.ice.orderservice.Enum.OutboxStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_type", length = 50, nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 64, nullable = false)
    private String aggregateId;

    @Column(name = "topic", length = 100, nullable = false)
    private String topic;

    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private String payload;   // JSON string của KafkaEvent<...>

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (nextAttemptAt == null) nextAttemptAt = now;
    }
}
```

```java
package com.ice.orderservice.Enum;

public enum OutboxStatus { PENDING, SENT, FAILED }
```

### 4.3. Repository

```java
package com.ice.orderservice.Repository;

import com.ice.orderservice.Entity.OutboxEvent;
import com.ice.orderservice.Enum.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepo extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
           SELECT o FROM OutboxEvent o
           WHERE o.status = :status
             AND o.nextAttemptAt <= :now
           ORDER BY o.createdAt ASC
           """)
    List<OutboxEvent> findBatchToSend(@Param("status") OutboxStatus status,
                                      @Param("now") LocalDateTime now,
                                      Pageable pageable);
}
```

### 4.4. Service ghi outbox (thay cho `KafkaProducerService.send`)

```java
package com.ice.orderservice.Service;

import com.ice.orderservice.DTO.Event.Publish.KafkaEvent;
import com.ice.orderservice.Entity.OutboxEvent;
import com.ice.orderservice.Repository.OutboxEventRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepo outboxEventRepo;
    private final ObjectMapper objectMapper;

    /**
     * Gọi hàm này BÊN TRONG @Transactional của nghiệp vụ (createOrder, listener...).
     * Nó chỉ INSERT 1 dòng DB — cùng transaction với orders/saga_state.
     */
    public void publish(String topic, String aggregateId, Object payload) {
        KafkaEvent<Object> event = new KafkaEvent<>(
                UUID.randomUUID().toString(),
                topic,
                Instant.now().toString(),
                "1.0",
                payload
        );

        OutboxEvent row = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId(aggregateId)
                .topic(topic)
                .eventType(topic)
                .payload(objectMapper.writeValueAsString(event))
                .build();

        outboxEventRepo.save(row);
    }
}
```

Sau đó trong `OrderService.createOrder()` **đổi**:

```java
// CŨ:
kafkaProducerService.publishOrderCreatedEvent(new OrderCreatedPayload(...));
// MỚI:
outboxService.publish("order.created", saveOrder.getId().toString(), new OrderCreatedPayload(...));
```

Làm tương tự cho `order.confirmed`, `order.cancelled` trong `OrderService` và
`PaymentProcessedListener`. Vì `outboxService.publish()` chỉ `save()` một entity, nó tự động
nằm trong transaction đang mở của các method `@Transactional` đó.

### 4.5. Tiến trình nền gửi Kafka

```java
package com.ice.orderservice.Scheduler;

import com.ice.orderservice.Entity.OutboxEvent;
import com.ice.orderservice.Enum.OutboxStatus;
import com.ice.orderservice.Repository.OutboxEventRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY = 10;

    private final OutboxEventRepo outboxEventRepo;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 2000)   // 2 giây/lần
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = outboxEventRepo.findBatchToSend(
                OutboxStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));

        for (OutboxEvent e : batch) {
            try {
                // .get() -> chờ broker xác nhận đã nhận (đồng bộ, chắc chắn)
                kafkaTemplate.send(e.getTopic(), e.getAggregateId(), e.getPayload())
                             .get();

                e.setStatus(OutboxStatus.SENT);
                e.setSentAt(LocalDateTime.now());

            } catch (Exception ex) {
                int n = e.getRetryCount() + 1;
                e.setRetryCount(n);
                // backoff tăng dần: 2^n giây (tối đa ~ vài phút)
                e.setNextAttemptAt(LocalDateTime.now().plusSeconds((long) Math.min(300, Math.pow(2, n))));
                if (n >= MAX_RETRY) {
                    e.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox event {} FAILED sau {} lần thử — cần xử lý tay", e.getId(), n, ex);
                } else {
                    log.warn("Gửi outbox event {} lỗi, thử lại lần {}", e.getId(), n, ex);
                }
            }
        }
    }
}
```

> **Lưu ý về payload:** ở đây ta gửi thẳng chuỗi JSON đã lưu trong `payload`. Vậy consumer
> vẫn nhận đúng JSON như trước (vì `KafkaProducerService` cũ cũng gửi object rồi
> `JsonSerializer` tự stringify). Nếu producer config của bạn đang set
> `value-serializer=JsonSerializer`, gửi `String` sẽ bị bọc thêm dấu `"`. Cách an toàn:
> tạo một `KafkaTemplate<String, String>` riêng với `StringSerializer` cho relay, hoặc
> deserialize `payload` về `Object` rồi gửi. Kiểm tra bằng 1 message thật.

### 4.6. Củng cố producer (rẻ mà lợi)

Trong `application.properties`:

```properties
spring.kafka.producer.acks=all
spring.kafka.producer.retries=5
spring.kafka.producer.properties.enable.idempotence=true
spring.kafka.producer.properties.max.in.flight.requests.per.connection=5
```

`acks=all` = chờ tất cả replica xác nhận mới coi là gửi thành công (không mất message khi
1 broker chết). `enable.idempotence=true` = Kafka tự chống gửi trùng ở tầng network.

### 4.7. Bật scheduling

`order-service` chưa có. Thêm vào class `OrderServiceApplication` hoặc một `@Configuration`:

```java
@SpringBootApplication
@EnableScheduling      // <-- thêm dòng này
public class OrderServiceApplication { ... }
```

---

## 5. Giải pháp 2 — Saga Recovery Scheduler

Outbox lo việc "event đã quyết định gửi thì chắc chắn tới". Nhưng còn trường hợp **saga
đứng im vì đang chờ một event từ ngoài không bao giờ tới** (payment-service chết) — cái này
cần một vòng quét riêng.

### 5.1. Thêm query

```java
// SageStateRepo.java
@Query("""
       SELECT s FROM SagaState s
       WHERE s.sagaStatus IN :statuses
         AND s.updatedAt < :threshold
       """)
List<SagaState> findStuck(@Param("statuses") List<SagaStatus> statuses,
                          @Param("threshold") LocalDateTime threshold);
```

### 5.2. Scheduler

```java
package com.ice.orderservice.Scheduler;

import com.ice.orderservice.Entity.Order;
import com.ice.orderservice.Entity.SagaState;
import com.ice.orderservice.Enum.*;
import com.ice.orderservice.Repository.OrderRepo;
import com.ice.orderservice.Repository.SageStateRepo;
import com.ice.orderservice.Service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final SageStateRepo sageStateRepo;
    private final OrderRepo orderRepo;
    private final OutboxService outboxService;

    @Scheduled(fixedDelay = 60_000)   // 1 phút/lần
    @Transactional
    public void recoverStuckSagas() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_MINUTES);
        List<SagaState> stuck = sageStateRepo.findStuck(
                List.of(SagaStatus.STARTED, SagaStatus.COMPENSATING), threshold);

        for (SagaState saga : stuck) {
            Order order = saga.getOrder();

            if (saga.getRetryCount() >= MAX_RETRY) {
                saga.setSagaStatus(SagaStatus.FAILED);
                saga.setFailureReason("Quá " + MAX_RETRY + " lần recovery vẫn kẹt ở " + saga.getCurrentStep());
                log.error("SAGA FAILED order={} step={} — cần người xử lý tay",
                          order.getId(), saga.getCurrentStep());
                continue;
            }
            saga.setRetryCount(saga.getRetryCount() + 1);

            switch (saga.getCurrentStep()) {

                // Đã reserve kho, đang chờ payment.processed mà mãi không tới.
                // An toàn nhất: huỷ đơn + nhả kho (compensate).
                case STOCK_RESERVED -> {
                    log.warn("Recovery: order {} kẹt ở STOCK_RESERVED, tiến hành huỷ + nhả kho", order.getId());
                    order.setStatus(OrderStatus.CANCELLED);
                    saga.setSagaStatus(SagaStatus.COMPENSATED);
                    // publish để inventory nhả kho; consumer phải idempotent
                    outboxService.publish("order.cancelled", order.getId().toString(),
                            buildCancelledPayload(order, "SAGA_TIMEOUT"));
                }

                // Đã confirm nhưng chưa chắc event order.confirmed đã ra.
                // Với Outbox thì trường hợp này gần như không còn — nhưng vẫn re-publish cho chắc.
                case ORDER_CONFIRMED -> {
                    log.warn("Recovery: order {} CONFIRMED, re-publish order.confirmed", order.getId());
                    outboxService.publish("order.confirmed", order.getId().toString(),
                            buildConfirmPayload(order));
                    saga.setSagaStatus(SagaStatus.COMPLETED);
                }

                default -> log.warn("Recovery: chưa có chiến lược cho step {} (order {})",
                                    saga.getCurrentStep(), order.getId());
            }
        }
    }

    // buildCancelledPayload / buildConfirmPayload: tách logic dựng payload dùng chung
    // từ OrderService ra một helper để tái sử dụng ở đây.
}
```

> **Nguyên tắc chọn hành động recovery:**
> - Nếu chưa có gì "không thể hoàn tác" xảy ra (chưa trừ tiền, chưa trừ kho) → **compensate**
>   (huỷ đơn, nhả reserve). An toàn nhất.
> - Nếu đã có bước không hoàn tác được (đã refund, đã trừ kho) → **đẩy tiếp** cho xong,
>   không được rollback.
> - Quá `MAX_RETRY` → `FAILED` + log `ERROR` để con người vào xử lý. **Đừng cố tự sửa mãi.**

---

## 6. Giải pháp 3 — Idempotency

"Idempotent" = gọi 1 lần hay 10 lần **kết quả cuối giống nhau**. Bắt buộc phải có vì:
Outbox có thể gửi trùng, Kafka giao at-least-once, recovery scheduler có thể re-publish.

### 6.1. Phía `order-service` — guard theo saga step

Trong `PaymentProcessedListener`, thay guard chỉ-check-status bằng check theo `completedSteps`:

```java
// Nếu saga đã đi qua bước này rồi -> message trùng -> bỏ qua, KHÔNG gọi lại inventory
if (saga.getCompletedSteps().contains("STOCK_DEDUCTED")) {
    log.info("Order {} đã STOCK_DEDUCTED rồi, bỏ qua payment.processed trùng", order.getId());
    return;
}
```

Và trước khi gọi `inventoryClient.deduct(...)`, chỉ gọi nếu chưa từng deduct.

### 6.2. Phía `inventory-service` — làm `deduct/release/reserve` idempotent theo `orderId`

Sửa `InventoryService.deductOrder()`:

```java
@Transactional
public DeductResponse deductOrder(DeductRequest request) {
    List<StockReservation> reserved =
            stockReservationService.getAllByOrderIdWithStatusRESERVED(request.getOrderId());

    if (reserved.isEmpty()) {
        // Không còn dòng RESERVED. Có phải vì đã deduct trước đó rồi không?
        List<StockReservation> deducted =
                stockReservationService.getAllByOrderIdWithStatusDEDUCT(request.getOrderId());
        if (!deducted.isEmpty()) {
            // ĐÃ trừ kho cho order này rồi -> trả OK, KHÔNG trừ lần nữa
            return new DeductResponse(true, request.getOrderId(), Instant.now());
        }
        throw new ResourceNotFoundException("no reserved stock found for orderId", ErrorCode.INVENTORY_NOT_FOUND);
    }
    // ... phần trừ kho như cũ ...
}
```

Làm tương tự:
- `releaseOrder()`: nếu không còn `RESERVED` nhưng đã có `RELEASED` cho orderId → trả OK.
- `reserveOrder()`: nếu **đã tồn tại** reservation cho orderId (bất kể trạng thái) → trả về
  `ReserveResponseSuccess` cũ, **không insert thêm** và **không cộng `reservedQty` lần nữa**.

> Với `returnOrder()` cũng vậy: đã `RETURNED` rồi thì trả OK.

### 6.3. (Tùy chọn) Bảng `processed_event` chống xử lý trùng ở consumer

Cách tổng quát hơn: mỗi listener, trước khi xử lý, thử `INSERT` `eventId` vào bảng
`processed_event (event_id PRIMARY KEY, processed_at)`. Nếu dính khóa trùng → đã xử lý rồi →
`return`. `KafkaEvent` của bạn **đã có sẵn `eventId`** nên rất hợp.

---

## 7. Giải pháp 4 — Kafka error handler + Dead Letter Topic

```java
package com.ice.orderservice.Config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@RequiredArgsConstructor
public class KafkaErrorHandlerConfig {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Bean
    public DefaultErrorHandler errorHandler() {
        // Sau khi retry mà vẫn lỗi -> đẩy sang topic "<tên-topic-gốc>.DLT"
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) ->
                        new org.apache.kafka.common.TopicPartition(record.topic() + ".DLT", record.partition()));

        // thử lại 3 lần, mỗi lần cách 2 giây; hết thì gọi recoverer ở trên
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(2_000L, 3L));

        // Lỗi kiểu này thì retry vô ích -> cho vào DLT luôn
        handler.addNotRetryableExceptions(
                com.ice.orderservice.Exception.ResourceNotFoundException.class,
                IllegalArgumentException.class);

        return handler;
    }
}
```

Spring Boot sẽ tự gắn bean `DefaultErrorHandler` này vào listener container mặc định. Sau đó
tạo sẵn các topic `payment.processed.DLT`, `stock.released.DLT`... (hoặc để Kafka auto-create).
Định kỳ xem DLT để biết message nào chết.

---

## 8. Thứ tự triển khai & checklist

Làm từ trên xuống, mỗi bước test xong mới sang bước sau:

### Bước 1 — Outbox *(diệt lỗ hổng A, đỡ phần lớn C)*
- [ ] Thêm enum `OutboxStatus`, entity `OutboxEvent`, repo `OutboxEventRepo`
- [ ] Tạo bảng `outbox_event` (ddl-auto hoặc SQL tay)
- [ ] Viết `OutboxService.publish(...)`
- [ ] Thay mọi `kafkaProducerService.publishXxx(...)` trong `OrderService` +
      `PaymentProcessedListener` bằng `outboxService.publish(...)`
- [ ] Viết `OutboxRelayScheduler`
- [ ] `@EnableScheduling` trên `OrderServiceApplication`
- [ ] Thêm `acks=all`, `enable.idempotence=true` vào `application.properties`
- [ ] **Test:** đặt 1 đơn COD, xem bảng `outbox_event` có dòng `PENDING` → sau ~2s thành
      `SENT`; consumer vẫn nhận đúng. Thử tắt Kafka → dòng vẫn `PENDING`, bật lại → tự gửi.

### Bước 2 — Idempotency *(diệt lỗ hổng D)*
- [ ] Guard listener theo `completedSteps`
- [ ] `deductOrder` / `releaseOrder` / `reserveOrder` / `returnOrder` idempotent theo `orderId`
- [ ] **Test:** gửi lại tay 1 message `payment.processed` cũ → không trừ kho lần 2, không lỗi

### Bước 3 — Saga recovery *(diệt lỗ hổng B)*
- [ ] `SageStateRepo.findStuck(...)`
- [ ] `SagaRecoveryScheduler`
- [ ] Tách helper dựng payload dùng chung
- [ ] **Test:** tạo đơn online, cố tình không cho payment-service phản hồi → sau
      `STUCK_MINUTES`, scheduler huỷ đơn + nhả kho; sau `MAX_RETRY` lần thì saga = `FAILED`

### Bước 4 — Kafka error handler + DLT *(lỗ hổng E)*
- [ ] `KafkaErrorHandlerConfig`
- [ ] **Test:** gửi 1 message JSON rác vào `payment.processed` → sau 3 lần retry nó nằm ở
      `payment.processed.DLT`, listener vẫn xử lý được message tiếp theo

### Bước 5 *(làm sau, refactor lớn)* — đưa REST call ra khỏi `@Transactional` của `createOrder`
- [ ] `createOrder` chỉ lưu order `PENDING` + outbox `order.created` rồi return
- [ ] Reserve / createPayment chuyển sang xử lý ở listener của `order.created` hoặc bước riêng
- [ ] Job đối soát payment "mồ côi"

---

## 9. Những thứ được phép bỏ qua

Ở scope project học, **không cần**:

- **Kafka exactly-once / transactional producer** (`KafkaTransactionManager`): phức tạp,
  Outbox + idempotency đã đủ tốt.
- **Crash ngay giữa lúc scheduler đang chạy**: scheduler `@Transactional` + query lại từ đầu
  mỗi lần chạy nên tự nó đã an toàn với crash.
- **Distributed transaction / 2PC**: không ai làm thế với microservice nữa.
- **Chạy nhiều instance `order-service` cùng lúc**: nếu sau này chạy ≥2 instance thì
  `OutboxRelayScheduler` cần `SELECT ... FOR UPDATE SKIP LOCKED` để 2 instance không gửi
  trùng. Một instance thì chưa cần lo.

---

## Tóm tắt 1 dòng

> Nguồn gốc mọi rắc rối là **ghi DB và gửi Kafka không cùng transaction**. Sửa bằng
> **Outbox** (gửi Kafka biến thành ghi DB), **Saga recovery scheduler** (quét đơn kẹt),
> **Idempotency** (xử lý trùng không sao), **DLT** (message chết không làm nghẽn).
