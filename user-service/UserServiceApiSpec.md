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
- `phone`: định dạng số VN (tùy chọn — không bắt buộc)

**Response 201**
```json
{
  "success": true,
  "message": "Đăng ký thành công. Vui lòng kiểm tra email để xác thực tài khoản",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email":  "user@example.com"
  }
}
```

**Response 409** — email đã tồn tại
```json
{
  "success":   false,
  "errorCode": "EMAIL_ALREADY_EXISTS",
  "message":   "Email này đã được đăng ký"
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

**Validation**
- `email`: không được trống, đúng định dạng email
- `password`: không được trống, 8–32 ký tự, có ít nhất 1 chữ hoa, 1 số, 1 ký tự đặc biệt

**Response 200**
```json
{
  "success": true,
  "message": "login thành công",
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
  "success":   false,
  "errorCode": "INVALID_CREDENTIALS",
  "message":   "Email hoặc mật khẩu không đúng"
}
```

**Response 403** — tài khoản bị khoá
```json
{
  "success":   false,
  "errorCode": "ACCOUNT_DISABLED",
  "message":   "Tài khoản đã bị vô hiệu hóa"
}
```

**Side effect:** Cập nhật `lastLoginAt` của user.

---

### POST /auth/refresh
Lấy access token mới bằng refresh token (Refresh Token Rotation — refresh token cũ bị hủy, cấp token mới).

**Request Body**
```json
{
  "refreshToken": "uuid-string"
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Dùng Refresh token đổi lấy token thành công",
  "data": {
    "accessToken":  "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "uuid-string-moi",
    "tokenType":    "Bearer",
    "expiresIn":    900
  }
}
```

**Response 401** — refresh token hết hạn hoặc không hợp lệ
```json
{
  "success":   false,
  "errorCode": "REFRESH_TOKEN_INVALID",
  "message":   "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại."
}
```

---

### POST /auth/logout
Đăng xuất — blacklist access token hiện tại và xóa tất cả refresh token của user (logout all devices).

**Header:** `Authorization: Bearer {accessToken}` *(bắt buộc)*

**Response 200**
```json
{
  "success": true,
  "message": "Đăng xuất thành công"
}
```

**Side effect:**
- Lưu `jti` của access token vào Redis blacklist với TTL = thời gian còn lại của token.
- Xóa toàn bộ refresh token của user (tất cả thiết bị đều bị logout).

---

### POST /auth/forgot-password
Gửi email đặt lại mật khẩu.

**Request Body**
```json
{
  "email": "user@example.com"
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Email có thể đổi mật khẩu.",
  "data": {
    "token":     "reset-token-uuid",
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
  "message": "Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập lại"
}
```

**Response 400** — token hết hạn hoặc không hợp lệ
```json
{
  "success":   false,
  "errorCode": "RESET_TOKEN_INVALID",
  "message":   "Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn."
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
  "success":   false,
  "errorCode": "VERIFICATION_TOKEN_INVALID",
  "message":   "Link xác thực không hợp lệ hoặc đã hết hạn."
}
```

---

## 2. OAUTH2 — Đăng nhập mạng xã hội

---

### GET /oauth2/authorize/{provider}
Redirect sang trang đăng nhập Google. Spring Security tự xử lý, không cần viết controller.

- `provider`: `google`

**Flow:** Client gọi endpoint này → redirect sang Google → sau khi user đồng ý → Google redirect về callback.

---

### GET /oauth2/callback/{provider}
Callback URL sau khi Google xác thực. Spring Security tự xử lý.

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
  "message": "Lấy thông tin thành công",
  "data": {
    "userId":        "550e8400-e29b-41d4-a716-446655440000",
    "email":         "user@example.com",
    "fullName":      "Nguyen Van A",
    "phone":         "0901234567",
    "avatarUrl":     "http://localhost:8081/uploads/abc.jpg",
    "role":          "ROLE_USER",
    "provider":      "LOCAL",
    "emailVerified": true,
    "isActive":      true,
    "createdAt":     "2024-01-15T10:30:00"
  }
}
```

> **Lưu ý:** `createdAt` là `LocalDateTime` không có timezone (format: `"2024-01-15T10:30:00"`).

---

### PUT /users/me
Cập nhật thông tin cá nhân.

**Header:** `Authorization: Bearer {accessToken}` *(bắt buộc)*

**Request Body**
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
  "message": "Cập nhập thông tin thành công",
  "data": {
    "userId":        "550e8400-e29b-41d4-a716-446655440000",
    "email":         "user@example.com",
    "fullName":      "Nguyen Van B",
    "phone":         "0909999999",
    "avatarUrl":     null,
    "role":          "ROLE_USER",
    "provider":      "LOCAL",
    "emailVerified": false,
    "isActive":      true,
    "createdAt":     "2024-01-15T10:30:00"
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
  "message": "Ảnh thành công",
  "data": {
    "avatarUrl": "http://localhost:8081/uploads/550e8400.jpg"
  }
}
```

**Response 400** — file không hợp lệ
```json
{
  "success":   false,
  "errorCode": "INVALID_FILE",
  "message":   "Đuôi ảnh không hợp lệ"
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
  "newPassword":     "NewPassword456!"
}
```

**Validation**
- `currentPassword`: không được trống, 8–32 ký tự, ít nhất 1 chữ hoa + 1 số + 1 ký tự đặc biệt
- `newPassword`: không được trống, 8–32 ký tự, ít nhất 1 chữ hoa + 1 số + 1 ký tự đặc biệt

**Response 200**
```json
{
  "success": true,
  "message": "Đổi mật khẩu thành công"
}
```

**Response 400** — mật khẩu hiện tại sai hoặc tài khoản OAuth2
```json
{
  "success":   false,
  "errorCode": "WRONG_PASSWORD",
  "message":   "Mật khẩu hiện tại không đúng."
}
```

> **Lưu ý:** Cả 2 trường hợp (sai mật khẩu và tài khoản OAuth2) đều trả về `errorCode: WRONG_PASSWORD`. Message sẽ khác nhau.
> **Side effect:** Xóa tất cả refresh token của user (logout all devices).

---

## 4. ADDRESS — Địa chỉ giao hàng

---

### GET /users/me/addresses
Lấy danh sách địa chỉ của user.

**Response 200**
```json
{
  "success": true,
  "message": "Lấy danh sách địa chỉ thành công",
  "data": [
    {
      "addressId":    "uuid-1",
      "fullName":     "Nguyen Van A",
      "phone":        "0901234567",
      "province":     "TP. Hồ Chí Minh",
      "district":     "Quận 1",
      "ward":         "Phường Bến Nghé",
      "streetDetail": "123 Đường Lê Lợi",
      "isDefault":    true
    }
  ]
}
```

---

### POST /users/me/addresses
Thêm địa chỉ mới. `fullName` và `phone` lấy từ profile của user.

**Header:** `Authorization: Bearer {accessToken}` *(bắt buộc)*

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
  "message": "Thêm thành công",
  "data": {
    "addressId":    "uuid-moi",
    "fullName":     "Nguyen Van A",
    "phone":        "0901234567",
    "province":     "TP. Hồ Chí Minh",
    "district":     "Quận 1",
    "ward":         "Phường Bến Nghé",
    "streetDetail": "123 Đường Lê Lợi",
    "isDefault":    false
  }
}
```

**Validation:**
- Mỗi user tối đa 5 địa chỉ.
- User phải có `phone` trong profile trước khi thêm địa chỉ.

**Response 400** — vượt giới hạn hoặc chưa có phone
```json
{
  "success":   false,
  "errorCode": "INVALID_FILE",
  "message":   "Mỗi tài khoản chỉ được thêm tối đa 5 địa chỉ"
}
```

---

### PUT /users/me/addresses/{addressId}
Cập nhật địa chỉ.

**Request Body:** Giống POST.

**Response 200**
```json
{
  "success": true,
  "message": "Cập nhập thành công",
  "data": { }
}
```

---

### DELETE /users/me/addresses/{addressId}
Xóa địa chỉ.

**Response 200**
```json
{
  "success": true,
  "message": "Xóa thành công"
}
```

---

### PATCH /users/me/addresses/{addressId}/default
Đặt làm địa chỉ mặc định.

**Response 200**
```json
{
  "success": true,
  "message": "Cập nhập thành công"
}
```

---

## 5. INTERNAL — Dành cho các service khác gọi

> Các endpoint này được bảo vệ bằng header `X-Internal-Token`. Request thiếu hoặc sai token → 403.

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

> **Lưu ý:** `defaultAddress` là `null` nếu user chưa có địa chỉ mặc định.

---

### GET /internal/users/{userId}/exists
Kiểm tra user có tồn tại không.

**Response 200**
```json
{
  "exists":   true,
  "isActive": true
}
```

---

## 6. ADMIN — Quản trị (chỉ ROLE_ADMIN)

---

### GET /api/v1/admin/users
Lấy danh sách tất cả user, có phân trang và filter.

**Header:** `Authorization: Bearer {accessToken}` *(ROLE_ADMIN)*

**Query Params**
```
page      = 0         (default 0)
size      = 10        (default 10)
sort      = createdAt (default createdAt)
direction = DESC      (default DESC — ASC | DESC)
keyword               (tùy chọn — tìm theo email hoặc fullName)
provider              (tùy chọn — filter theo provider: LOCAL | GOOGLE)
isActive              (tùy chọn — filter theo trạng thái: true | false)
```

**Response 200**
```json
{
  "success": true,
  "message": "lấy thành công",
  "data": {
    "content": [
      {
        "userId":        "550e8400-...",
        "email":         "user@example.com",
        "fullName":      "Nguyen Van A",
        "phone":         "0901234567",
        "avatarUrl":     null,
        "role":          "ROLE_USER",
        "provider":      "LOCAL",
        "emailVerified": false,
        "isActive":      true,
        "createdAt":     "2024-01-15T10:30:00"
      }
    ],
    "page":          0,
    "size":          10,
    "totalElements": 150,
    "totalPages":    15,
    "isLast":        false
  }
}
```

---

### GET /api/v1/admin/users/{userId}
Lấy chi tiết 1 user (trả về đầy đủ thông tin như `/users/me`).

---

### PATCH /api/v1/admin/users/{userId}/ban
Khoá tài khoản user.

**Response 200**
```json
{
  "success": true,
  "message": "lấy thành công"
}
```

---

### PATCH /api/v1/admin/users/{userId}/unban
Mở khoá tài khoản user.

**Response 200**
```json
{
  "success": true,
  "message": "lấy thành công"
}
```

---

### PATCH /api/v1/admin/users/{userId}/role
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
  "message": "lấy thành công"
}
```

---

## 7. ERROR RESPONSE FORMAT — Chuẩn chung

Mọi lỗi đều trả về cùng format:

```json
{
  "success":   false,
  "errorCode": "ERROR_CODE",
  "message":   "Mô tả lỗi thân thiện với người dùng"
}
```

> **Lưu ý:** Field tên là `errorCode` (không phải `code`).

**Validation error (400):**
```json
{
  "success":   false,
  "errorCode": "INVALID_REQUEST",
  "message":   "email: email is invalid, password: Password must be between 8 and 32 characters"
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
| 409 | Conflict (email đã tồn tại) |
| 500 | Lỗi server |

**Error codes:**
| errorCode | HTTP | Ý nghĩa |
|-----------|------|---------|
| `INVALID_REQUEST` | 400 | Validation thất bại |
| `INVALID_FILE` | 400 | File upload không hợp lệ / lỗi địa chỉ |
| `WRONG_PASSWORD` | 400 | Sai mật khẩu hoặc tài khoản OAuth2 |
| `RESET_TOKEN_INVALID` | 400 | Reset password token hết hạn |
| `VERIFICATION_TOKEN_INVALID` | 400 | Email verify token hết hạn |
| `INVALID_CREDENTIALS` | 401 | Sai email/password khi login |
| `REFRESH_TOKEN_INVALID` | 401 | Refresh token không hợp lệ |
| `ACCOUNT_DISABLED` | 403 | Tài khoản bị vô hiệu hóa |
| `ACCOUNT_LOCKED` | 403 | Tài khoản bị khóa |
| `NOT_FOUND` | 404 | Không tìm thấy resource |
| `EMAIL_ALREADY_EXISTS` | 409 | Email đã được đăng ký |
| `INTERNAL_ERROR` | 500 | Lỗi hệ thống |

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
| provider | VARCHAR(20) | NOT NULL, DEFAULT 'LOCAL' | LOCAL / GOOGLE |
| provider_id | VARCHAR(255) | NULLABLE | ID từ Google |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE | |
| email_verified | BOOLEAN | NOT NULL, DEFAULT FALSE | |
| last_login_at | TIMESTAMP | NULLABLE | Cập nhật mỗi lần login thành công |
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
| token | TEXT | NOT NULL, UNIQUE | Refresh token string (UUID) |
| expires_at | TIMESTAMP | NOT NULL | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE UNIQUE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
```

**Lưu ý:**
- Một user có thể có nhiều refresh token (đăng nhập nhiều thiết bị).
- Khi logout → xóa **tất cả** refresh token của user đó (logout all devices).
- Khi đổi mật khẩu → xóa tất cả refresh token của user đó.

---

## Bảng: user_addresses

| Column | Type | Constraint | Ghi chú |
|--------|------|-----------|---------|
| id | UUID | PK, DEFAULT uuid_generate_v4() | |
| user_id | UUID | NOT NULL, FK → users(id) ON DELETE CASCADE | |
| full_name | VARCHAR(100) | NOT NULL | Lấy từ user profile khi tạo |
| phone | VARCHAR(15) | NOT NULL | Lấy từ user profile khi tạo |
| province | VARCHAR(100) | NOT NULL | |
| district | VARCHAR(100) | NOT NULL | |
| ward | VARCHAR(100) | NOT NULL | |
| street_detail | VARCHAR(255) | NOT NULL | |
| is_default | BOOLEAN | NOT NULL, DEFAULT FALSE | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | |

**Index:**
```sql
CREATE INDEX idx_addresses_user_id ON user_addresses(user_id);
```

**Constraint:**
- Mỗi user tối đa **5 địa chỉ** → kiểm tra trong Service.
- Mỗi user chỉ có 1 địa chỉ default → khi set default mới, update false tất cả địa chỉ cũ trước.
- User phải có `phone` trong profile trước khi thêm địa chỉ.

---

## Redis Keys

| Key pattern | Value | TTL | Mục đích |
|-------------|-------|-----|---------|
| `blacklist:{jti}` | `"1"` | Thời gian còn lại của access token | JWT blacklist khi logout |
| `reset_pwd:{token}` | `userId` | 15 phút | Forgot password token |
| `verify_email:{token}` | `userId` | 24 giờ | Email verification token |

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
    "createdAt": "2024-01-15T10:30:00"
  }
}
```
**Kafka key:** `userId`
**Consumer:** Notification Service (gửi email chào mừng + link verify)

> **Lưu ý:** `createdAt` là `LocalDateTime.toString()` — không có timezone suffix `Z`.

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
**Kafka key:** `userId`
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
| GET | /api/v1/admin/users | ✅ | ADMIN |
| GET | /api/v1/admin/users/{userId} | ✅ | ADMIN |
| PATCH | /api/v1/admin/users/{userId}/ban | ✅ | ADMIN |
| PATCH | /api/v1/admin/users/{userId}/unban | ✅ | ADMIN |
| PATCH | /api/v1/admin/users/{userId}/role | ✅ | ADMIN |