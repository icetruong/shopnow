package com.ice.orderservice.Service;

import com.ice.orderservice.Client.CartClient;
import com.ice.orderservice.Client.InventoryClient;
import com.ice.orderservice.Client.PaymentClient;
import com.ice.orderservice.Client.UserClient;
import com.ice.orderservice.DTO.Event.Publish.*;
import com.ice.orderservice.DTO.Request.Inventory.*;
import com.ice.orderservice.DTO.Request.Order.AdminUpdateStatusOrderRequest;
import com.ice.orderservice.DTO.Request.Order.CancelledOrderRequest;
import com.ice.orderservice.DTO.Request.Order.CreatedOrderRequest;
import com.ice.orderservice.DTO.Request.Payment.CreatePaymentRequest;
import com.ice.orderservice.DTO.Request.Payment.RefundPaymentRequest;
import com.ice.orderservice.DTO.Response.Cart.CartCheckoutTokenResponse;
import com.ice.orderservice.DTO.Response.Cart.CartItemDataCheckoutTokenResponse;
import com.ice.orderservice.DTO.Response.Inventory.DeductResponse;
import com.ice.orderservice.DTO.Response.Inventory.ReserveResponseSuccess;
import com.ice.orderservice.DTO.Response.Order.*;
import com.ice.orderservice.DTO.Response.Payment.PaymentCreationResult;
import com.ice.orderservice.DTO.Response.Payment.PaymentInternalResponse;
import com.ice.orderservice.DTO.Response.User.AddressResponse;
import com.ice.orderservice.Entity.*;
import com.ice.orderservice.Enum.*;
import com.ice.orderservice.Exception.InvalidStatusTransitionException;
import com.ice.orderservice.Exception.OrderAccessDeniedException;
import com.ice.orderservice.Exception.OrderCannotCancelException;
import com.ice.orderservice.Exception.ResourceNotFoundException;
import com.ice.orderservice.Repository.OrderRepo;
import com.ice.orderservice.Repository.OrderShippingAddressRepo;
import com.ice.orderservice.Repository.SageStateRepo;
import com.ice.orderservice.Specification.OrderSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepo orderRepo;
    private final OrderShippingAddressRepo orderShippingAddressRepo;
    private final SageStateRepo sageStateRepo;
    private final CartClient cartClient;
    private final UserClient userClient;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;
    private final KafkaProducerService kafkaProducerService;

    // Chỉ chứa các bước thuần logistics, không cần xác thực nghiệp vụ gì thêm.
    // PENDING->CONFIRMED (cần xác nhận thanh toán/trừ kho) và *->CANCELLED (cần refund/release/return)
    // KHÔNG được phép đi qua đây — phải qua createOrder()/PaymentProcessedListener/cancelledOrder()
    // để đảm bảo đúng điều kiện nghiệp vụ, admin không thể tự ý bypass qua PATCH status.
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_STATUS_TRANSITIONS = Map.of(
            OrderStatus.CONFIRMED, Set.of(OrderStatus.PROCESSING),
            OrderStatus.PROCESSING, Set.of(OrderStatus.SHIPPING),
            OrderStatus.SHIPPING, Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, Set.of(OrderStatus.COMPLETED)
    );

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

        ReserveResponseSuccess reserveResponseSuccess = inventoryClient.reserve(new ReserveRequest(
                order.getId().toString(),
                saveOrder.getOrderItems().stream()
                        .map(orderItem ->
                                new ItemReserveRequest(
                                        orderItem.getVariantId().toString(), orderItem.getQty())
                        )
                        .toList()
        ));

        SagaState sagaState = SagaState.builder()
                .order(saveOrder)
                .sagaStatus(SagaStatus.STARTED)
                .currentStep(CurrentStep.STOCK_RESERVED)
                .completedSteps(new ArrayList<>(List.of("ORDER_CREATED", "STOCK_RESERVED")))
                .build();

        // TODO: gọi payment-service (POST /payments/create) khi service đã sẵn sàng — PHẢI đứng TRƯỚC publish order.created -> Done
        // - Thành công (VNPAY/MOMO): nhận paymentId + paymentUrl -> set response.paymentUrl
        // - Thành công (COD): nhận paymentId, không paymentUrl
        // - Lỗi (GATEWAY_ERROR...): ROLLBACK order/order_items/shipping/saga_state vừa tạo, trả lỗi cho client, KHÔNG publish event bên dưới


        String paymentUrl;
        try
        {
            PaymentCreationResult paymentCreationResult = paymentClient.createPayment(new CreatePaymentRequest(
                    saveOrder.getId().toString(),
                    saveOrder.getOrderCode(),
                    userId,
                    saveOrder.getTotalAmount(),
                    saveOrder.getPaymentMethod(),
                    null,
                    null
            ));
            paymentUrl = switch (paymentCreationResult)
            {
                case PaymentCreationResult.Online online -> online.response().getPaymentUrl();
                case PaymentCreationResult.Cod cod -> null;
            };
        }
        catch (Exception e)
        {
            inventoryClient.release(new ReleaseRequest(
                    saveOrder.getId().toString(),
                    ReasonRelease.PAYMENT_FAILED
            ));
            throw e;
        }

        if(saveOrder.getPaymentMethod() == PaymentMethod.COD)
        {
            try
            {
                DeductResponse deductResponse = inventoryClient.deduct(new DeductRequest(saveOrder.getId().toString()));
                sagaState.getCompletedSteps().add("PAYMENT_PROCESSED");
                sagaState.getCompletedSteps().add("STOCK_DEDUCTED");
                sagaState.getCompletedSteps().add("ORDER_CONFIRMED");
                sagaState.setCurrentStep(CurrentStep.ORDER_CONFIRMED);
                saveOrder.setStatus(OrderStatus.CONFIRMED);
                sagaState.setSagaStatus(SagaStatus.COMPLETED);
            }
            catch (Exception e)
            {
                inventoryClient.release(new ReleaseRequest(
                        saveOrder.getId().toString(),
                        ReasonRelease.PAYMENT_FAILED
                ));
                throw e;
            }
        }
        orderRepo.save(saveOrder);
        sageStateRepo.save(sagaState);

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
        if(saveOrder.getPaymentMethod() == PaymentMethod.COD)
        {
            kafkaProducerService.publishOrderConfirmEvent(new OrderConfirmPayload(
                    saveOrder.getId().toString(),
                    saveOrder.getOrderCode(),
                    userId,
                    new ShippingAddressEvent(
                            addressResponse.getFullName(),
                            addressResponse.getPhone(),
                            addressResponse.getProvince(),
                            addressResponse.getDistrict(),
                            addressResponse.getWard(),
                            addressResponse.getStreetDetail()
                    ),
                    saveOrder.getOrderItems().stream()
                            .map(orderItem ->
                                    new OrderItemEvent(
                                            orderItem.getVariantId().toString(), orderItem.getQty())
                            )
                            .toList()
            ));
        }

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

    public OrderDetailResponse getOrderDetail(String orderId, String userId) {
        Order order = orderRepo.findById(UUID.fromString(orderId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));

        if (!order.getUserId().equals(UUID.fromString(userId))) {
            throw new OrderAccessDeniedException("Đơn hàng không thuộc về bạn");
        }

        return buildOrderDetailResponse(order);
    }

    @Transactional
    public CancelledOrderResponse cancelledOrder(CancelledOrderRequest request, String orderId, String userId) {
        Order order = orderRepo.findById(UUID.fromString(orderId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));

        if (!order.getUserId().equals(UUID.fromString(userId))) {
            throw new OrderAccessDeniedException("Đơn hàng không thuộc về bạn");
        }

        return doCancelOrder(order, request, userId);
    }

    @Transactional
    public CancelledOrderResponse cancelOrderByAdmin(CancelledOrderRequest request, String orderId, String adminUserId) {
        Order order = orderRepo.findById(UUID.fromString(orderId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));

        // Không check order.getUserId() — admin được hủy đơn của bất kỳ khách hàng nào.
        return doCancelOrder(order, request, adminUserId);
    }

    private CancelledOrderResponse doCancelOrder(Order order, CancelledOrderRequest request, String changedBy) {
        if(order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PENDING)
        {
            throw new OrderCannotCancelException("Trạng thái đơn hàng không thể hủy");
        }

        SagaState sagaState = sageStateRepo.findByOrderId(order.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Sage State của đơn hàng"));

        OrderStatus oldStatus = order.getStatus();

        if (sagaState.getCompletedSteps().contains(CurrentStep.PAYMENT_PROCESSED.name()))
        {
            order.setStatus(OrderStatus.REFUNDING);
            // REFUNDING -> REFUNDED sẽ do 1 @KafkaListener riêng lắng nghe event "payment.refunded" xử lý, không phải ở đây

            PaymentInternalResponse payment = paymentClient.getPaymentByOrderId(order.getId().toString());

            paymentClient.refundPayment(new RefundPaymentRequest(
                    order.getId().toString(),
                    order.getTotalAmount(),
                    request.getReason()
            ), payment.getPaymentId());
        }
        else
        {
            order.setStatus(OrderStatus.CANCELLED);
        }

        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(oldStatus)
                .toStatus(order.getStatus())
                .note(request.getReason())
                .changedBy(changedBy)
                .build();
        order.getOrderStatusHistories().add(history);

        Order savedOrder = orderRepo.save(order);

        if(sagaState.getCompletedSteps().contains(CurrentStep.STOCK_DEDUCTED.name()))
        {
            // Refund (nếu có) đã gọi thành công ở trên rồi — đây là hành động không thể hoàn tác.
            // Nếu returnStock lỗi ở bước này, KHÔNG được để exception rollback lại toàn bộ transaction,
            // vì như vậy sẽ tạo ra tình trạng payment-service đã ghi nhận hoàn tiền nhưng order-service
            // lại báo lỗi và giữ nguyên trạng thái cũ — chỉ log lại để xử lý thủ công.
            try
            {
                inventoryClient.returnStock(new ReturnRequest(savedOrder.getId().toString()));
            }
            catch (Exception e)
            {
                log.error("Không thể hoàn kho cho order {} sau khi hủy (đã refund thành công) — cần xử lý thủ công",
                        savedOrder.getId(), e);
            }
        }
        else if (sagaState.getCompletedSteps().contains(CurrentStep.STOCK_RESERVED.name()))
        {
            // Chưa từng refund ở nhánh này (order còn PENDING, chưa PAYMENT_PROCESSED) — chưa có gì
            // không thể hoàn tác xảy ra trước đó, nên để lỗi rollback toàn bộ transaction là an toàn.
            inventoryClient.release(new ReleaseRequest(
                    savedOrder.getId().toString(),
                    ReasonRelease.ORDER_CANCELLED
            ));
        }

        sagaState.setSagaStatus(SagaStatus.COMPENSATED);
        sageStateRepo.save(sagaState);

        boolean needReleaseStock = false;

        List<OrderItemEvent> items = order.getOrderItems().stream()
                .map(orderItem -> new OrderItemEvent(orderItem.getVariantId().toString(), orderItem.getQty()))
                .toList();

        kafkaProducerService.publishOrderCancelledEvent(new OrderCancelledPayload(
                savedOrder.getId().toString(),
                request.getReason(),
                needReleaseStock,
                items
        ));

        return new CancelledOrderResponse(
                savedOrder.getId().toString(),
                savedOrder.getStatus().name()
        );
    }

    public OrderDetailResponse getOrderDetailInternal(String orderId) {
        Order order = orderRepo.findById(UUID.fromString(orderId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));

        return buildOrderDetailResponse(order);
    }

    public List<OrderDetailResponse> getOrderOfUser(String userId, List<OrderStatus> statuses) {
        List<OrderStatus> filter = (statuses == null || statuses.isEmpty()) ?
                List.of(OrderStatus.values())
                : statuses;

        return orderRepo.findAllByUserIdAndStatusIn(UUID.fromString(userId), filter)
                .stream()
                .map(this::buildOrderDetailResponse)
                .toList();
    }

    private OrderDetailResponse buildOrderDetailResponse(Order order) {
        OrderShippingAddress shippingAddress = orderShippingAddressRepo.findByOrderId(order.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ giao hàng"));

        List<OrderItemDetailResponse> items = order.getOrderItems().stream()
                .map(item -> new OrderItemDetailResponse(
                        item.getVariantId().toString(),
                        item.getProductId().toString(),
                        item.getProductName(),
                        item.getSku(),
                        item.getColor(),
                        item.getSize(),
                        item.getThumbnail(),
                        item.getUnitPrice(),
                        item.getQty(),
                        item.getSubtotal()
                ))
                .toList();

        OrderShippingAddressResponse shippingAddressResponse = new OrderShippingAddressResponse(
                shippingAddress.getFullName(),
                shippingAddress.getPhone(),
                shippingAddress.getProvince(),
                shippingAddress.getDistrict(),
                shippingAddress.getWard(),
                shippingAddress.getStreetDetail()
        );

        OrderPricingResponse pricing = new OrderPricingResponse(
                order.getSubtotal(),
                order.getDiscountAmount(),
                order.getShippingFee(),
                order.getTotalAmount()
        );

        List<OrderTimelineResponse> timeline = order.getOrderStatusHistories().stream()
                .sorted(Comparator.comparing(OrderStatusHistory::getCreatedAt))
                .map(history -> new OrderTimelineResponse(
                        history.getToStatus().name(),
                        history.getCreatedAt().toInstant(ZoneOffset.UTC)
                ))
                .toList();

        return new OrderDetailResponse(
                order.getId().toString(),
                order.getUserId().toString(),
                order.getOrderCode(),
                order.getStatus().name(),
                items,
                shippingAddressResponse,
                pricing,
                order.getCouponCode(),
                order.getPaymentMethod().name(),
                order.getPaymentStatus().name(),
                order.getNote(),
                timeline,
                order.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }

    public AdminOrderPageResponse getOrderPageAdmin(int page, int size, OrderStatus status, PaymentStatus paymentStatus, String keyword, String userId, LocalDate startDate, LocalDate endDate) {
        Specification<Order> orderSpecification = Specification
                .where(OrderSpecification.hasStatus(status))
                .and(OrderSpecification.hasPaymentStatus(paymentStatus))
                .and(OrderSpecification.hasKeyword(keyword))
                .and(OrderSpecification.hasUserId(userId == null ? null : UUID.fromString(userId)))
                .and(OrderSpecification.betweenDays(
                        startDate == null ? null : startDate.atStartOfDay(),
                        endDate == null ? null : endDate.atTime(LocalTime.MAX)
                ));

        Page<Order> pageOrder = orderRepo.findAll(orderSpecification, PageRequest.of(page, size));

        List<AdminOrderResponse> adminOrderResponses = pageOrder.getContent().stream()
                .map(AdminOrderResponse::from)
                .toList();

        return new AdminOrderPageResponse(
                adminOrderResponses,
                page,
                pageOrder.getTotalElements(),
                pageOrder.getTotalPages()
        );
    }

    @Transactional
    public void updateStatusOrder(AdminUpdateStatusOrderRequest request, String orderId, String userId) {
        Order order = orderRepo.findById(UUID.fromString(orderId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));

        OrderStatus oldStatus = order.getStatus();
        OrderStatus newStatus = request.getStatus();

        Set<OrderStatus> allowedNextStatuses = ALLOWED_STATUS_TRANSITIONS.getOrDefault(oldStatus, Set.of());
        if (!allowedNextStatuses.contains(newStatus)) {
            throw new InvalidStatusTransitionException(
                    "Không thể chuyển trạng thái từ " + oldStatus + " sang " + newStatus
            );
        }

        order.setStatus(newStatus);

        // COD & thu tiền khi giao hàng: DELIVERED + COD -> báo payment-service đã thu tiền (chỉ phục vụ đối soát/lịch sử)
        if (newStatus == OrderStatus.DELIVERED && order.getPaymentMethod() == PaymentMethod.COD) {
            PaymentInternalResponse payment = paymentClient.getPaymentByOrderId(order.getId().toString());
            paymentClient.confirmCod(payment.getPaymentId());
        }

        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(oldStatus)
                .toStatus(order.getStatus())
                .note(request.getNote())
                .changedBy(userId)
                .build();
        order.getOrderStatusHistories().add(history);

        orderRepo.save(order);
    }
}
