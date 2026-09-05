# ShopNow — Nền tảng E-Commerce Microservices

> Sàn thương mại điện tử theo kiến trúc microservices, event-driven, xây bằng **Java 21 + Spring Boot 4**.
> Mỗi service là một Maven project độc lập, có database riêng, giao tiếp với nhau qua **Kafka** (bất đồng bộ)
> và **REST nội bộ** (đồng bộ, chỉ dùng khi bắt buộc).

Đây là project học tập — mục tiêu là dựng lại một hệ thống TMĐT ở mức "gần production" để thực hành
Spring Security, JWT/OAuth2, JPA, Kafka, Redis, Elasticsearch và Saga pattern.

---

## Mục lục

- [Tổng quan](#tổng-quan)
- [Nguyên tắc kiến trúc](#nguyên-tắc-kiến-trúc)
- [Sơ đồ kiến trúc](#sơ-đồ-kiến-trúc)
- [Danh sách service](#danh-sách-service)
- [Trách nhiệm từng service](#trách-nhiệm-từng-service)
- [Tech stack](#tech-stack)
- [Luồng đặt hàng — Saga choreography](#luồng-đặt-hàng--saga-choreography)
- [Kafka topics](#kafka-topics)
- [REST nội bộ giữa các service](#rest-nội-bộ-giữa-các-service)
- [Bảo mật](#bảo-mật)
- [Hạ tầng cần có](#hạ-tầng-cần-có)
- [Chạy dự án](#chạy-dự-án)
- [Biến môi trường](#biến-môi-trường)
- [Cấu trúc thư mục](#cấu-trúc-thư-mục)
- [Tài liệu API](#tài-liệu-api)

---

## Tổng quan

ShopNow cài đặt luồng đầy đủ của một sàn TMĐT: đăng ký / đăng nhập, duyệt sản phẩm, tìm kiếm,
giỏ hàng, đặt hàng, thanh toán (VNPay / MoMo / Stripe), quản lý tồn kho, giao vận, đánh giá,
khuyến mãi / flash sale và thông báo.

Repo là một **multi-module theo thư mục** (không phải Maven parent POM): mỗi service tự build,
tự chạy, tự expose port và tự verify JWT.

---

## Nguyên tắc kiến trúc

- **Database per service** — mỗi service sở hữu dữ liệu của mình, không JOIN cross-service.
  Cần dữ liệu của service khác thì gọi REST nội bộ hoặc đọc từ event Kafka.
- **Event-driven** — mọi thay đổi trạng thái quan trọng được publish lên Kafka theo một
  envelope chung; các service khác phản ứng bất đồng bộ.
- **Saga choreography** cho luồng đặt hàng — không có orchestrator trung tâm. Mỗi service tự
  publish / consume event và tự bù trừ (compensation) khi có lỗi.
- **Entity ID = UUID v4** (kiểu `String`) — vì mỗi service có DB riêng, không dùng chung sequence.
- **JWT RS256** — `user-service` ký token bằng private key, các service khác verify bằng
  public key (OAuth2 Resource Server), không gọi ngược lại `user-service`.
- **Kafka message key = ID của entity liên quan nhất** — đảm bảo thứ tự xử lý trong cùng partition.

---

## Sơ đồ kiến trúc

```
                    ┌───────────────┐
   Client ────────▶ │  api-gateway  │  (cửa ngõ duy nhất — đang ở mức skeleton)
                    └───────┬───────┘
                            │ route + validate JWT 1 lần
        ┌───────────────────┼───────────────────────────────┐
        ▼                   ▼                               ▼
 ┌────────────┐      ┌────────────┐                  ┌────────────┐
 │user-service│      │  product/  │       ...        │  các       │
 │  (ký JWT)  │      │  cart/...  │                  │  service   │
 └─────┬──────┘      └─────┬──────┘                  └─────┬──────┘
       │  publish/consume event                           │
       ▼                                                   ▼
 ┌──────────────────────────── Apache Kafka ───────────────────────────┐
 │  user.* · product.updated · order.* · payment.* · stock.* ·         │
 │  shipment.updated · review.posted · flash.purchased · promotion.*   │
 └────────────────────────────────────────────────────────────────────┘

 REST nội bộ (đồng bộ, header X-Internal-Token) — chỉ ở các cặp bắt buộc:
   cart → product, cart → inventory
   order → user, order → payment, order → inventory
   promotion → product, promotion → inventory
   review → order, review → user

 Hạ tầng dùng chung: PostgreSQL · Redis · Kafka · Elasticsearch · MinIO
```

> `api-gateway` hiện mới có class `ApiGatewayApplication` + file đặc tả
> `api-gateway/apiGatewaySpecConfig.md`. Khi chưa dựng xong, client gọi thẳng port từng service.

---

## Danh sách service

| Service | Port | Package gốc | Spring Boot | Persistence |
|---|---|---|---|---|
| **api-gateway** | 8080 | `com.ice.apigateway` | 4.1.1 | — (skeleton) |
| **user-service** | 8081 | `com.qlda.userservice` | 4.0.6 | PostgreSQL `user_service_db` + Redis |
| **Product-service** | 8082 | `com.ice.productservice` | 4.0.6 | PostgreSQL `product_service_db` + Redis + MinIO |
| **inventory-service** | 8083 | `com.ice.inventoryservice` | 4.0.6 | PostgreSQL `inventory_service_db` + Redis |
| **cart-service** | 8084 | `com.ice.cartservice` | 4.0.6 | Redis only |
| **order-service** | 8085 | `com.ice.orderservice` | 4.1.0 | PostgreSQL + Redis |
| **payment-service** | 8086 | `com.ice.paymentservice` | 4.1.0 | PostgreSQL |
| **shipping-service** | 8087 | `com.ice.shippingservice` | 4.1.1 | PostgreSQL + Redis |
| **notification-service** | 8088 | `com.ice.notificationservice` | 4.1.1 | PostgreSQL + Redis |
| **search-service** | 8089 | `com.ice.searchservice` | 4.1.1 | Elasticsearch + Redis |
| **review-service** | 8090 | `com.ice.reviewservice` | 4.1.1 | PostgreSQL |
| **promotion-service** | 8091 | `com.ice.promotionservice` | 4.1.1 | PostgreSQL + Redis |

> **Datasource:** `user-service`, `Product-service`, `inventory-service` khai báo sẵn
> `spring.datasource.*` trong `application.properties` (`postgres` / `123456`).
> Các service JPA còn lại (`order`, `payment`, `shipping`, `notification`, `review`, `promotion`)
> nhận thông tin kết nối DB qua biến môi trường / file `.env` cục bộ (không commit). Quy ước tên
> DB: `<service>_service_db`.

---

## Trách nhiệm từng service

- **api-gateway** — (dự kiến) cửa ngõ duy nhất: routing, validate JWT một lần, forward user
  context qua header, rate limiting, circuit breaker. Không route `/internal/**`.
- **user-service** — đăng ký, đăng nhập, access / refresh token, OAuth2 Google, quản lý địa chỉ,
  upload avatar, chức năng admin, blacklist token (Redis). Publish `user.registered`,
  `user.password_reset_requested`.
- **Product-service** — catalog sản phẩm, biến thể (size / màu), thuộc tính, category dạng cây,
  upload ảnh (MinIO). Publish `product.updated`; consume `review.posted`, `stock.changed` để cập
  nhật thông tin hiển thị.
- **inventory-service** — tồn kho theo biến thể; reserve / release / deduct / return stock; lịch
  sử giao dịch kho; flash sale (Redis atomic); scheduler dọn reservation hết hạn. Publish
  `stock.changed`, `stock.released`, `stock.low_warning`, `flash.purchased`.
- **cart-service** — giỏ hàng lưu Redis (có TTL), chọn item, validate tồn kho + giá khi checkout,
  phát hành checkout token cho `order-service`.
- **order-service** — tạo đơn từ checkout token, state machine trạng thái đơn, Saga choreography,
  lưu `SagaState` để crash-recovery. Gọi REST nội bộ tới cart / user / payment / inventory;
  consume `payment.processed`, `payment.refunded`, `stock.released`, `shipment.updated`. Publish
  `order.created`, `order.confirmed`, `order.cancelled`, `order.refunded`.
- **payment-service** — tích hợp VNPay / MoMo / Stripe, xử lý webhook / IPN, idempotency
  (`ProcessedWebhook`), hoàn tiền, đối soát. Publish `payment.processed`, `payment.refunded`.
- **shipping-service** — tạo shipment từ đơn đã xác nhận; abstraction hãng vận chuyển
  (`carrier.mode=mock` mặc định, `real` gọi GHN / GHTK thật); tính phí ship; cập nhật tracking
  qua webhook; scheduler retry tạo vận đơn. Consume `order.confirmed`, `order.cancelled`;
  publish `shipment.updated`.
- **notification-service** — email / SMS / push, template thông báo, quản lý device token và tuỳ
  chọn nhận thông báo; có xử lý DLT. Consume `user.registered`, `user.password_reset_requested`,
  `order.created`, `order.confirmed`, `order.cancelled`, `payment.processed`, `payment.refunded`,
  `shipment.updated`, `stock.low_warning`.
- **search-service** — full-text search sản phẩm, lọc theo thuộc tính, aggregation (category /
  màu / khoảng giá) trên Elasticsearch. Consume `product.updated` để đồng bộ index.
- **review-service** — rating, comment, moderation; kiểm tra user đã mua hàng qua REST tới
  `order-service`. Publish `review.posted`.
- **promotion-service** — coupon và flash sale (Redis atomic counter chống oversell), scheduler
  kích hoạt flash sale. Gọi REST nội bộ tới product / inventory; consume `flash.purchased`;
  publish `promotion.flash_sale_starting`.

---

## Tech stack

| Nhóm | Công nghệ |
|---|---|
| Ngôn ngữ / Runtime | Java 21 |
| Framework | Spring Boot 4.x (4.0.6 – 4.1.1 tuỳ service): Spring MVC, Spring Data JPA, Spring Security 6 |
| Auth | OAuth2 Resource Server (JWT RS256), OAuth2 Client (Google), refresh token rotation |
| Messaging | Apache Kafka (Spring Kafka) — JSON envelope, có DLT ở `notification-service` |
| Cache / Lock | Redis (Spring Data Redis) |
| Search | Elasticsearch 8 (Spring Data Elasticsearch) — chỉ `search-service` |
| Database | PostgreSQL, Spring Data JPA + Specification |
| Object storage | MinIO (S3-compatible) — chỉ `Product-service` |
| Payment | VNPay, MoMo, Stripe (`stripe-java`) |
| Build | Maven wrapper riêng cho mỗi service (`mvnw` / `mvnw.cmd`) |
| Tiện ích | Lombok |

---

## Luồng đặt hàng — Saga choreography

Không có orchestrator. Đường đi thuận dùng REST nội bộ, đường phản hồi và bù trừ dùng Kafka.

```
1. cart-service    : validate giỏ hàng + giá + tồn kho  →  phát hành checkout token
2. order-service   : nhận token → tạo Order (PENDING) → lưu SagaState
                     → REST: inventory reserve stock
                     → REST: payment tạo giao dịch
3. payment-service : xử lý thanh toán (COD / VNPay / MoMo / Stripe)
                     → publish payment.processed { status: SUCCESS | FAILED }

4a. SUCCESS  → order-service    : deduct stock, chuyển Order sang CONFIRMED
                                 → publish order.confirmed
             → shipping-service : tạo shipment → publish shipment.updated
             → notification-service : gửi email xác nhận đơn

4b. FAILED   → order-service    : hủy đơn (CANCELLED) → publish order.cancelled
             → inventory-service : publish stock.released → hoàn kho
             → payment-service  : publish payment.refunded (nếu đã charge)
```

- **Crash-recovery:** `order-service` lưu `SagaState` + bước hiện tại của saga để phục hồi khi
  service restart giữa chừng.
- **Idempotency:** webhook thanh toán / giao vận được ghi vào bảng `ProcessedWebhook`; consumer
  Kafka dùng `eventId` để chống xử lý trùng.

---

## Kafka topics

Quy ước tên topic: `{domain}.{event}` — chữ thường, phân tách bằng dấu chấm.
Message key = ID của entity liên quan nhất.

| Topic | Publisher | Consumer chính |
|---|---|---|
| `user.registered` | user-service | notification-service |
| `user.password_reset_requested` | user-service | notification-service |
| `product.updated` | Product-service | search-service |
| `review.posted` | review-service | Product-service |
| `stock.changed` | inventory-service | Product-service |
| `stock.released` | inventory-service | order-service |
| `stock.low_warning` | inventory-service | notification-service |
| `flash.purchased` | inventory-service | promotion-service |
| `promotion.flash_sale_starting` | promotion-service | (dự kiến: notification-service) |
| `order.created` | order-service | notification-service |
| `order.confirmed` | order-service | shipping-service, notification-service |
| `order.cancelled` | order-service | shipping-service, notification-service |
| `order.refunded` | order-service | notification-service |
| `payment.processed` | payment-service | order-service, notification-service |
| `payment.refunded` | payment-service | order-service, notification-service |
| `shipment.updated` | shipping-service | order-service, notification-service |

Envelope chung của mọi event:

```json
{
  "eventId":   "uuid-v4",
  "eventType": "order.created",
  "timestamp": "2026-01-15T10:30:00Z",
  "version":   "1.0",
  "payload":   { }
}
```

Mỗi service có consumer group riêng (`order-service`, `promotion-service`, ...) để nhận đủ mọi
message của topic.

---

## REST nội bộ giữa các service

- Endpoint `/internal/**` được bảo vệ bằng header **`X-Internal-Token`** (shared secret).
- Kiểm tra bởi `InternalTokenFilter` trong mỗi service; giá trị lấy từ biến môi trường
  `INTERNAL_SECRET_TOKEN`.
- Không route `/internal/**` qua `api-gateway`.

| Caller | Callee | Mục đích |
|---|---|---|
| cart-service | Product-service | lấy tên, giá sản phẩm |
| cart-service | inventory-service | kiểm tra tồn kho |
| order-service | user-service | lấy địa chỉ giao hàng |
| order-service | payment-service | tạo giao dịch thanh toán |
| order-service | inventory-service | reserve / deduct / release / return stock |
| promotion-service | Product-service | lấy thông tin sản phẩm cho coupon / flash sale |
| promotion-service | inventory-service | kiểm tra / trừ tồn kho flash sale |
| review-service | order-service | xác minh user đã mua sản phẩm |
| review-service | user-service | lấy thông tin người đánh giá |

Mọi trao đổi khác đi qua **Kafka async**.

---

## Bảo mật

### JWT cho request từ client

- `user-service` ký JWT bằng **RSA private key** (`src/main/resources/keys/private.pem`), RS256.
- Các service còn lại là **OAuth2 Resource Server**, verify chữ ký bằng **public key**
  (`keys/public.pem`).
- Access token: **900s (15 phút)** · Refresh token: **2592000s (30 ngày)** — lưu DB, rotate mỗi
  lần dùng · Reset-password token: 900s.
- Logout: đưa `jti` vào blacklist Redis với TTL = thời gian sống còn lại của token.

### Khác

- `/internal/**` bảo vệ bằng `X-Internal-Token` (xem mục trên).
- Các file `keys/*.pem`, `.env` và `Project_context.md` đã nằm trong `.gitignore` — **không commit
  khóa thật**.

---

## Hạ tầng cần có

Chạy sẵn trước khi khởi động service:

| Thành phần | Cổng mặc định | Dùng bởi |
|---|---|---|
| PostgreSQL 16 | 5432 | user, Product, inventory, order, payment, shipping, notification, review, promotion |
| Redis 7 | 6379 | user, Product, inventory, cart, order, shipping, notification, search, promotion |
| Apache Kafka | 9092 | user, Product, inventory, order, payment, shipping, notification, search, review, promotion |
| Elasticsearch 8 | 9200 | search |
| MinIO | 9000 | Product |

Tạo database (tên khớp cấu hình từng service):

```sql
CREATE DATABASE user_service_db;
CREATE DATABASE product_service_db;
CREATE DATABASE inventory_service_db;
CREATE DATABASE order_service_db;
CREATE DATABASE payment_service_db;
CREATE DATABASE shipping_service_db;
CREATE DATABASE notification_service_db;
CREATE DATABASE review_service_db;
CREATE DATABASE promotion_service_db;
```

---

## Chạy dự án

Mỗi service chạy độc lập bằng Maven wrapper của chính nó.

```bash
# Ví dụ: user-service
cd user-service
./mvnw spring-boot:run          # Linux/macOS
mvnw.cmd spring-boot:run        # Windows

# Đóng gói & chạy jar
./mvnw clean package
java -jar target/user-service-0.0.1-SNAPSHOT.jar
```

Thứ tự khởi động gợi ý:

```
user-service → Product-service → inventory-service → cart-service →
order-service → payment-service → shipping-service → notification-service →
search-service → review-service → promotion-service
```

Chạy test:

```bash
cd <service>
./mvnw test
```

---

## Biến môi trường

Đặt trong `.env` của từng service hoặc export ở shell trước khi chạy.

### Mọi service

| Biến | Ý nghĩa |
|---|---|
| `INTERNAL_SECRET_TOKEN` | shared secret cho REST nội bộ (`X-Internal-Token`) |

### Service JPA không khai báo datasource trong `application.properties`

`order`, `payment`, `shipping`, `notification`, `review`, `promotion` — cần cung cấp thêm:

| Biến | Ý nghĩa |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/<service>_service_db` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | tài khoản PostgreSQL |

### user-service

| Biến | Ý nghĩa |
|---|---|
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | OAuth2 login Google |
| `FRONTEND_RESET_PASSWORD_URL` | link trong email reset mật khẩu |

### payment-service

| Biến | Ý nghĩa |
|---|---|
| `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_RETURN_URL` | cấu hình VNPay (URL sandbox có default) |
| `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY`, `MOMO_IPN_URL`, `MOMO_REDIRECT_URL` | cấu hình MoMo |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_SUCCESS_URL`, `STRIPE_CANCEL_URL` | cấu hình Stripe |

### shipping-service (chỉ khi `carrier.mode=real`)

| Biến | Ý nghĩa |
|---|---|
| `GHN_TOKEN`, `GHN_SHOP_ID`, `GHN_WEBHOOK_SECRET` | tích hợp GHN |
| `GHTK_TOKEN`, `GHTK_WEBHOOK_SECRET` | tích hợp GHTK |

### notification-service

| Biến | Ý nghĩa |
|---|---|
| `NOTIFICATION_ADMIN_USER_ID` / `NOTIFICATION_ADMIN_EMAIL` | người nhận cảnh báo `stock.low_warning` (để trống → chỉ log) |

> Giá trị hạ tầng khác (PostgreSQL `postgres/123456`, Redis / Kafka `localhost`, MinIO
> `minioadmin/minioadmin`) đang đặt trực tiếp trong `application.properties` của các service cũ —
> chỉnh tại chỗ khi cần.

---

## Cấu trúc thư mục

```
E-Commerce/
├── api-gateway/             # com.ice.apigateway (skeleton) + apiGatewaySpecConfig.md
├── user-service/            # com.qlda.userservice
├── Product-service/         # com.ice.productservice
├── inventory-service/
├── cart-service/
├── order-service/
├── payment-service/
├── shipping-service/
├── notification-service/
├── search-service/
├── review-service/
├── promotion-service/
├── Project_context.md       # bối cảnh & convention gốc (gitignored)
└── README.md
```

Bố cục bên trong mỗi service (tên thư mục viết hoa theo convention hiện tại của repo):

```
<service>/src/main/java/com/<group>/<service>/
├── Config/         # SecurityConfig, RedisConfig, InternalTokenFilter, KafkaConfig, ...
├── Controller/     # REST controller (bao gồm cả endpoint /internal/**)
├── Service/        # business logic + KafkaProducerService / KafkaConsumerService
├── Listener/       # @KafkaListener (một số service tách riêng khỏi Service/)
├── Repository/     # Spring Data JPA repository
├── Entity/         # JPA entity (ID kiểu String / UUID)
├── DTO/            # Request / Response / Event
├── Enum/           # ErrorCode và các enum domain
├── Exception/      # custom exception + GlobalExceptionHandler
├── Client/         # REST client gọi service khác
├── Scheduler/      # scheduled job (order, shipping, promotion, ...)
└── <Service>Application.java
```

---

## Tài liệu API

Mỗi service có file đặc tả API riêng ở thư mục gốc của service:

| Service | File |
|---|---|
| api-gateway | `api-gateway/apiGatewaySpecConfig.md` |
| user-service | `user-service/UserServiceApiSpec.md` |
| Product-service | `Product-service/ProductServiceApiSpec.md` |
| inventory-service | `inventory-service/inventoryServiceApiSpec.md` |
| cart-service | `cart-service/CartServiceApiSpec.md` |
| order-service | `order-service/orderServiceApiSpec.md` |
| payment-service | `payment-service/paymentServiceApiSpec.md` |
| shipping-service | `shipping-service/shippingServiceApiSpec.md` |
| notification-service | `notification-service/notificationServiceApiSpec.md` |
| search-service | `search-service/searchServiceApiSpec.md` |
| review-service | `review-service/reviewServiceSpecApi.md` |
| promotion-service | `promotion-service/promotionServiceApiSpec.md` |
