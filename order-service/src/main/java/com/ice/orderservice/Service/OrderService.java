package com.ice.orderservice.Service;

import com.ice.orderservice.Client.CartClient;
import com.ice.orderservice.Client.UserClient;
import com.ice.orderservice.DTO.Event.OrderCreatedPayload;
import com.ice.orderservice.DTO.Event.OrderItemEvent;
import com.ice.orderservice.DTO.Event.ShippingAddressEvent;
import com.ice.orderservice.DTO.Request.Order.CreatedOrderRequest;
import com.ice.orderservice.DTO.Response.Cart.CartCheckoutTokenResponse;
import com.ice.orderservice.DTO.Response.Cart.CartItemDataCheckoutTokenResponse;
import com.ice.orderservice.DTO.Response.Order.CreatedOrderResponse;
import com.ice.orderservice.DTO.Response.Order.OrderPageResponse;
import com.ice.orderservice.DTO.Response.Order.OrderResponse;
import com.ice.orderservice.DTO.Response.User.AddressResponse;
import com.ice.orderservice.Entity.*;
import com.ice.orderservice.Enum.CurrentStep;
import com.ice.orderservice.Enum.OrderStatus;
import com.ice.orderservice.Enum.SagaStatus;
import com.ice.orderservice.Exception.OrderAccessDeniedException;
import com.ice.orderservice.Repository.OrderRepo;
import com.ice.orderservice.Repository.OrderShippingAddressRepo;
import com.ice.orderservice.Repository.SageStateRepo;
import com.ice.orderservice.Specification.OrderSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepo orderRepo;
    private final OrderShippingAddressRepo orderShippingAddressRepo;
    private final SageStateRepo sageStateRepo;
    private final CartClient cartClient;
    private final UserClient userClient;
    private final KafkaProducerService kafkaProducerService;

    @Transactional
    public CreatedOrderResponse createOrder(CreatedOrderRequest request, String userId) {
        CartCheckoutTokenResponse cartCheckoutTokenResponse = cartClient.checkoutToken(request.getCheckoutToken());

        if (!cartCheckoutTokenResponse.getUserId().equals(userId)) {
            throw new OrderAccessDeniedException("Đơn hàng không thuộc về bạn");
        }

        AddressResponse addressResponse = userClient.getAddress(userId, request.getAddressId());

        String orderCode = generateOrderCode();
        long subtotal = cartCheckoutTokenResponse.getItems().stream()
                .mapToLong(CartItemDataCheckoutTokenResponse::getSubtotal)
                .sum();
        long discountAmount = cartCheckoutTokenResponse.getDiscountAmt() == null ? 0L : cartCheckoutTokenResponse.getDiscountAmt();
        long shippingFee = 0L; // TODO: tính phí ship khi có service riêng
        long totalAmount = subtotal - discountAmount + shippingFee;

        Order order = Order.builder()
                .orderCode(orderCode)
                .userId(UUID.fromString(userId))
                .subtotal(subtotal)
                .discountAmount(discountAmount)
                .shippingFee(shippingFee)
                .totalAmount(totalAmount)
                .couponCode(cartCheckoutTokenResponse.getCouponCode())
                .paymentMethod(request.getPaymentMethod())
                .note(request.getNote())
                .build();

        for (CartItemDataCheckoutTokenResponse cartItem : cartCheckoutTokenResponse.getItems())
        {
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .variantId(UUID.fromString(cartItem.getVariantId()))
                    .productId(UUID.fromString(cartItem.getProductId()))
                    .productName(cartItem.getProductName())
                    .sku(cartItem.getSku())
                    .color(cartItem.getColor())
                    .size(cartItem.getSize())
                    .thumbnail(cartItem.getThumbnail())
                    .unitPrice(cartItem.getUnitPrice())
                    .qty(cartItem.getQty())
                    .subtotal(cartItem.getSubtotal())
                    .build();
            order.getOrderItems().add(orderItem);
        }

        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(null)
                .toStatus(OrderStatus.PENDING)
                .note("Đơn hàng được tạo")
                .changedBy(userId)
                .build();
        order.getOrderStatusHistories().add(history);

        Order saveOrder = orderRepo.save(order);

        OrderShippingAddress shippingAddress = OrderShippingAddress.builder()
                .order(saveOrder)
                .fullName(addressResponse.getFullName())
                .phone(addressResponse.getPhone())
                .province(addressResponse.getProvince())
                .district(addressResponse.getDistrict())
                .ward(addressResponse.getWard())
                .streetDetail(addressResponse.getStreetDetail())
                .build();
        orderShippingAddressRepo.save(shippingAddress);

        SagaState sagaState = SagaState.builder()
                .order(saveOrder)
                .sagaStatus(SagaStatus.STARTED)
                .currentStep(CurrentStep.ORDER_CREATED)
                .completedSteps(new ArrayList<>(List.of("ORDER_CREATED")))
                .build();
        sageStateRepo.save(sagaState);

        // TODO: gọi payment-service (POST /payments/create) khi service đã sẵn sàng — PHẢI đứng TRƯỚC publish order.created
        // - Thành công (VNPAY/MOMO): nhận paymentId + paymentUrl -> set response.paymentUrl
        // - Thành công (COD): nhận paymentId, không paymentUrl
        // - Lỗi (GATEWAY_ERROR...): ROLLBACK order/order_items/shipping/saga_state vừa tạo, trả lỗi cho client, KHÔNG publish event bên dưới
        String paymentUrl = null;

        kafkaProducerService.publishOrderCreatedEvent(new OrderCreatedPayload(
                saveOrder.getId().toString(),
                saveOrder.getOrderCode(),
                userId,
                saveOrder.getTotalAmount(),
                saveOrder.getOrderItems().stream()
                        .map(orderItem ->
                                new OrderItemEvent(
                                        orderItem.getVariantId().toString(), orderItem.getQty())
                        )
                        .toList(),
                new ShippingAddressEvent(
                        addressResponse.getFullName(),
                        addressResponse.getPhone(),
                        addressResponse.getProvince(),
                        addressResponse.getDistrict(),
                        addressResponse.getWard(),
                        addressResponse.getStreetDetail()
                )
        ));

        return new CreatedOrderResponse(
                saveOrder.getId().toString(),
                saveOrder.getOrderCode(),
                saveOrder.getStatus().toString(),
                saveOrder.getTotalAmount(),
                saveOrder.getPaymentMethod().name(),
                paymentUrl,
                saveOrder.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }

    private String generateOrderCode() {
        return "ORD" + System.currentTimeMillis();
    }

    public OrderPageResponse getOrders(int page, int size, OrderStatus status, LocalDate startDate, LocalDate endDate, String userId) {

        Specification<Order> orderSpecification = Specification
                .where(OrderSpecification.hasStatus(status))
                .and(OrderSpecification.hasUserId(UUID.fromString(userId)))
                .and(OrderSpecification.betweenDays(
                        startDate == null ? null : startDate.atStartOfDay(),
                        endDate == null ? null : endDate.atTime(LocalTime.MAX)
                ));

        Page<Order> pageOrder = orderRepo.findAll(orderSpecification, PageRequest.of(page, size));

        List<OrderResponse> orderResponses = pageOrder.getContent().stream()
                .map(OrderResponse::from)
                .toList();

        return new OrderPageResponse(
                orderResponses,
                page,
                pageOrder.getTotalElements(),
                pageOrder.getTotalPages()
        );
    }
}
