package com.ice.shippingservice.Service;

import com.ice.shippingservice.Carrier.CarrierClient;
import com.ice.shippingservice.Carrier.CarrierClientFactory;
import com.ice.shippingservice.Client.OrderClient;
import com.ice.shippingservice.Config.ShippingProperties;
import com.ice.shippingservice.DTO.Carrier.*;
import com.ice.shippingservice.DTO.Event.Consume.OrderCancelledPayload;
import com.ice.shippingservice.DTO.Event.Consume.OrderConfirmPayload;
import com.ice.shippingservice.DTO.Event.Publish.ShipmentUpdatePayload;
import com.ice.shippingservice.DTO.Request.ShippingFeeRequest;
import com.ice.shippingservice.DTO.Response.Order.OrderDetailResponse;
import com.ice.shippingservice.DTO.Response.Shipping.ShippingFeeResponse;
import com.ice.shippingservice.Entity.LocationMapping;
import com.ice.shippingservice.Entity.Shipment;
import com.ice.shippingservice.Entity.ShipmentTracking;
import com.ice.shippingservice.Enum.CarrierType;
import com.ice.shippingservice.Enum.ShipmentStatus;
import com.ice.shippingservice.Exception.CarrierApiException;
import com.ice.shippingservice.Exception.FeeCalculationException;
import com.ice.shippingservice.Exception.CarrierCannotCancelException;
import com.ice.shippingservice.Exception.ResourceNotFoundException;
import com.ice.shippingservice.Repository.ShipmentRepo;
import com.ice.shippingservice.Repository.ShipmentTrackingRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShippingService {
    private static final String PROCESSED_KEY = "processed:event:";
    private static final String TRACKING_CREATED_DESC = "Đơn hàng đã tạo, chờ lấy hàng";

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final CarrierClientFactory carrierClientFactory;
    private final ShippingProperties shippingProperties;
    private final ShipmentRepo shipmentRepo;
    private final OrderClient orderClient;
    private final LocationResolver locationResolver;
    private final ShipmentTrackingRepo shipmentTrackingRepo;
    private final KafkaProducerService kafkaProducerService;

    public List<ShippingFeeResponse> calculateFee(ShippingFeeRequest request) {
        ShippingProperties.DefaultPackage pkg = shippingProperties.getDefaultPackage();
        int length = request.getLength() != null ? request.getLength() : pkg.getLength();
        int width  = request.getWidth()  != null ? request.getWidth()  : pkg.getWidth();
        int height = request.getHeight() != null ? request.getHeight() : pkg.getHeight();
        long insurance = request.getInsuranceValue() != null ? request.getInsuranceValue() : 0L;

        var from = shippingProperties.getFrom();
        FeeRequest feeRequest = new FeeRequest(
                from.getDistrictId(),
                from.getWardCode(),
                request.getToDistrictId(),
                request.getToWardCode(),
                null,
                null,
                null,
                request.getWeight(),
                length,
                width,
                height,
                insurance
        );

        String cacheKey = buildCacheKey(feeRequest);

        Object cache = redisTemplate.opsForValue().get(cacheKey);

        if(cache != null)
            return (List<ShippingFeeResponse>) cache;

        List<FeeQuote> feeQuotes = new ArrayList<>();
        for(CarrierClient client : carrierClientFactory.forFeeQuote())
        {
            try
            {
                feeQuotes.addAll(client.calculateFee(feeRequest));
            }
            catch (RuntimeException e)
            {
                log.warn("Carrier {} tính phí lỗi: {}", client.carrierType(), e.getMessage());
            }
        }

        if (feeQuotes.isEmpty()) {
            throw new FeeCalculationException("Không tính được phí ship từ bất kỳ nhà vận chuyển nào.");
        }

        List<ShippingFeeResponse> responses = new ArrayList<>(
                feeQuotes.stream().map(feeQuote -> new ShippingFeeResponse(
                        feeQuote.carrier().name(),
                        feeQuote.serviceId(),
                        feeQuote.serviceName(),
                        feeQuote.fee(),
                        feeQuote.estimatedDays(),
                        feeQuote.estimatedDate()
                )).toList()
        );

        redisTemplate.opsForValue().set(cacheKey, responses, Duration.ofHours(1));

        return responses;
    }

    // =========================================================================
    // FLOW TẠO VẬN ĐƠN
    // =========================================================================

    /** Consumer order.confirmed: idempotency + enrich + dựng shipment MỚI -> resolveAndDispatch. */
    @Transactional
    public void createFromOrderConfirmed(OrderConfirmPayload payload, String eventId) {
        UUID orderId = UUID.fromString(payload.getOrderId());

        // 0. IDEMPOTENCY
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(PROCESSED_KEY + eventId))) {
            log.info("event {} đã xử lý, bỏ qua", eventId);
            return;
        }
        if (shipmentRepo.findByOrderId(orderId).isPresent()) {
            log.info("order {} đã có shipment, bỏ qua event {}", orderId, eventId);
            markProcessed(eventId);
            return;
        }

        // 1. ENRICH
        OrderDetailResponse order = orderClient.getOrder(orderId.toString());

        Shipment shipment = buildShipmentFromOrder(
                orderId, UUID.fromString(payload.getUserId()), order);

        // 2 -> 10
        resolveAndDispatch(shipment, toItemLines(order));

        // 11
        markProcessed(eventId);
    }

    /** Kết quả tạo vận đơn cho POST /internal/shipments (phân biệt tạo mới vs đã tồn tại). */
    public record ShipmentCreationResult(Shipment shipment, boolean alreadyExisted) {}

    /**
     * POST /internal/shipments: tạo vận đơn cho 1 order.
     * - Đã có shipment cho order này -> trả về row cũ, alreadyExisted = true (controller -> 200).
     * - Chưa có -> enrich + dựng shipment MỚI -> resolveAndDispatch (INSERT).
     *   Sau khi trả về, controller kiểm tra status/failureReason để quyết định 201 / 422.
     */
    @Transactional
    public ShipmentCreationResult createForOrder(String orderIdStr, UUID userId) {
        UUID orderId = UUID.fromString(orderIdStr);

        Optional<Shipment> existing = shipmentRepo.findByOrderId(orderId);
        if (existing.isPresent()) {
            return new ShipmentCreationResult(existing.get(), true);
        }

        OrderDetailResponse order = orderClient.getOrder(orderIdStr);
        Shipment shipment = buildShipmentFromOrder(orderId, userId, order);
        resolveAndDispatch(shipment, toItemLines(order));

        return new ShipmentCreationResult(shipment, false);
    }

    /**
     * POST /admin/shipments/{id}/retry: ép tạo lại vận đơn với carrier cho shipment đang PENDING
     * (địa chỉ đã được sửa mapping, hoặc carrier API đã ổn). Dùng LẠI row cũ -> resolveAndDispatch UPDATE.
     */
    @Transactional
    public Shipment retry(String shipmentIdStr) {
        UUID id = UUID.fromString(shipmentIdStr);
        Shipment shipment = shipmentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy shipment " + id));

        if (shipment.getStatus() != ShipmentStatus.PENDING) {
            throw new IllegalStateException("Chỉ retry được shipment đang PENDING (hiện tại: "
                    + shipment.getStatus() + ")");
        }
        int retryCount = shipment.getRetryCount() == null ? 0 : shipment.getRetryCount();
        if (retryCount >= 5) {
            throw new IllegalStateException("Shipment đã retry >= 5 lần, cần xử lý tay");
        }

        OrderDetailResponse order = orderClient.getOrder(shipment.getOrderId().toString());
        shipment.setRetryCount(retryCount + 1);

        resolveAndDispatch(shipment, toItemLines(order));

        return shipment;
    }

    /**
     * LÕI DÙNG CHUNG (bước 2 -> 10 của "Flow tạo vận đơn").
     * shipment: entity đã điền phần snapshot (to*, weight, phí, note...). Có thể MỚI (chưa id)
     *           hoặc row PENDING đang retry -> shipmentRepo.save() tự INSERT / UPDATE.
     * itemLines: tên hàng để in label (từ enrich order).
     * Kết thúc: shipment.status = READY_TO_PICK (OK) hoặc PENDING + failureReason (fail). Luôn được save.
     */
    private void resolveAndDispatch(Shipment shipment, List<ItemLine> itemLines) {
        UUID orderId = shipment.getOrderId();

        // 2. RESOLVE ĐỊA CHỈ (text snapshot -> mã GHN)
        Optional<LocationMapping> loc = locationResolver.resolve(
                shipment.getToProvince(), shipment.getToDistrict(), shipment.getToWard());
        if (loc.isEmpty()) {
            markShipmentPending(shipment, "ADDRESS_MAPPING_FAILED");
            log.warn("order {} map địa chỉ fail -> shipment PENDING", orderId);
            return;
        }
        LocationMapping m = loc.get();
        shipment.setToProvinceId(m.getGhnProvinceId());
        shipment.setToDistrictId(m.getGhnDistrictId());
        shipment.setToWardCode(m.getGhnWardCode());

        // 3. CARRIER + SERVICE
        CarrierClient client = carrierClientFactory.forCarrier(
                CarrierType.valueOf(shipment.getCarrier()));
        String serviceId = client.resolveServiceId(new ResolveServiceRequest(
                shippingProperties.getFrom().getDistrictId(), m.getGhnDistrictId(), shipment.getWeight()));
        shipment.setServiceId(serviceId);

        // 6. GỌI CARRIER TẠO ĐƠN
        CreateOrderResult result;
        try {
            result = client.createOrder(buildCreateOrderRequest(shipment, itemLines));
        } catch (CarrierApiException e) {
            markShipmentPending(shipment, "CARRIER_API_ERROR");
            log.warn("order {} carrier tạo đơn lỗi -> shipment PENDING: {}", orderId, e.getMessage());
            return;
        }

        // 7. CẬP NHẬT shipment
        shipment.setStatus(result.status());                 // READY_TO_PICK
        shipment.setTrackingCode(result.trackingCode());
        shipment.setEstimatedDate(result.estimatedDate());
        shipment.setShippingLabelUrl(result.labelUrl());
        shipment.setFailureReason(null);                     // clear khi thành công (quan trọng cho retry)
        shipmentRepo.save(shipment);

        // 8. INSERT tracking
        ShipmentTracking tracking = ShipmentTracking.builder()
                .shipment(shipment)
                .status(result.status())
                .description(TRACKING_CREATED_DESC)
                .happenedAt(LocalDateTime.now())
                .build();
        shipmentTrackingRepo.save(tracking);

        // 10. PUBLISH
        kafkaProducerService.publishShipmentUpdate(new ShipmentUpdatePayload(
                orderId.toString(),
                shipment.getId().toString(),
                shipment.getTrackingCode(),
                shipment.getCarrier(),
                shipment.getStatus().name(),
                tracking.getDescription(),
                shipment.getEstimatedDate()
        ));

        log.info("shipment {} cho order {} -> {} trackingCode={}",
                shipment.getId(), orderId, shipment.getStatus(), shipment.getTrackingCode());
    }

    /** Dựng entity Shipment MỚI từ dữ liệu enrich (order-service) + userId. status tạm = PENDING. */
    private Shipment buildShipmentFromOrder(UUID orderId, UUID userId, OrderDetailResponse order) {
        var addr = order.getShippingAddress();
        int weightGram = order.getItems().stream().mapToInt(i -> i.getQty()).sum()
                * shippingProperties.getDefaultItemWeightGrams();
        long total = order.getPricing().getTotal();
        long codAmount = "COD".equalsIgnoreCase(order.getPaymentMethod()) ? total : 0L;

        return Shipment.builder()
                .orderId(orderId)
                .orderCode(order.getOrderCode())
                .userId(userId)
                .carrier(shippingProperties.getDefaultCarrier())
                .toName(addr.getFullName())
                .toPhone(addr.getPhone())
                .toAddress(addr.getStreetDetail())
                .toProvince(addr.getProvince())
                .toDistrict(addr.getDistrict())
                .toWard(addr.getWard())
                .weight(weightGram)
                .shippingFee(order.getPricing().getShippingFee())
                .codAmount(codAmount)
                .insuranceValue(total)
                .paymentMethod(order.getPaymentMethod())
                .note(order.getNote())
                .status(ShipmentStatus.PENDING)
                .build();
    }

    private List<ItemLine> toItemLines(OrderDetailResponse order) {
        return order.getItems().stream()
                .map(i -> new ItemLine(i.getProductName(), i.getQty(),
                        shippingProperties.getDefaultItemWeightGrams()))
                .toList();
    }

    private void markShipmentPending(Shipment shipment, String reason) {
        shipment.setStatus(ShipmentStatus.PENDING);
        shipment.setFailureReason(reason);
        shipmentRepo.save(shipment);
    }

    private CreateOrderRequest buildCreateOrderRequest(Shipment s, List<ItemLine> lines) {
        var pkg = shippingProperties.getDefaultPackage();

        Recipient to = new Recipient(
                s.getToName(), s.getToPhone(), s.getToAddress(),
                s.getToProvince(), s.getToDistrict(), s.getToWard(),
                s.getToDistrictId(), s.getToWardCode());   // mã GHN - set bởi bước resolve

        return new CreateOrderRequest(
                s.getOrderCode(), s.getServiceId(), to,
                s.getWeight(), pkg.getLength(), pkg.getWidth(), pkg.getHeight(),
                s.getCodAmount(), s.getInsuranceValue(), s.getNote(), lines);
    }

    // =========================================================================
    // HỦY VẬN ĐƠN
    // =========================================================================

    @Transactional
    public void orderCancelled(OrderCancelledPayload payload, String eventId)
    {
        UUID orderId = UUID.fromString(payload.getOrderId());

        if(Boolean.TRUE.equals(stringRedisTemplate.hasKey(PROCESSED_KEY + eventId)))
        {
            log.info("event {} đã xử lý, bỏ qua", eventId);
            return;
        }

        // Không có shipment -> order bị hủy trước khi kịp tạo vận đơn -> coi như xong
        Optional<Shipment> opt = shipmentRepo.findByOrderId(orderId);
        if(opt.isEmpty())
        {
            log.info("order {} chưa có shipment, bỏ qua order.cancelled", orderId);
            markProcessed(eventId);
            return;
        }

        Shipment shipment = opt.get();
        ShipmentStatus status = shipment.getStatus();

        // Chưa lấy hàng -> hủy được
        if(status == ShipmentStatus.PENDING || status == ShipmentStatus.READY_TO_PICK)
        {
            try
            {
                if(shipment.getTrackingCode() != null)   // PENDING chưa có trackingCode
                {
                    carrierClientFactory.forCarrier(CarrierType.valueOf(shipment.getCarrier()))
                            .cancelOrder(shipment.getTrackingCode());
                }
                cancelShipment(shipment, payload.getReason());
            }
            catch (CarrierCannotCancelException e)
            {
                log.warn("order {}: carrier từ chối hủy ({}), giữ nguyên - cần admin xử lý",
                        orderId, e.getMessage());
            }
        }
        // Đã lấy hàng -> không hủy được
        else if(status == ShipmentStatus.PICKED_UP || status == ShipmentStatus.IN_TRANSIT)
        {
            log.warn("order {}: shipment đang {}, KHÔNG hủy được - alert admin xử lý hoàn hàng",
                    orderId, status);
        }
        // còn lại (DELIVERED/FAILED/RETURNED/CANCELLED) -> không làm gì
        else
        {
            log.info("order {}: shipment đang {}, bỏ qua order.cancelled", orderId, status);
        }

        markProcessed(eventId);
    }

    private void cancelShipment(Shipment shipment, String reason)
    {
        shipment.setStatus(ShipmentStatus.CANCELLED);
        shipmentRepo.save(shipment);

        String description = "Đơn hàng đã bị hủy" + (reason != null ? " (" + reason + ")" : "");

        ShipmentTracking shipmentTracking = ShipmentTracking.builder()
                .shipment(shipment)
                .status(ShipmentStatus.CANCELLED)
                .description(description)
                .happenedAt(LocalDateTime.now())
                .build();
        shipmentTrackingRepo.save(shipmentTracking);

        kafkaProducerService.publishShipmentUpdate(new ShipmentUpdatePayload(
                shipment.getOrderId().toString(),
                shipment.getId().toString(),
                shipment.getTrackingCode(),
                shipment.getCarrier(),
                shipment.getStatus().name(),
                description,
                shipment.getEstimatedDate()
        ));

        log.info("order {}: shipment {} -> CANCELLED", shipment.getOrderId(), shipment.getId());
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private String buildCacheKey(FeeRequest r) {
        return "shipping:fee:" + r.fromDistrictId()
                + ":" + r.toDistrictId()
                + ":" + r.toWardCode()
                + ":" + r.weightGram();
    }

    private void markProcessed(String eventId)
    {
        stringRedisTemplate.opsForValue().set(PROCESSED_KEY+eventId, "1", Duration.ofHours(24));
    }
}
