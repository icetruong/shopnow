# ShopNow — Nền tảng E-Commerce Microservices

> Sàn thương mại điện tử xây theo kiến trúc microservices event-driven với Spring Boot 4 & Java 21.
> Mỗi service có database riêng, giao tiếp qua Kafka (async) và REST nội bộ (sync bắt buộc).

---

## Mục lục

- [Tổng quan](#tổng-quan)
- [Kiến trúc](#kiến-trúc)
- [Danh sách service](#danh-sách-service)
- [Tech stack](#tech-stack)
- [Luồng nghiệp vụ chính — Saga đặt hàng](#luồng-nghiệp-vụ-chính--saga-đặt-hàng)
- [Kafka topics](#kafka-topics)
- [Cơ chế bảo mật](#cơ-chế-bảo-mật)
- [Yêu cầu hạ tầng](#yêu-cầu-hạ-tầng)
- [Chạy dự án](#chạy-dự-án)
- [Biến môi trường](#biến-môi-trường)
- [Cấu trúc thư mục](#cấu-trúc-thư-mục)
- [Tài liệu API](#tài-liệu-api)

---

## Tổng quan

ShopNow cài đặt đầy đủ luồng của một sàn TMĐT: đăng ký/đăng nhập, duyệt sản phẩm, tìm kiếm,
giỏ hàng, đặt hàng, thanh toán (VNPay / MoMo / Stripe), quản lý tồn kho, giao vận và thông báo.

Nguyên tắc kiến trúc:

- **Database per service** — không JOIN cross-service. Cần dữ liệu service khác thì gọi REST nội bộ hoặc đọc từ event Kafka.
- **Event-driven** — thay đổi trạng thái quan trọng được publish lên Kafka; các service khác phản ứng bất đồng bộ.
- **Saga choreography** cho luồng đặt hàng — không có orchestrator trung tâm, mỗi service tự publish/consume event và tự bù trừ (compensation) khi lỗi.
- **Entity ID = UUID v4** (String) — vì mỗi service có DB riêng, không dùng chung sequence.
- **JWT RS256** — `user-service` ký token bằng private key, các service khác verify bằng public key (OAuth2 Resource Server).

---

## Kiến trúc

```
                         ┌──────────────┐
        Client  ───────▶ │ user-service │  ── ký JWT (RS256, private.pem)
                         └──────┬───────┘
                                │ publish: user.registered
                                ▼
          ┌───────────────────────────────────────────────┐
          │                  Apache Kafka                  │
          └───────────────────────────────────────────────┘
             ▲          ▲            ▲            ▲       ▲
   product.  │  order.  │  payment.  │   stock.   │  shipment.
   updated   │  created │  processed │  reserved  │  updated
             │          │            │  released  │
   ┌─────────┴──┐  ┌────┴─────┐  ┌───┴──────┐  ┌──┴────────┐  ┌──────────────┐
   │  product-  │  │  order-  │  │ payment- │  │ inventory-│  │  shipping-   │
   │  service   │  │  service │  │ service  │  │  service  │  │  service     │
   └─────┬──────┘  └────┬─────┘  └──────────┘  └───────────┘  └──────┬───────┘
         │              │                                            │
         │ REST nội bộ  │ REST nội bộ (X-Internal-Token)             │
         ▼              ▼                                            ▼
   ┌───────────┐  ┌───────────┐   ┌──────────────┐        ┌────────────────────┐
   │ inventory │  │ cart /    │   │ search-      │        │ notification-service│
   │  service  │  │ user /    │   │ service (ES) │        │  (consume đa topic) │
   └───────────┘  │ payment   │   └──────────────┘        └────────────────────┘
                  └───────────┘

Hạ tầng dùng chung: PostgreSQL · Redis · Kafka · Elasticsearch · MinIO
```

---

## Danh sách service

| Service | Port | Package gốc | Persistence |
|---|---|---|---|
| **user-service** | 8081 | `com.qlda.userservice` | PostgreSQL `user_service_db` + Redis |
| **Product-service** | 8082 | `com.ice.productservice` | PostgreSQL `product_service_db` + Redis + Elasticsearch + MinIO |
| **inventory-service** | 8083 | `com.ice.inventoryservice` | PostgreSQL `inventory_service_db` + Redis |
| **cart-service** | 8084 | `com.ice.cartservice` | Redis only |
| **order-service** | 8085 | `com.ice.orderservice` | PostgreSQL `order_service_db` |
| **payment-service** | 8086 | `com.ice.paymentservice` | PostgreSQL `payment_service_db` |
| **shipping-service** | 8087 | `com.ice.shippingservice` | PostgreSQL `shipping_service_db` |
| **notification-service** | 8088 | `com.ice.notificationservice` | PostgreSQL `notification_service_db` |
| **search-service** | 8089 | `com.ice.searchservice` | Elasticsearch |

### Trách nhiệm từng service

- **user-service** — đăng ký, đăng nhập, JWT access/refresh token, OAuth2 Google, quản lý địa chỉ, upload avatar, chức năng admin, blacklist token (Redis), publish `user.registered`.
- **Product-service** — catalog sản phẩm, biến thể (size/màu), thuộc tính, category cây, upload ảnh (MinIO), đồng bộ sang Elasticsearch, publish `product.updated`, consume `review.posted`.
- **inventory-service** — tồn kho theo biến thể, reserve/release/deduct/return stock, lịch sử giao dịch kho, flash sale (Redis atomic), scheduler dọn reservation hết hạn, publish `stock.reserved` / `stock.released`.
- **cart-service** — giỏ hàng lưu Redis (TTL), chọn item, validate tồn kho + giá khi checkout, phát hành checkout token cho order-service.
- **order-service** — tạo đơn từ checkout token, state machine trạng thái đơn, Saga choreography, lưu `SagaState` để crash-recovery, consume `payment.processed` / `stock.released`, gọi REST nội bộ tới cart/user/payment/inventory.
- **payment-service** — tích hợp VNPay / MoMo / Stripe, xử lý webhook/IPN, idempotency (`ProcessedWebhook`), hoàn tiền (refund), đối soát (reconciliation), publish `payment.processed` / `payment.refund`.
- **shipping-service** — tạo shipment từ đơn đã xác nhận, map địa chỉ sang mã vùng hãng vận chuyển, tính phí ship, cập nhật tracking qua webhook, publish `shipment.updated`.
- **notification-service** — email / SMS / push, template thông báo, quản lý device token và tuỳ chọn nhận thông báo của user; consume `user.registered`, `payment.processed`, `shipment.updated`.
- **search-service** — full-text search sản phẩm, lọc theo thuộc tính, aggregation (category / màu / khoảng giá) trên Elasticsearch; consume `product.updated` để đồng bộ index.

---

## Tech stack

| Nhóm | Công nghệ |
|---|---|
| Ngôn ngữ / Runtime | Java 21 |
| Framework | Spring Boot 4.0.6 (Spring MVC, Spring Data JPA, Spring Security 6) |
| Auth | OAuth2 Resource Server (JWT RS256), OAuth2 Client (Google), refresh token rotation |
| Messaging | Apache Kafka (Spring Kafka) |
| Cache / Lock | Redis (Spring Data Redis) |
| Search | Elasticsearch 8 (Spring Data Elasticsearch) |
| Database | PostgreSQL, Spring Data JPA + Specification |
| Object storage | MinIO (S3-compatible) |
| Payment | VNPay, MoMo, Stripe |
| Build | Maven (mỗi service có `mvnw` wrapper riêng) |
| Tiện ích | Lombok |

> Mỗi service là một Maven project độc lập; expose port riêng và verify JWT cục bộ.

---

## Luồng nghiệp vụ chính — Saga đặt hàng

Choreography-based Saga, không có orchestrator:

```
1. cart-service   : validate giỏ hàng + giá + stock  →  phát hành checkout token
2. order-service  : nhận token → tạo Order (PENDING) → lưu SagaState
                    → gọi inventory reserve (REST) → gọi payment create (REST)
3. payment-service: xử lý thanh toán (COD / VNPay / MoMo / Stripe)
                    → publish  payment.processed { status: SUCCESS | FAILED }
4a. SUCCESS  → order-service: deduct stock, chuyển Order sang CONFIRMED
             → shipping-service: tạo shipment → publish shipment.updated
             → notification-service: gửi email xác nhận đơn
4b. FAILED   → order-service: hủy đơn (CANCELLED)
             → inventory-service: publish stock.released → hoàn kho
             → payment-service : publish payment.refund (nếu đã charge)
```

- **Crash-recovery**: `order-service` lưu `SagaState` + `CurrentStep`; xem `order-service/docs/crash-recovery.md`.
- **Idempotency**: webhook thanh toán / giao vận ghi vào bảng `ProcessedWebhook` để chống xử lý trùng.

---

## Kafka topics

Quy ước tên topic: `{domain}.{event}` — chữ thường, phân tách bằng dấu chấm.
Message key = ID của entity liên quan nhất (đảm bảo thứ tự trong cùng partition).

| Topic | Publisher | Consumer chính |
|---|---|---|
| `user.registered` | user-service | notification-service |
| `product.updated` | Product-service | search-service |
| `order.created` | order-service | payment-service, inventory-service |
| `payment.processed` | payment-service | order-service, notification-service |
| `payment.refund` | payment-service | order-service, notification-service |
| `stock.reserved` | inventory-service | order-service |
| `stock.released` | inventory-service | order-service |
| `shipment.updated` | shipping-service | order-service, notification-service |

Schema chung của mọi event:

```json
{
  "eventId":   "uuid-v4",
  "eventType": "order.created",
  "timestamp": "2026-01-15T10:30:00Z",
  "version":   "1.0",
  "payload":   { }
}
```

Mỗi service có consumer group riêng (`order-service`, `product-service`, ...) để nhận đủ mọi message của topic.

---

## Cơ chế bảo mật

### JWT cho request từ client

- `user-service` ký JWT bằng **RSA private key** (`src/main/resources/keys/private.pem`), thuật toán RS256.
- Các service còn lại là **OAuth2 Resource Server**, verify chữ ký bằng **public key** (`keys/public.pem`).
- Access token: 15 phút · Refresh token: 30 ngày (lưu DB, rotate mỗi lần dùng).
- Logout: đưa `jti` vào blacklist Redis với TTL = thời gian sống còn lại của token.

### REST nội bộ giữa các service

- Các endpoint `/internal/**` được bảo vệ bằng header **`X-Internal-Token`** (shared secret).
- Kiểm tra bởi `InternalTokenFilter` trong mỗi service; giá trị lấy từ biến môi trường `INTERNAL_SECRET_TOKEN`.
- Các cặp gọi REST nội bộ bắt buộc (sync):

| Caller | Callee | Mục đích |
|---|---|---|
| cart-service | Product-service | lấy tên, giá sản phẩm |
| cart-service | inventory-service | kiểm tra tồn kho |
| order-service | user-service | lấy địa chỉ giao hàng |
| order-service | payment-service | tạo giao dịch thanh toán |
| order-service | inventory-service | reserve / deduct / release / return stock |
| Product-service | inventory-service | tạo bản ghi tồn kho khi thêm biến thể mới |

> ⚠️ Các file `keys/*.pem` và `.env` đã được `.gitignore`. Không commit khóa thật.

---

## Yêu cầu hạ tầng

Cần chạy sẵn trước khi khởi động service:

| Thành phần | Cổng mặc định | Dùng bởi |
|---|---|---|
| PostgreSQL 16 | 5432 | user, product, inventory, order, payment, shipping, notification |
| Redis 7 | 6379 | user, product, inventory, cart |
| Apache Kafka | 9092 | user, product, inventory, order, payment, shipping, notification, search |
| Elasticsearch 8 | 9200 | product, search |
| MinIO | 9000 | product |

Tạo database (tên khớp `application.properties`):

```sql
CREATE DATABASE user_service_db;
CREATE DATABASE product_service_db;
CREATE DATABASE inventory_service_db;
CREATE DATABASE order_service_db;
CREATE DATABASE payment_service_db;
CREATE DATABASE shipping_service_db;
CREATE DATABASE notification_service_db;
```

---

## Chạy dự án

Mỗi service chạy độc lập bằng Maven wrapper của chính nó.

```bash
# Ví dụ: user-service
cd user-service
./mvnw spring-boot:run          # Linux/macOS
mvnw.cmd spring-boot:run        # Windows

# Đóng gói
./mvnw clean package
java -jar target/user-service-0.0.1-SNAPSHOT.jar
```

Thứ tự khởi động gợi ý: `user-service` → `Product-service` → `inventory-service` →
`cart-service` → `order-service` → `payment-service` → `shipping-service` →
`notification-service` → `search-service`.

Chạy test:

```bash
cd <service>
./mvnw test
```

---

## Biến môi trường

Đặt trong `.env` hoặc export ở shell trước khi chạy.

### Dùng chung

| Biến | Ý nghĩa |
|---|---|
| `INTERNAL_SECRET_TOKEN` | shared secret cho REST nội bộ (`X-Internal-Token`) — mọi service |

### user-service

| Biến | Ý nghĩa |
|---|---|
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | OAuth2 login Google |

### payment-service

| Biến | Ý nghĩa |
|---|---|
| `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_RETURN_URL` | cấu hình VNPay (URL sandbox có sẵn default) |
| `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY`, `MOMO_IPN_URL`, `MOMO_REDIRECT_URL` | cấu hình MoMo |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_SUCCESS_URL`, `STRIPE_CANCEL_URL` | cấu hình Stripe |

Giá trị hạ tầng khác (PostgreSQL `postgres/123456`, Redis/Kafka `localhost`, MinIO `minioadmin/minioadmin`,
Elasticsearch `elastic/changeme`) đang đặt trong `application.properties` — chỉnh trực tiếp khi cần.

---

## Cấu trúc thư mục

```
E-Commerce/
├── user-service/            # com.qlda.userservice
├── Product-service/         # com.ice.productservice
├── inventory-service/
├── cart-service/
├── order-service/
│   └── docs/                # crash-recovery.md, order-service-todo.md
├── payment-service/
├── shipping-service/
├── notification-service/
├── search-service/
├── Project_context.md       # bối cảnh & convention gốc của project
└── README.md
```

Bố cục bên trong mỗi service:

```
<service>/src/main/java/com/<group>/<service>/
├── Config/         # SecurityConfig, RedisConfig, InternalTokenFilter, ...
├── Controller/     # REST controller; Controller/Internal/ cho endpoint /internal/**
├── Service/        # business logic + KafkaProducerService / KafkaConsumerService
├── Repository/     # Spring Data JPA repository
├── Entity/         # JPA entity (ID kiểu String/UUID)
├── DTO/            # Request / Response / Event
├── Enum/           # ErrorCode và các enum domain
├── Exception/      # custom exception + GlobalExceptionHandler
├── Client/         # REST client gọi service khác
└── <Service>Application.java
```

---

## Tài liệu API

Mỗi service có file đặc tả API riêng ở thư mục gốc của service:

| Service | File |
|---|---|
| user-service | `user-service/UserServiceApiSpec.md` |
| Product-service | `Product-service/ProductServiceApiSpec.md` |
| inventory-service | `inventory-service/inventoryServiceApiSpec.md` |
| cart-service | `cart-service/CartServiceApiSpec.md` |
| order-service | `order-service/orderServiceApiSpec.md` |
| payment-service | `payment-service/paymentServiceApiSpec.md` |
| shipping-service | `shipping-service/shippingServiceApiSpec.md` |
| notification-service | `notification-service/notificationServiceApiSpec.md` |
| search-service | `search-service/searchServiceApiSpec.md` |
