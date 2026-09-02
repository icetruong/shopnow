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
- Gateway validate JWT **một lần duy nhất**, forward user context qua header
- Các service nội bộ **không expose ra internet** (chỉ Gateway public)

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
| recommendation-service | `/api/v1/recommendations/**` | recommendation-service | 8092 | |

**Lưu ý quan trọng:** Endpoint `/internal/**` của các service **KHÔNG được route qua Gateway** — chúng chỉ dùng cho service-to-service call trong mạng nội bộ.

---

## Cấu hình route trong application.yml

```yaml
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: false          # Tắt auto-discovery, định nghĩa route thủ công cho rõ ràng
      
      routes:
        # ============ USER SERVICE ============
        - id: user-service
          uri: lb://user-service   # lb:// = load balance qua Eureka
          predicates:
            - Path=/api/v1/auth/**, /api/v1/users/**, /api/v1/admin/users/**
          filters:
            - name: CircuitBreaker
              args:
                name: userServiceCB
                fallbackUri: forward:/fallback/user
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
                key-resolver: "#{@ipKeyResolver}"

        # ============ PRODUCT SERVICE ============
        - id: product-service
          uri: lb://product-service
          predicates:
            - Path=/api/v1/products/**, /api/v1/categories/**, /api/v1/admin/products/**
          filters:
            - name: CircuitBreaker
              args:
                name: productServiceCB
                fallbackUri: forward:/fallback/product

        # ============ ORDER SERVICE ============
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/v1/orders/**, /api/v1/admin/orders/**
          filters:
            - name: CircuitBreaker
              args:
                name: orderServiceCB
                fallbackUri: forward:/fallback/order

        # ============ FLASH SALE (rate limit chặt) ============
        - id: flash-sale
          uri: lb://promotion-service
          predicates:
            - Path=/api/v1/flash-sales/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 5
                redis-rate-limiter.burstCapacity: 10
                key-resolver: "#{@userKeyResolver}"
```

**Giải thích các thành phần:**

`uri: lb://user-service` — `lb` là load balancer. Gateway hỏi Eureka địa chỉ thật của `user-service`, nếu có nhiều instance thì tự chia tải (round-robin).

`predicates` — điều kiện match request. `Path=` là phổ biến nhất, còn có `Method=`, `Header=`, `Query=`, `After=` (theo thời gian).

`filters` — xử lý request/response trước và sau khi forward.

---

# PHẦN 2 — FILTER CHAIN

---

## Thứ tự thực thi filter

```
Request từ client
    ↓
[1] CorsFilter              — xử lý CORS preflight
    ↓
[2] JwtAuthenticationFilter  — validate JWT, inject X-User-* header
    ↓
[3] RequestRateLimiter       — kiểm tra rate limit
    ↓
[4] CircuitBreaker           — bọc call, fallback nếu service chết
    ↓
[5] LoadBalancer             — chọn instance qua Eureka
    ↓
Forward tới service đích
    ↓
[6] ResponseLogging          — log response, thời gian xử lý
    ↓
Response về client
```

---

## Filter 1 — JWT Authentication (Global Filter)

Đây là filter quan trọng nhất. Nó validate JWT một lần tại Gateway, các service phía sau chỉ cần đọc header.

**Logic:**
```
1. Kiểm tra path có nằm trong whitelist không (public endpoints)
   → Nếu có → bỏ qua, forward luôn

2. Lấy token từ header Authorization: Bearer {token}
   → Không có → trả 401

3. Validate JWT:
   a. Verify signature (HMAC-SHA256 với shared secret)
   b. Check expiry
   c. Check jti có trong Redis blacklist không (logout)
   → Sai bất kỳ điều nào → trả 401

4. Extract claims từ token: userId, email, role

5. Inject vào header forward xuống service:
   X-User-Id:    {userId}
   X-User-Email: {email}
   X-User-Role:  {role}

6. XÓA header Authorization gốc (service không cần)
```

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

---

## Filter 2 — Role-based Authorization

Sau khi có `X-User-Role`, kiểm tra quyền truy cập path admin.

```
Nếu path bắt đầu bằng /api/v1/admin/**
  → Yêu cầu X-User-Role = ROLE_ADMIN
  → Không đúng → trả 403 Forbidden
```

---

## Filter 3 — Request Logging

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

## Filter 4 — CORS

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins:
              - "http://localhost:3000"
              - "https://shopnow.com"
            allowedMethods:
              - GET
              - POST
              - PUT
              - PATCH
              - DELETE
              - OPTIONS
            allowedHeaders: "*"
            allowCredentials: true
            maxAge: 3600
```

**Lưu ý:** Config CORS ở Gateway thôi, các service phía sau **không cần** config CORS nữa (vì client không gọi trực tiếp).

---

# PHẦN 3 — RATE LIMITING

---

## Cách hoạt động — Token Bucket algorithm

```
Mỗi user/IP có 1 "bucket" chứa token:
  - replenishRate: số token được nạp lại mỗi giây
  - burstCapacity: sức chứa tối đa của bucket

Mỗi request tiêu 1 token.
  → Còn token → cho qua
  → Hết token → trả 429 Too Many Requests

Ví dụ: replenishRate=10, burstCapacity=20
  → Bình thường: 10 request/giây
  → Cho phép burst: tối đa 20 request liên tiếp (nếu bucket đầy)
  → Sau burst phải chờ nạp lại
```

Spring Cloud Gateway dùng **Redis** để lưu bucket state (chia sẻ giữa nhiều instance Gateway).

---

## Cấu hình rate limit theo từng loại endpoint

| Loại endpoint | replenishRate | burstCapacity | Key resolver | Lý do |
|---|---|---|---|---|
| Auth (login, register) | 3 | 5 | IP | Chống brute force |
| Flash sale purchase | 5 | 10 | userId | Chống bot cướp hàng |
| Search | 20 | 40 | IP | Search nhiều là bình thường |
| Product browse | 30 | 60 | IP | Duyệt sản phẩm cần thoáng |
| Order create | 5 | 10 | userId | Chống spam đơn |
| Default (còn lại) | 10 | 20 | userId hoặc IP | Mặc định |

---

## Key Resolver — xác định giới hạn theo cái gì

**Theo IP** (cho endpoint public, chưa login):
```
Lấy IP từ header X-Forwarded-For (nếu sau proxy) hoặc remote address
Key: rate_limit:ip:{ipAddress}
```

**Theo userId** (cho endpoint đã login):
```
Lấy X-User-Id đã được JWT filter inject
Key: rate_limit:user:{userId}
Nếu chưa login → fallback về IP
```

**Theo API key** (cho 3rd party):
```
Lấy từ header X-API-Key
Key: rate_limit:apikey:{apiKey}
```

---

## Response khi bị rate limit

```
HTTP 429 Too Many Requests

Headers:
  X-RateLimit-Limit:     20
  X-RateLimit-Remaining: 0
  X-RateLimit-Reset:     1705318800

Body:
{
  "success": false,
  "code":    "RATE_LIMIT_EXCEEDED",
  "message": "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau."
}
```

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

## Cấu hình Resilience4j

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
| Recommendation | Trả trending (rule-based) | Không quan trọng, degrade được |
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

3. Gateway chọn 1 instance (round-robin mặc định)
   → Forward request tới đó

4. Nếu 1 instance chết → Eureka loại khỏi danh sách sau vài giây
   → Gateway không route tới nữa
```

**Cấu hình:**
```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true
    lease-renewal-interval-in-seconds: 10
```

---

# PHẦN 6 — TỔNG HỢP CẤU HÌNH

## application.yml đầy đủ (khung sườn)

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway

  # Redis (cho rate limiting + JWT blacklist)
  data:
    redis:
      host: localhost
      port: 6379

  cloud:
    gateway:
      # CORS
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "http://localhost:3000"
            allowedMethods: "*"
            allowedHeaders: "*"
            allowCredentials: true

      # Default filters (áp dụng cho MỌI route)
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Origin
        - name: Retry
          args:
            retries: 2
            statuses: BAD_GATEWAY, SERVICE_UNAVAILABLE
            methods: GET                 # Chỉ retry GET (idempotent)
            backoff:
              firstBackoff: 100ms
              maxBackoff: 500ms

      routes:
        # ... (xem phần 1)

# JWT (dùng chung secret với User Service)
jwt:
  secret: ${JWT_SECRET}

# Gateway whitelist
gateway:
  whitelist:
    - /api/v1/auth/**
    - /oauth2/**
    - /api/v1/products/**
    # ... (xem phần 2)

# Circuit breaker
resilience4j:
  circuitbreaker:
    # ... (xem phần 4)

# Eureka
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health, gateway, circuitbreakers, metrics, prometheus
  endpoint:
    health:
      show-details: always

# Logging
logging:
  level:
    org.springframework.cloud.gateway: INFO
    com.shopnow.gateway: DEBUG
```

---

# PHẦN 7 — ENDPOINTS CỦA CHÍNH GATEWAY

| Method | Endpoint | Mục đích |
|--------|----------|----------|
| GET | /actuator/health | Health check |
| GET | /actuator/gateway/routes | Xem tất cả route đang active |
| GET | /actuator/circuitbreakers | Trạng thái circuit breaker |
| GET | /actuator/prometheus | Metrics cho Prometheus |
| GET | /fallback/{service} | Fallback endpoint |

---

# PHẦN 8 — ĐIỂM PHỎNG VẤN

```
1. Tại sao cần API Gateway?
   → Client chỉ cần biết 1 địa chỉ
   → Auth/rate limit/CORS xử lý tập trung, không lặp ở mỗi service
   → Service nội bộ không expose ra internet

2. Validate JWT ở Gateway hay ở từng service?
   → Ở Gateway: validate 1 lần, forward X-User-* header
   → Service tin header vì chỉ Gateway mới truy cập được (mạng nội bộ)
   → Tránh mỗi service phải parse JWT lại (tốn CPU)

3. Circuit Breaker giải quyết vấn đề gì?
   → Cascading failure: 1 service chết kéo sập toàn hệ thống
   → OPEN ngắt mạch → trả fallback ngay, không chờ timeout
   → Thread pool không bị cạn

4. Rate limiting dùng thuật toán gì?
   → Token Bucket (Spring Cloud Gateway mặc định)
   → replenishRate = tốc độ nạp, burstCapacity = sức chứa
   → State lưu Redis để share giữa nhiều instance Gateway

5. Tại sao chỉ retry GET, không retry POST?
   → GET idempotent — gọi nhiều lần kết quả như nhau
   → POST không idempotent — retry có thể tạo 2 đơn hàng
```