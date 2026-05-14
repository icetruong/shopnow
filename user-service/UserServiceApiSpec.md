# User Service — API Specification & Database Schema

---

## Base URL
```
http://localhost:8081/api/v1
```

## Auth header (các endpoint cần login)
```
Authorization: Bearer {accessToken}
```

---

# PHẦN 1 — API ENDPOINTS

---

## 1. AUTH — Đăng ký / Đăng nhập

---

### POST /auth/register
Đăng ký tài khoản mới bằng email + password.

**Request Body**
```json
{
  "email":     "user@example.com",
  "password":  "Password123!",
  "fullName":  "Nguyen Van A",
  "phone":     "0901234567"
}
```

**Validation**
- `email`: không được trống, đúng định dạng email, chưa tồn tại trong DB
- `password`: 8–32 ký tự, có ít nhất 1 chữ hoa, 1 số, 1 ký tự đặc biệt
- `fullName`: không được trống, 2–50 ký tự
- `phone`: đúng định dạng số VN (tùy chọn)

**Response 201**
```json
{
  "success": true,
  "message": "Đăng ký thành công. Vui lòng kiểm tra email để xác thực tài khoản.",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email":  "user@example.com"
  }
}
```

**Response 409** — email đã tồn tại
```json
{
  "success": false,
  "code":    "EMAIL_ALREADY_EXISTS",
  "message": "Email này đã được đăng ký."
}
```

**Side effect:** Publish Kafka event `user.registered`

---

### POST /auth/login
Đăng nhập bằng email + password.

**Request Body**
```json
{
  "email":    "user@example.com",
  "password": "Password123!"
}
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "accessToken":  "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType":    "Bearer",
    "expiresIn":    900
  }
}
```

**Response 401** — sai email hoặc password
```json
{
  "success": false,
  "code":    "INVALID_CREDENTIALS",
  "message": "Email hoặc mật khẩu không đúng."
}
```

**Response 403** — tài khoản bị khoá
```json
{
  "success": false,
  "code":    "ACCOUNT_DISABLED",
  "message": "Tài khoản của bạn đã bị khoá. Vui lòng liên hệ hỗ trợ."
}
```

---

### POST /auth/refresh
Lấy access token mới bằng refresh token (Refresh Token Rotation — refresh token cũ bị hủy, cấp token mới).

**Request Body**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "accessToken":  "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType":    "Bearer",
    "expiresIn":    900
  }
}
```

**Response 401** — refresh token hết hạn hoặc đã bị dùng
```json
{
  "success": false,
  "code":    "REFRESH_TOKEN_INVALID",
  "message": "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại."
}
```

---

### POST /auth/logout
Đăng xuất — blacklist access token hiện tại và xóa refresh token.

**Header:** `Authorization: Bearer {accessToken}` *(bắt buộc)*

**Request Body**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Đăng xuất thành công."
}
```

**Side effect:** Lưu `jti` của access token vào Redis blacklist với TTL = thời gian còn lại của token.

---

### POST /auth/forgot-password
Gửi email đặt lại mật khẩu.

**Request Body**
```json
{
  "email": "user@example.com"
}
```

**Response 200** **
```json
{
  "success": true,
  "message": "Email có thể đổi mật khẩu."
  "data": {
    "token": "reset-token-uuid",
    "expiresIn": 900
  }
}
```

**Side effect:** Tạo reset token (UUID, TTL 15 phút) lưu Redis, publish Kafka event `user.password_reset_requested`

---

### POST /auth/reset-password
Đặt lại mật khẩu bằng reset token.

**Request Body**
```json
{
  "token":       "reset-token-uuid",
  "newPassword": "NewPassword456!"
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập lại."
}
```

**Response 400** — token hết hạn hoặc không hợp lệ
```json
{
  "success": false,
  "code":    "RESET_TOKEN_INVALID",
  "message": "Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn."
}
```

---

### GET /auth/verify-email?token={token}
Xác thực email sau khi đăng ký.

**Query Param:** `token` — UUID gửi qua email

**Response 200**
```json
{
  "success": true,
  "message": "Email đã được xác thực thành công."
}
```

**Response 400**
```json
{
  "success": false,
  "code":    "VERIFICATION_TOKEN_INVALID",
  "message": "Link xác thực không hợp lệ hoặc đã hết hạn."
}
```

---

## 2. OAUTH2 — Đăng nhập mạng xã hội

---

### GET /oauth2/authorize/{provider}
Redirect sang trang đăng nhập Google / Facebook. Spring Security tự xử lý, không cần viết controller.

- `provider`: `google` hoặc `facebook`

**Flow:** Client gọi endpoint này → redirect sang Google/Facebook → sau khi user đồng ý → Google/Facebook redirect về callback.

---

### GET /oauth2/callback/{provider}
Callback URL sau khi Google/Facebook xác thực. Spring Security tự xử lý.

**Kết quả thành công:** Redirect về frontend kèm token trong URL fragment
```
http://localhost:3000/oauth2/redirect#accessToken=xxx&refreshToken=yyy
```

**Kết quả thất bại:** Redirect về frontend kèm lỗi
```
http://localhost:3000/oauth2/redirect?error=oauth2_error
```

---

## 3. USER PROFILE — Thông tin cá nhân

---

### GET /users/me
Lấy thông tin profile của user đang đăng nhập.

**Header:** `Authorization: Bearer {accessToken}` *(bắt buộc)*

**Response 200**
```json
{
  "success": true,
  "data": {
    "userId":          "550e8400-e29b-41d4-a716-446655440000",
    "email":           "user@example.com",
    "fullName":        "Nguyen Van A",
    "phone":           "0901234567",
    "avatarUrl":       "https://storage.shopnow.com/avatars/abc.jpg",
    "role":            "ROLE_USER",
    "provider":        "LOCAL",
    "emailVerified":   true,
    "isActive":        true,
    "createdAt":       "2024-01-15T10:30:00Z"
  }
}
```

---

### PUT /users/me
Cập nhật thông tin cá nhân.

**Header:** `Authorization: Bearer {accessToken}` *(bắt buộc)*

**Request Body** *(tất cả các field đều optional, chỉ gửi field muốn đổi)*
```json
{
  "fullName": "Nguyen Van B",
  "phone":    "0909999999"
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Cập nhật thông tin thành công.",
  "data": {
    "userId":    "550e8400-e29b-41d4-a716-446655440000",
    "fullName":  "Nguyen Van B",
    "phone":     "0909999999",
    "updatedAt": "2024-01-15T11:00:00Z"
  }
}
```

---

### POST /users/me/avatar
Upload ảnh đại diện.

**Header:** `Authorization: Bearer {accessToken}` *(bắt buộc)*

**Request:** `multipart/form-data`
```
file: [binary image]
```

**Validation:** JPG/PNG/WEBP, tối đa 5MB

**Response 200**
```json
{
  "success": true,
  "data": {
    "avatarUrl": "https://storage.shopnow.com/avatars/550e8400.jpg"
  }
}
```

---

### PUT /users/me/change-password
Đổi mật khẩu (chỉ dành cho tài khoản LOCAL, không áp dụng cho OAuth2).

**Header:** `Authorization: Bearer {accessToken}` *(bắt buộc)*

**Request Body**
```json
{
  "currentPassword": "Password123!",
  "newPassword":     "NewPassword456!",
  "confirmPassword": "NewPassword456!"
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Đổi mật khẩu thành công. Vui lòng đăng nhập lại."
}
```

**Response 400** — mật khẩu hiện tại sai
```json
{
  "success": false,
  "code":    "WRONG_PASSWORD",
  "message": "Mật khẩu hiện tại không đúng."
}
```

**Response 400** — tài khoản OAuth2
```json
{
  "success": false,
  "code":    "OAUTH2_ACCOUNT_NO_PASSWORD",
  "message": "Tài khoản đăng nhập qua Google/Facebook không thể đổi mật khẩu."
}
```

---

## 4. ADDRESS — Địa chỉ giao hàng

---

### GET /users/me/addresses
Lấy danh sách địa chỉ của user.

**Response 200**
```json
{
  "success": true,
  "data": [
    {
      "addressId":   "addr-uuid-1",
      "fullName":    "Nguyen Van A",
      "phone":       "0901234567",
      "province":    "TP. Hồ Chí Minh",
      "district":    "Quận 1",
      "ward":        "Phường Bến Nghé",
      "streetDetail":"123 Đường Lê Lợi",
      "isDefault":   true
    },
    {
      "addressId":   "addr-uuid-2",
      "fullName":    "Nguyen Van A",
      "phone":       "0901234567",
      "province":    "Hà Nội",
      "district":    "Quận Hoàn Kiếm",
      "ward":        "Phường Hàng Bài",
      "streetDetail":"456 Đường Đinh Tiên Hoàng",
      "isDefault":   false
    }
  ]
}
```

---

### POST /users/me/addresses
Thêm địa chỉ mới.

**Request Body**
```json
{
  "province":    "TP. Hồ Chí Minh",
  "district":    "Quận 1",
  "ward":        "Phường Bến Nghé",
  "streetDetail":"123 Đường Lê Lợi",
  "isDefault":   false
}
```

**Response 201**
```json
{
  "success": true,
  "data": {
    "addressId": "addr-uuid-3"
  }
}
```

**Validation:** Mỗi user tối đa 5 địa chỉ.

---

### PUT /users/me/addresses/{addressId}
Cập nhật địa chỉ.

**Request Body:** Giống POST, các field optional.

**Response 200**
```json
{
  "success": true,
  "message": "Cập nhật địa chỉ thành công."
}
```

---

### DELETE /users/me/addresses/{addressId}
Xóa địa chỉ.

**Response 200**
```json
{
  "success": true,
  "message": "Đã xóa địa chỉ."
}
```

---

### PATCH /users/me/addresses/{addressId}/default
Đặt làm địa chỉ mặc định.

**Response 200**
```json
{
  "success": true,
  "message": "Đã đặt làm địa chỉ mặc định."
}
```

---

## 5. INTERNAL — Dành cho các service khác gọi

> Các endpoint này chỉ accessible trong internal network, được bảo vệ bằng header `X-Internal-Token`.

---

### GET /internal/users/{userId}
Order Service gọi để lấy địa chỉ giao hàng khi tạo đơn.

**Header:** `X-Internal-Token: {sharedSecret}`

**Response 200**
```json
{
  "userId":   "550e8400-e29b-41d4-a716-446655440000",
  "fullName": "Nguyen Van A",
  "email":    "user@example.com",
  "phone":    "0901234567",
  "defaultAddress": {
    "fullName":    "Nguyen Van A",
    "phone":       "0901234567",
    "province":    "TP. Hồ Chí Minh",
    "district":    "Quận 1",
    "ward":        "Phường Bến Nghé",
    "streetDetail":"123 Đường Lê Lợi"
  }
}
```

---

### GET /internal/users/{userId}/exists
Kiểm tra user có tồn tại không.

**Response 200**
```json
{
  "exists": true,
  "isActive": true
}
```

---

## 6. ADMIN — Quản trị (chỉ ROLE_ADMIN)

---

### GET /admin/users
Lấy danh sách tất cả user, có phân trang và filter.

**Header:** `Authorization: Bearer {accessToken}` *(ROLE_ADMIN)*

**Query Params**
```
page     = 0          (default 0)
size     = 20         (default 20, max 100)
sort     = createdAt  (createdAt | fullName | email)
direction= DESC       (ASC | DESC)
keyword  = "nguyen"   (tìm theo email hoặc fullName)
role     = ROLE_USER  (filter theo role)
provider = LOCAL      (filter theo provider)
isActive = true       (filter theo trạng thái)
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "userId":    "550e8400-...",
        "email":     "user@example.com",
        "fullName":  "Nguyen Van A",
        "role":      "ROLE_USER",
        "provider":  "LOCAL",
        "isActive":  true,
        "createdAt": "2024-01-15T10:30:00Z"
      }
    ],
    "page":          0,
    "size":          20,
    "totalElements": 150,
    "totalPages":    8,
    "isLast":        false
  }
}
```

---

### GET /admin/users/{userId}
Lấy chi tiết 1 user.

**Response 200:** Trả về đầy đủ thông tin như `/users/me` kèm thêm `lastLoginAt`.

---

### PATCH /admin/users/{userId}/ban
Khoá tài khoản user.

**Request Body**
```json
{
  "reason": "Vi phạm điều khoản sử dụng"
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Đã khoá tài khoản người dùng."
}
```

---

### PATCH /admin/users/{userId}/unban
Mở khoá tài khoản user.

**Response 200**
```json
{
  "success": true,
  "message": "Đã mở khoá tài khoản người dùng."
}
```

---

### PATCH /admin/users/{userId}/role
Thay đổi role của user.

**Request Body**
```json
{
  "role": "ROLE_ADMIN"
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Đã cập nhật role thành công."
}
```

---

## 7. ERROR RESPONSE FORMAT — Chuẩn chung

Mọi lỗi đều trả về cùng format:

```json
{
  "success":   false,
  "code":      "ERROR_CODE",
  "message":   "Mô tả lỗi thân thiện với người dùng",
  "timestamp": "2024-01-15T10:30:00Z",
  "path":      "/api/v1/auth/login"
}
```

**Validation error (400):**
```json
{
  "success": false,
  "code":    "VALIDATION_FAILED",
  "message": "Dữ liệu không hợp lệ.",
  "errors": [
    { "field": "email",    "message": "Email không đúng định dạng." },
    { "field": "password", "message": "Mật khẩu phải có ít nhất 8 ký tự." }
  ]
}
```

**HTTP Status mapping:**
| Status | Ý nghĩa |
|--------|---------|
| 200 | Thành công |
| 201 | Tạo mới thành công |
| 400 | Dữ liệu không hợp lệ |
| 401 | Chưa đăng nhập / token hết hạn |
| 403 | Không có quyền |
| 404 | Không tìm thấy |
| 409 | Conflict (email đã tồn tại...) |
| 500 | Lỗi server |

---

---

# PHẦN 2 — DATABASE SCHEMA

---

## Bảng: users

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| email | VARCHAR(255) | NOT NULL, UNIQUE | |
| password_hash | VARCHAR(255) | NULLABLE | NULL nếu đăng nhập OAuth2 |
| full_name | VARCHAR(100) | NOT NULL | |
| phone | VARCHAR(15) | NULLABLE | |
| avatar_url | TEXT | NULLABLE | |
| role | VARCHAR(20) | NOT NULL, DEFAULT 'ROLE_USER' | ROLE_USER / ROLE_ADMIN |
| provider | VARCHAR(20) | NOT NULL, DEFAULT 'LOCAL' | LOCAL / GOOGLE / FACEBOOK |
| provider_id | VARCHAR(255) | NULLABLE | ID từ Google/Facebook |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE | |
| email_verified | BOOLEAN | NOT NULL, DEFAULT FALSE | |
| last_login_at | TIMESTAMP | NULLABLE | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_provider_provider_id ON users(provider, provider_id);
CREATE INDEX idx_users_is_active ON users(is_active);
```

---

## Bảng: refresh_tokens

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| user_id | UUID | NOT NULL, FK → users(id) ON DELETE CASCADE | |
| token | TEXT | NOT NULL, UNIQUE | Refresh token string |
| expires_at | TIMESTAMP | NOT NULL | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
```

**Lưu ý:** Một user có thể có nhiều refresh token (đăng nhập nhiều thiết bị). Khi logout thì xóa đúng token đó. Khi đổi mật khẩu thì xóa hết tất cả token của user đó.

---

## Bảng: user_addresses

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| user_id | UUID | NOT NULL, FK → users(id) ON DELETE CASCADE | |
| full_name | VARCHAR(100) | NOT NULL | Tên người nhận |
| phone | VARCHAR(15) | NOT NULL | SĐT người nhận |
| province | VARCHAR(100) | NOT NULL | Tỉnh / TP |
| district | VARCHAR(100) | NOT NULL | Quận / Huyện |
| ward | VARCHAR(100) | NOT NULL | Phường / Xã |
| street_detail | VARCHAR(255) | NOT NULL | Số nhà, tên đường |
| is_default | BOOLEAN | NOT NULL, DEFAULT FALSE | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE INDEX idx_addresses_user_id ON user_addresses(user_id);
```

**Constraint:** Mỗi user chỉ có 1 địa chỉ default → xử lý bằng logic trong Service (khi set default mới, update false tất cả địa chỉ cũ của user đó trước).

---

## Redis Keys

| Key pattern | Value | TTL | Mục đích |
|-------------|-------|-----|---------|
| `blacklist:{jti}` | `"1"` | Thời gian còn lại của access token | JWT blacklist khi logout |
| `reset_pwd:{token}` | `userId` | 15 phút | Forgot password token |
| `verify_email:{token}` | `userId` | 24 giờ | Email verification token |
| `rate_limit:login:{ip}` | Số lần thử | 15 phút | Chặn brute force login (max 5 lần) |

---

## Kafka Events publish từ User Service

### user.registered
```json
{
  "eventId":   "uuid-v4",
  "eventType": "user.registered",
  "timestamp": "2024-01-15T10:30:00Z",
  "version":   "1.0",
  "payload": {
    "userId":    "550e8400-e29b-41d4-a716-446655440000",
    "email":     "user@example.com",
    "fullName":  "Nguyen Van A",
    "provider":  "LOCAL",
    "createdAt": "2024-01-15T10:30:00Z"
  }
}
```
**Kafka key:** `userId`
**Consumer:** Notification Service (gửi email chào mừng)

---

### user.password_reset_requested
```json
{
  "eventId":   "uuid-v4",
  "eventType": "user.password_reset_requested",
  "timestamp": "2024-01-15T10:30:00Z",
  "version":   "1.0",
  "payload": {
    "userId":     "550e8400-e29b-41d4-a716-446655440000",
    "email":      "user@example.com",
    "resetToken": "reset-token-uuid",
    "expiresAt":  "2024-01-15T10:45:00Z"
  }
}
```
**Consumer:** Notification Service (gửi email reset password)

---

## Tổng hợp Endpoints

| Method | Endpoint | Auth | Role |
|--------|----------|------|------|
| POST | /auth/register | ❌ | — |
| POST | /auth/login | ❌ | — |
| POST | /auth/refresh | ❌ | — |
| POST | /auth/logout | ✅ | USER, ADMIN |
| POST | /auth/forgot-password | ❌ | — |
| POST | /auth/reset-password | ❌ | — |
| GET | /auth/verify-email | ❌ | — |
| GET | /oauth2/authorize/{provider} | ❌ | — |
| GET | /oauth2/callback/{provider} | ❌ | — |
| GET | /users/me | ✅ | USER, ADMIN |
| PUT | /users/me | ✅ | USER, ADMIN |
| POST | /users/me/avatar | ✅ | USER, ADMIN |
| PUT | /users/me/change-password | ✅ | USER, ADMIN |
| GET | /users/me/addresses | ✅ | USER, ADMIN |
| POST | /users/me/addresses | ✅ | USER, ADMIN |
| PUT | /users/me/addresses/{id} | ✅ | USER, ADMIN |
| DELETE | /users/me/addresses/{id} | ✅ | USER, ADMIN |
| PATCH | /users/me/addresses/{id}/default | ✅ | USER, ADMIN |
| GET | /internal/users/{userId} | 🔒 Internal | — |
| GET | /internal/users/{userId}/exists | 🔒 Internal | — |
| GET | /admin/users | ✅ | ADMIN |
| GET | /admin/users/{userId} | ✅ | ADMIN |
| PATCH | /admin/users/{userId}/ban | ✅ | ADMIN |
| PATCH | /admin/users/{userId}/unban | ✅ | ADMIN |
| PATCH | /admin/users/{userId}/role | ✅ | ADMIN |