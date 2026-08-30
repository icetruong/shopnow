package com.ice.notificationservice.Service;

import com.ice.notificationservice.Client.OrderClient;
import com.ice.notificationservice.Client.UserClient;
import com.ice.notificationservice.DTO.Response.Order.OrderDetailResponse;
import com.ice.notificationservice.DTO.Response.User.InternalUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Bước 3 (xác định người nhận) dùng chung.
 *
 *  - Event có sẵn userId (order.created/confirmed, user.*) -> {@link #byUserId(String)}.
 *  - Event chỉ có orderId (payment.*, shipment.updated, order.cancelled) -> {@link #byOrderId(String)}:
 *    gọi Order Service lấy userId + orderCode trước, rồi mới gọi User Service.
 */
@Service
@RequiredArgsConstructor
public class RecipientResolver {

    private final UserClient userClient;
    private final OrderClient orderClient;

    public record Recipient(UUID userId, String email, String phone, String displayName) {}

    /** Người nhận + toàn bộ chi tiết đơn (để lấy orderCode, ...). */
    public record Resolved(Recipient recipient, OrderDetailResponse order) {}

    public Recipient byUserId(String userId) {
        InternalUserResponse user = userClient.getUser(userId);
        String name = (user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName()
                : "bạn";
        return new Recipient(UUID.fromString(userId), user.getEmail(), user.getPhone(), name);
    }

    public Resolved byOrderId(String orderId) {
        OrderDetailResponse order = orderClient.getOrder(orderId);
        return new Resolved(byUserId(order.getUserId()), order);
    }
}
