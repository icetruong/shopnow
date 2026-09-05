# API Gateway — Configuration Specification

---

## Base URL
```
http://localhost:8080
```

## Vai trò
API Gateway là **cửa ngõ duy nhất** của toàn hệ thống. Mọi request từ client đều đi qua đây trước khi được route đến microservice tương ứng. Nó xử lý authentication, rate limiting, circuit breaking, và load balancing tập trung — các service phía sau không cần lo những việc này.

## Nguyên tắc thiết kế
- Client **chỉ biết địa chỉ Gateway**, không biết địa chỉ các service
- Gateway verify JWT **fail-fast** (chặn sớm request rõ ràng invalid), nhưng **không phải nguồn sự thật duy nhất** — mỗi service vẫn tự validate lại JWT bằng RSA public key như đang chạy (mô hình defense-in-depth, xem chi tiết ở Phần 2)
- Các service nội bộ **không expose ra internet** (chỉ Gateway public)

## ⚠️ Ghi chú về stack thực tế của project này
`api-gateway` chạy trên nền **Servlet (Spring MVC)** — `pom.xml` dùng `spring-boot-starter-webmvc`, không phải WebFlux/reactive. Vì vậy module dùng ở đây là **Spring Cloud Gateway Server WebMVC** (`spring-cloud-starter-gateway-server-webmvc`), **không phải** bản Gateway reactive cổ điển (`spring-cloud-starter-gateway`) mà đa số tutorial trên mạng hay dùng.

Hai bản này khác nhau khá nhiều ở tên property và một số filter built-in không tồn tại ở bản MVC (CORS toàn cục, rate limiter kiểu Redis). Toàn bộ tài liệu này đã được viết lại cho đúng với bản **WebMVC**. Version đang dùng: Spring Boot `4.1.1` + Spring Cloud `2025.1.3`.

---

# PHẦN 1 — ROUTING

---

## Bảng route mapping

| Route ID | Path pattern | Service đích | Port | Ghi chú |
|----------|--------------|--------------|------|---------|
| user-service | `/api/v1/auth/**`, `/api/v1/users/**`, `/api/v1/admin/users/**` | user-service | 8081 | |
| oauth2 | `/oauth2/**` | user-service | 8081 | OAuth2 callback |
| product-service | `/api/v1/products/**`, `/api/v1/categories/**`, `/api/v1/admin/products/**`, `/api/v1/admin/categories/**` | product-service | 8082 | |
| inventory-service | `/api/v1/admin/stock/**` | inventory-service | 8083 | |
| cart-service | `/api/v1/cart/**` | cart-service | 8084 | |
| order-service | `/api/v1/orders/**`, `/api/v1/admin/orders/**` | order-service | 8085 | |
| payment-service | `/api/v1/payments/**`, `/api/v1/admin/payments/**` | payment-service | 8086 | |
| shipping-service | `/api/v1/shipping/**`, `/api/v1/shipments/**`, `/api/v1/admin/shipments/**` | shipping-service | 8087 | |
| notification-service | `/api/v1/notifications/**`, `/api/v1/admin/notifications/**` | notification-service | 8088 | |
| search-service | `/api/v1/search/**` | search-service | 8089 | |
| review-service | `/api/v1/reviews/**`, `/api/v1/admin/reviews/**` | review-service | 8090 | |
| promotion-service | `/api/v1/coupons/**`, `/api/v1/flash-sales/**`, `/api/v1/admin/coupons/**`, `/api/v1/admin/flash-sales/**` | promotion-service | 8091 | |
| recommendation-service | `/api/v1/recommendations/**` | recommendation-service | 8092 | **Chưa tồn tại trong project** — thêm route này khi service được tạo |

**Lưu ý quan trọng:** Endpoint `/internal/**` của các service **KHÔNG được route qua Gateway** — chúng chỉ dùng cho service-to-service call trong mạng nội bộ.

---

## Dependency cần có (`pom.xml`)

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.1.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway-server-webmvc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-loadbalancer</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

`loadbalancer` bắt buộc phải có thì `lb://` mới thực sự resolve được qua Eureka (đây là cơ chế thay Ribbon cũ). `actuator` bắt buộc phải có thì endpoint `/actuator/gateway/routes` ở Phần 7 mới tồn tại — thiếu nó sẽ bị 404 dù có cấu hình `management.endpoints` trong yml.

## Cấu hình route trong application.yml

Property prefix đúng cho bản **Server WebMVC** là `spring.cloud.gateway.server.webmvc.routes` (khác với `spring.cloud.gateway.routes` của bản reactive, và khác với `spring.cloud.gateway.mvc.routes` — key này đã bị **deprecated**).

```yaml
spring:
  cloud:
    gateway:
      server:
        webmvc:
          routes:
            - id: user-service
              uri: lb://user-service   # lb:// = load balance qua Eureka
              predicates:
                - Path=/api/v1/auth/**,/api/v1/users/**,/api/v1/admin/users/**

            - id: oauth2
              uri: lb://user-service
              predicates:
                - Path=/oauth2/**

            - id: product-service
              uri: lb://product-service
              predicates:
                - Path=/api/v1/products/**,/api/v1/categories/**,/api/v1/admin/products/**,/api/v1/admin/categories/**

            - id: inventory-service
              uri: lb://inventory-service
              predicates:
                - Path=/api/v1/admin/stock/**

            - id: cart-service
              uri: lb://cart-service
              predicates:
                - Path=/api/v1/cart/**

            - id: order-service
              uri: lb://order-service
              predicates:
                - Path=/api/v1/orders/**,/api/v1/admin/orders/**

            - id: payment-service
              uri: lb://payment-service
              predicates:
                - Path=/api/v1/payments/**,/api/v1/admin/payments/**

            - id: shipping-service
              uri: lb://shipping-service
              predicates:
                - Path=/api/v1/shipping/**,/api/v1/shipments/**,/api/v1/admin/shipments/**

            - id: notification-service
              uri: lb://notification-service
              predicates:
                - Path=/api/v1/notifications/**,/api/v1/admin/notifications/**

            - id: search-service
              uri: lb://search-service
              predicates:
                - Path=/api/v1/search/**

            - id: review-service
              uri: lb://review-service
              predicates:
                - Path=/api/v1/reviews/**,/api/v1/admin/reviews/**

            - id: promotion-service
              uri: lb://promotion-service
              predicates:
                - Path=/api/v1/coupons/**,/api/v1/flash-sales/**,/api/v1/admin/coupons/**,/api/v1/admin/flash-sales/**
```

**Giải thích các thành phần:**

`uri: lb://user-service` — `lb` là load balancer. Gateway hỏi Eureka địa chỉ thật của `user-service`, nếu có nhiều instance thì tự chia tải (round-robin).

`predicates` — điều kiện match request. `Path=` là phổ biến nhất, còn có `Method=`, `Header=`, `Query=`, `After=` (theo thời gian).

**Cần verify khi chạy thật:** cú pháp nhiều pattern trong 1 dòng `Path=/a/**,/b/**` là cách viết chuẩn của bản reactive; bản Server WebMVC dùng chung cơ chế property-route nên nhiều khả năng vẫn hoạt động tương tự, nhưng nếu route không match đúng khi test, thử tách thành nhiều dòng `- Path=/a/**` riêng trong danh sách `predicates` (các predicate trong cùng 1 route là AND, nhưng nhiều giá trị trong cùng 1 `Path=` là OR).

Route chỉ khai báo `uri` + `predicates` ở bước này (chưa có `filters`) — CircuitBreaker và RateLimiter sẽ được thêm vào từng route ở Phần 3 và Phần 4, sau khi hai phần đó được cài đặt.

Không cần khai báo `discovery.locator.enabled: false` — mặc định tính năng auto-route-theo-Eureka này đã tắt, chỉ cần không bật nó lên.

---

# PHẦN 2 — FILTER CHAIN

---

## ⚠️ Ghi chú thiết kế — khác với spec gốc, đã điều chỉnh theo kiến trúc thực tế

Spec gốc giả định Gateway là **nguồn sự thật duy nhất** cho auth: validate JWT 1 lần bằng HMAC-SHA256 shared secret, xoá `Authorization`, forward header `X-User-Id/Email/Role`, service phía sau chỉ việc tin header.

Thực tế project này **không theo mô hình đó** — cả 11 service (bao gồm `user-service`) đã tự validate JWT độc lập bằng `spring-boot-starter-security-oauth2-resource-server` với **RSA public key** (`public.pem`), và mỗi service tự check `hasRole("ADMIN")` ngay trong `SecurityConfig` của nó từ claim `roles` thật trong token. Đây là mô hình zero-trust, mỗi service tự bảo vệ chính nó — không phụ thuộc header do Gateway gắn vào.

Vì vậy Gateway **không đóng vai trò validate duy nhất**, mà chỉ làm thêm 1 lớp **fail-fast** phía trước để chặn sớm request rác/hết hạn, đỡ tải cho service phía sau — service vẫn tự validate lại y như hiện tại. Cụ thể:

- Gateway **verify chữ ký RSA + hạn token** bằng chính `public.pem` (không phải HMAC secret như spec gốc nói)
- Gateway **check thêm Redis blacklist** — vì hiện tại chỉ `user-service` có check blacklist (`JwtBlacklistFilter`), 10 service còn lại **chưa hề check**, nghĩa là token đã logout vẫn dùng được ở các service đó tới khi tự hết hạn. Gateway check blacklist tập trung sẽ vá lỗ hổng này cho toàn bộ hệ thống cùng lúc, không cần sửa từng service.
- Gateway **KHÔNG xoá** header `Authorization`, cũng **KHÔNG cần** inject `X-User-Id/Email/Role` — forward request nguyên vẹn để service tự validate lại như đang chạy.
- Gateway **KHÔNG cần** check role ADMIN — service đã tự làm việc này tốt rồi từ claim `roles` thật, làm thêm ở Gateway là code thừa/trùng logic.

## Thứ tự thực thi filter

```
Request từ client
    ↓
[1] CorsConfig               — xử lý CORS preflight
    ↓
[2] Correlation ID           — sinh X-Request-Id, gắn vào request
    ↓
[3] JWT Fail-Fast Filter     — verify chữ ký RSA + hạn + blacklist Redis
    │                           → sai bất kỳ điều nào → trả 401 ngay, KHÔNG forward đi đâu
    │                           → hợp lệ → forward tiếp, GIỮ NGUYÊN header Authorization
    ↓
[4] RateLimiter (Bucket4j)   — kiểm tra rate limit
    ↓
[5] CircuitBreaker           — bọc call, fallback nếu service chết
    ↓
[6] LoadBalancer             — chọn instance qua Eureka
    ↓
Forward tới service đích (service tự validate lại JWT như hiện tại)
    ↓
[7] ResponseLogging          — log response, thời gian xử lý
    ↓
Response về client
```

---

## Filter 1 — JWT Fail-Fast

Filter này **không phải nguồn sự thật cuối cùng** — nó chỉ chặn sớm request rõ ràng invalid (sai chữ ký, hết hạn, đã logout) trước khi tốn tài nguyên forward xuống service. Service phía sau vẫn tự validate lại đầy đủ như đang chạy hiện tại.

**Cách triển khai (WebMVC):** viết thành 1 `OncePerRequestFilter` (Servlet filter chuẩn của Spring, không phải `GlobalFilter` — khái niệm đó chỉ tồn tại ở bản Gateway reactive) để chạy cho mọi request trước khi tới Gateway router, giống hệt cách `JwtBlacklistFilter` của `user-service` đang được viết.

**Logic:**
```
1. Kiểm tra path có nằm trong whitelist không (public endpoints)
   → Nếu có → bỏ qua, forward luôn

2. Lấy token từ header Authorization: Bearer {token}
   → Không có → trả 401

3. Validate JWT:
   a. Verify signature bằng RSA public key (public.pem — copy từ user-service,
      KHÔNG copy private.pem vì Gateway không cần ký token)
   b. Check expiry (claim exp)
   c. Lấy claim jti, check Redis key "blacklist:{jti}" (cùng format với
      TokenBlacklistService của user-service, trỏ chung Redis instance)
   → Sai bất kỳ điều nào → trả 401

4. Hợp lệ → forward request tiếp, GIỮ NGUYÊN header Authorization gốc
   (không đổi, không xoá — service phía sau cần header thật để tự validate lại)
```

**Cần thêm gì để làm được:**
- Copy file `public.pem` từ `user-service/src/main/resources/keys/` sang `api-gateway/src/main/resources/keys/`
- Dependency `spring-boot-starter-data-redis` trong `pom.xml`, trỏ cùng Redis host/port với các service khác (đọc key `blacklist:{jti}`, không cần ghi)
- `spring-boot-starter-security-oauth2-resource-server` (đã có sẵn trong `pom.xml` của `api-gateway`) để decode/verify JWT bằng RSA public key, giống hệt cách các service khác đang làm

**Whitelist — các path không cần JWT:**
```yaml
gateway:
  whitelist:
    - /api/v1/auth/register
    - /api/v1/auth/login
    - /api/v1/auth/refresh
    - /api/v1/auth/forgot-password
    - /api/v1/auth/reset-password
    - /api/v1/auth/verify-email
    - /oauth2/**
    - /api/v1/products/**          # GET công khai
    - /api/v1/categories/**
    - /api/v1/search/**
    - /api/v1/reviews/product/**   # Xem review công khai
    - /api/v1/flash-sales/active
    - /api/v1/payments/*/ipn       # Webhook từ cổng thanh toán
    - /api/v1/shipping/webhook/**  # Webhook từ nhà vận chuyển
    - /actuator/health
    - /swagger-ui/**
    - /v3/api-docs/**
```

**Lưu ý về webhook:** Webhook endpoint phải public (cổng thanh toán không có JWT), nhưng được bảo vệ bằng signature verification ở chính service đó.

**Lưu ý về Spring Security:** `api-gateway` đã có sẵn `spring-boot-starter-security` trong `pom.xml` nhưng **chưa có `SecurityConfig`**. Nếu không tạo `SecurityConfig` với `permitAll()` phù hợp, mặc định Spring Security sẽ khoá TẤT CẢ endpoint bằng basic-auth (kể cả `/actuator/**`). `SecurityConfig` nên được viết cùng lúc với `JWT Fail-Fast Filter` ở phần này.

---

## Filter 2 — Request Logging

Log mỗi request để debug và monitor.

```
Log format:
  [requestId] METHOD path — userId — status — duration(ms)

Ví dụ:
  [a3f2b1] POST /api/v1/orders — user-uuid-1 — 201 — 245ms
  [c8d4e2] GET  /api/v1/products — anonymous — 200 — 18ms
```

**Correlation ID:** Gateway sinh `X-Request-Id` (UUID) cho mỗi request và forward xuống service. Nhờ đó trace được 1 request đi qua nhiều service (kết hợp với Zipkin).

---

## Filter 3 — CORS

**⚠️ Khác với bản reactive:** `spring.cloud.gateway.globalcors` **không được hỗ trợ đầy đủ** ở Gateway Server WebMVC (đây vẫn là tính năng đang được đề xuất bổ sung, chưa có sẵn). Vì project đã có `spring-boot-starter-security`, cách đúng là cấu hình CORS theo chuẩn Spring Security/Spring MVC — khai báo 1 `CorsConfigurationSource` bean và gọi `.cors(...)` trong `SecurityFilterChain`:

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:3000", "https://shopnow.com"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```
rồi trong `SecurityFilterChain`: `http.cors(cors -> cors.configurationSource(corsConfigurationSource()))`.

**Lưu ý:** Config CORS ở Gateway thôi, các service phía sau **không cần** config CORS nữa (vì client không gọi trực tiếp).

---

# PHẦN 3 — RATE LIMITING

---

## Cách hoạt động — Bucket4j (không phải RequestRateLimiter kiểu Redis)

**⚠️ Khác với bản reactive:** filter `RequestRateLimiter` (dùng Redis token-bucket, `redis-rate-limiter.replenishRate`/`burstCapacity`) **chỉ tồn tại ở bản Gateway reactive**. Ở Gateway Server WebMVC, cơ chế rate limit built-in là filter `RateLimiter`, backed bởi thư viện **Bucket4j**, với model tham số khác (`capacity` + `period` thay vì `replenishRate`/`burstCapacity`).

```
Mỗi key (IP/userId) có 1 bucket chứa "capacity" token.
Bucket được nạp lại đầy sau mỗi "period".

Mỗi request tiêu 1 token (mặc định).
  → Còn token → cho qua
  → Hết token → trả 429 Too Many Requests
```

**Khác biệt quan trọng cần biết:** mặc định Bucket4j lưu bucket **trong bộ nhớ của chính instance Gateway đó** (không tự động chia sẻ qua Redis như bản reactive). Vì hiện tại chỉ chạy 1 instance `api-gateway` nên không ảnh hưởng gì; nếu sau này scale nhiều instance Gateway thì cần cấu hình thêm Bucket4j distributed backend (Redis/Hazelcast) mới đảm bảo rate limit đúng khi có nhiều instance.

## Cấu hình rate limit theo từng loại endpoint

| Loại endpoint | Capacity | Period | Key resolver | Lý do |
|---|---|---|---|---|
| Auth (login, register) | 5 | 1 phút | IP | Chống brute force |
| Flash sale purchase | 5 | 10 giây | userId | Chống bot cướp hàng |
| Search | 40 | 1 phút | IP | Search nhiều là bình thường |
| Product browse | 60 | 1 phút | IP | Duyệt sản phẩm cần thoáng |
| Order create | 10 | 1 phút | userId | Chống spam đơn |
| Default (còn lại) | 20 | 1 phút | userId hoặc IP | Mặc định |

*(Bảng số liệu trên là gợi ý ban đầu, tinh chỉnh lại khi có traffic thật.)*

## Ví dụ khai báo filter RateLimiter cho 1 route

```yaml
- id: flash-sale
  uri: lb://promotion-service
  predicates:
    - Path=/api/v1/flash-sales/**
  filters:
    - name: RateLimiter
      args:
        capacity: 5
        period: 10s
```

Việc set `keyResolver` (theo IP hay theo userId) cho filter `RateLimiter` cần viết bằng Java (`Bucket4jFilterFunctions.rateLimit(c -> c.setKeyResolver(...))`) khi định nghĩa route bằng `RouterFunction` — property-based YAML shortcut ở trên chưa chắc hỗ trợ custom key resolver phức tạp, cần verify khi cài đặt thật; nếu không hỗ trợ, viết route đó bằng Java Routes API (`GatewayRouterFunctions.route(...)`) thay vì YAML.

## Key Resolver — xác định giới hạn theo cái gì

**Theo IP** (cho endpoint public, chưa login):
```
Lấy IP từ header X-Forwarded-For (nếu sau proxy) hoặc remote address
```

**Theo userId** (cho endpoint đã login):
```
Gateway không có header X-User-Id (đã bỏ ở Phần 2), nên lấy trực tiếp claim "userId"
từ JWT thật trong header Authorization (JWT Fail-Fast filter đã decode token rồi,
tái dùng luôn object Jwt đó thay vì decode lại lần nữa)
Nếu chưa login (không có Authorization) → fallback về IP
```

**Theo API key** (cho 3rd party):
```
Lấy từ header X-API-Key
```

## Response khi bị rate limit

```
HTTP 429 Too Many Requests

Headers:
  X-RateLimit-Remaining: 0   # tên header mặc định của Bucket4jFilterFunctions

Body:
{
  "success": false,
  "code":    "RATE_LIMIT_EXCEEDED",
  "message": "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau."
}
```
Body JSON tuỳ chỉnh ở trên cần override lại behavior mặc định của filter (viết custom response, không phải hành vi có sẵn) — verify cách override khi cài đặt thật.

---

# PHẦN 4 — CIRCUIT BREAKER

---

## Vấn đề cần giải quyết

```
Nếu Payment Service bị chậm hoặc chết:
  → Mọi request đến payment đều timeout (30s)
  → Thread của Gateway bị giữ chờ
  → Thread pool cạn kiệt
  → Gateway sập luôn → toàn hệ thống chết 💀

→ Đây gọi là cascading failure (lỗi lan truyền)
```

---

## Circuit Breaker — 3 trạng thái

```
CLOSED (bình thường)
  → Request đi qua bình thường
  → Đếm tỉ lệ lỗi
  → Nếu tỉ lệ lỗi > ngưỡng (VD 50%) → chuyển OPEN

OPEN (ngắt mạch)
  → KHÔNG gọi service nữa, trả fallback ngay lập tức
  → Không tốn thời gian chờ timeout
  → Sau waitDuration (VD 30s) → chuyển HALF_OPEN

HALF_OPEN (thử lại)
  → Cho phép vài request thử (VD 3 request)
  → Nếu thành công → về CLOSED (service đã hồi phục)
  → Nếu vẫn lỗi → về OPEN (chờ tiếp)
```

---

## Dependency cần có

Filter `CircuitBreaker` của Gateway (kể cả bản Server WebMVC) cần dependency:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
</dependency>
```
(Đúng, tên dependency vẫn có chữ `reactor` dù project chạy nền MVC — đây là theo tài liệu chính thức của Spring Cloud Gateway Server WebMVC, verify lại khi cài vì đây là API khá mới.)

## Cấu hình Resilience4j

Phần cấu hình `resilience4j.*` bên dưới là cấu hình chuẩn của thư viện Resilience4j, không phụ thuộc vào Gateway chạy reactive hay MVC — giữ nguyên như spec gốc:

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 20              # Đánh giá dựa trên 20 request gần nhất
        minimumNumberOfCalls: 10           # Cần ít nhất 10 call mới đánh giá
        failureRateThreshold: 50           # Lỗi > 50% → OPEN
        slowCallRateThreshold: 60          # Chậm > 60% → OPEN
        slowCallDurationThreshold: 3s      # Coi là chậm nếu > 3s
        waitDurationInOpenState: 30s       # OPEN 30s rồi thử lại
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        registerHealthIndicator: true

    instances:
      userServiceCB:
        baseConfig: default
      productServiceCB:
        baseConfig: default
      orderServiceCB:
        baseConfig: default
        failureRateThreshold: 40           # Order quan trọng, nhạy hơn
      paymentServiceCB:
        baseConfig: default
        waitDurationInOpenState: 60s       # Payment cần thời gian hồi phục lâu hơn

  timelimiter:
    configs:
      default:
        timeoutDuration: 5s                # Timeout 5s
        cancelRunningFuture: true
```

Ví dụ gắn filter `CircuitBreaker` vào 1 route:
```yaml
- id: user-service
  uri: lb://user-service
  predicates:
    - Path=/api/v1/auth/**,/api/v1/users/**,/api/v1/admin/users/**
  filters:
    - name: CircuitBreaker
      args:
        name: userServiceCB
        fallbackUri: forward:/fallback/user
```

---

## Fallback Response

Khi circuit OPEN, Gateway trả về fallback thay vì lỗi trống.

**Fallback cho Product Service (có thể degrade):**
```json
{
  "success": false,
  "code":    "SERVICE_UNAVAILABLE",
  "message": "Hệ thống đang bận, vui lòng thử lại sau ít phút.",
  "data":    null,
  "fallback": true
}
```

**Fallback thông minh hơn — trả cached data:**
```
Với Product Service: nếu có cache Redis → trả data cũ
  → User vẫn duyệt được sản phẩm (dù có thể hơi cũ)
  → Trải nghiệm tốt hơn là màn hình lỗi

Với Order/Payment: KHÔNG fallback bằng cache
  → Không được tạo đơn giả khi service chết
  → Phải báo lỗi rõ ràng để user thử lại
```

---

## Bảng chiến lược fallback theo service

| Service | Fallback strategy | Lý do |
|---|---|---|
| Product | Trả cache Redis (data cũ) | Đọc, chấp nhận cũ |
| Search | Trả rỗng + gợi ý browse category | Search fail không chặn mua hàng |
| Recommendation | Trả trending (rule-based) | Không quan trọng, degrade được — service chưa build |
| Review | Trả rỗng | Không chặn xem sản phẩm |
| Cart | Báo lỗi rõ ràng | Không được sai data giỏ hàng |
| Order | Báo lỗi rõ ràng | Tuyệt đối không fake |
| Payment | Báo lỗi rõ ràng | Liên quan tiền, không đùa |

**Nguyên tắc:** Service **đọc** thì degrade được (trả cache/rỗng), service **ghi** thì phải báo lỗi thật.

---

# PHẦN 5 — LOAD BALANCING & SERVICE DISCOVERY

---

## Cách hoạt động với Eureka

```
1. Mỗi service khi khởi động → đăng ký với Eureka
   "Tôi là product-service, địa chỉ 192.168.1.5:8082"

2. Gateway hỏi Eureka: "product-service ở đâu?"
   → Eureka trả danh sách tất cả instance đang sống

3. Gateway chọn 1 instance (round-robin mặc định, qua spring-cloud-starter-loadbalancer)
   → Forward request tới đó

4. Nếu 1 instance chết → Eureka loại khỏi danh sách sau vài giây
   → Gateway không route tới nữa
```

**Cấu hình (đã áp dụng cho toàn bộ service trong project, bao gồm cả `discovery-server`):**
```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

`register-with-eureka` và `fetch-registry` không cần khai báo tường minh — mặc định đều là `true` cho mọi client trừ chính `discovery-server` (nơi 2 giá trị này phải set `false` để tránh nó tự đăng ký chính nó).

---

# PHẦN 6 — TỔNG HỢP CẤU HÌNH

## application.yml đầy đủ (khung sườn, đã cập nhật đúng property Server WebMVC)

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway

  cloud:
    gateway:
      server:
        webmvc:
          routes:
            # ... (xem Phần 1, gắn thêm filters CircuitBreaker/RateLimiter theo Phần 3-4)

  # Verify JWT bằng RSA public key — copy public.pem từ user-service (Phần 2)
  security:
    oauth2:
      resourceserver:
        jwt:
          public-key-location: classpath:keys/public.pem

  # Chỉ cần để đọc key blacklist:{jti} (Phần 2), trỏ chung Redis với các service khác
  data:
    redis:
      host: localhost
      port: 6379

# Gateway whitelist
gateway:
  whitelist:
    - /api/v1/auth/**
    - /oauth2/**
    - /api/v1/products/**
    # ... (xem Phần 2)

# Circuit breaker
resilience4j:
  circuitbreaker:
    # ... (xem Phần 4)

# Eureka
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

# Actuator — CẦN dependency spring-boot-starter-actuator, không có thì các key dưới đây vô nghĩa
management:
  endpoints:
    web:
      exposure:
        include: health,gateway,circuitbreakers,metrics
  endpoint:
    health:
      show-details: always

# Logging
logging:
  level:
    org.springframework.cloud.gateway: INFO
    com.ice.apigateway: DEBUG
```

**Những gì đã bị bỏ so với bản gốc trước đây (không áp dụng được cho Server WebMVC):**
- `spring.cloud.gateway.globalcors` → chuyển sang `CorsConfigurationSource` bean (Phần 2)
- `spring.data.redis` để làm rate-limiter store → không cần nữa vì đổi sang Bucket4j in-memory (Phần 3); Redis vẫn cần cho JWT blacklist (Phần 2) nếu áp dụng, đó là việc khác không liên quan tới rate limiting
- `default-filters` dạng `DedupeResponseHeader=...` / `Retry` — các shortcut filter này thuộc bản reactive; nếu Server WebMVC có filter tương đương thì tên/args có thể khác, cần tra `org.springframework.cloud.gateway.server.mvc.filter.*FilterFunctions` khi cần dùng, chưa đưa vào spec vì chưa verify.

---

# PHẦN 7 — ENDPOINTS CỦA CHÍNH GATEWAY

| Method | Endpoint | Mục đích |
|--------|----------|----------|
| GET | /actuator/health | Health check |
| GET | /actuator/gateway/routes | Xem tất cả route đang active |
| GET | /actuator/circuitbreakers | Trạng thái circuit breaker |
| GET | /fallback/{service} | Fallback endpoint |

Toàn bộ endpoint `/actuator/**` cần dependency `spring-boot-starter-actuator` (Phần 1) mới tồn tại, và cần `SecurityConfig` permit chúng (Phần 2) nếu không sẽ bị Spring Security chặn bằng basic-auth trước khi tới được actuator.

---

# PHẦN 8 — ĐIỂM PHỎNG VẤN

```
1. Tại sao cần API Gateway?
   → Client chỉ cần biết 1 địa chỉ
   → Auth/rate limit/CORS xử lý tập trung, không lặp ở mỗi service
   → Service nội bộ không expose ra internet

2. Validate JWT ở Gateway hay ở từng service?
   → Project này: CẢ HAI (defense-in-depth), không phải chỉ Gateway
   → Gateway verify fail-fast (chữ ký RSA + hạn + blacklist Redis) để chặn sớm request rác
   → Service vẫn tự validate lại đầy đủ bằng chính JWT thật (Authorization header được
     forward nguyên vẹn, không bị Gateway xoá hay thay bằng header khác)
   → Lý do không dùng mô hình "Gateway validate 1 lần, service tin header": project đã
     có sẵn 11 service tự validate độc lập bằng RSA, đổi sang tin header sẽ phải sửa
     lại toàn bộ SecurityConfig của từng service — không đáng đánh đổi

3. Circuit Breaker giải quyết vấn đề gì?
   → Cascading failure: 1 service chết kéo sập toàn hệ thống
   → OPEN ngắt mạch → trả fallback ngay, không chờ timeout
   → Thread pool không bị cạn

4. Rate limiting dùng thuật toán gì?
   → Bucket4j (Server WebMVC dùng Bucket4j, không phải RequestRateLimiter+Redis như bản reactive)
   → capacity = sức chứa bucket, period = thời gian nạp lại đầy
   → Mặc định lưu state trong bộ nhớ của từng instance Gateway (không tự share qua Redis)

5. Tại sao chỉ retry GET, không retry POST?
   → GET idempotent — gọi nhiều lần kết quả như nhau
   → POST không idempotent — retry có thể tạo 2 đơn hàng
```
