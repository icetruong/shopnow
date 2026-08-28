package com.ice.shippingservice.Service;

import com.ice.shippingservice.Carrier.CarrierClient;
import com.ice.shippingservice.Carrier.CarrierClientFactory;
import com.ice.shippingservice.Client.OrderClient;
import com.ice.shippingservice.Config.ShippingProperties;
import com.ice.shippingservice.DTO.Carrier.*;
import com.ice.shippingservice.DTO.Event.Consume.OrderCancelledPayload;
import com.ice.shippingservice.DTO.Event.Consume.OrderConfirmPayload;
import com.ice.shippingservice.DTO.Event.Consume.OrderItemEvent;
import com.ice.shippingservice.DTO.Event.Consume.ShippingAddressEvent;
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

    @Transactional
    public void createFromOrderConfirmed(OrderConfirmPayload payload, String eventId)
    {
        UUID orderId = UUID.fromString(payload.getOrderId());

        // 0. IDEMPOTENCY
        if(Boolean.TRUE.equals(stringRedisTemplate.hasKey(PROCESSED_KEY + eventId)))
        {
            log.info("event {} đã xử lý, bỏ qua", eventId);
            return;
        }

        if(shipmentRepo.findByOrderId(orderId).isPresent())
        {
            log.info("order {} đã có shipment, bỏ qua event {}", orderId, eventId);
            markProcessed(eventId);
            return;
        }

        // 1. ENRICH: gọi Order Service
        //   GET http://localhost:8085/api/v1/internal/orders/{orderId}
        OrderDetailResponse orderDetailResponse = orderClient.getOrder(orderId.toString());

        ShippingAddressEvent addr = payload.getShippingAddress();
        int weightGram = payload.getItems().stream().mapToInt(OrderItemEvent::getQty).sum()
                * shippingProperties.getDefaultItemWeightGrams();
        long total = orderDetailResponse.getPricing().getTotal();
        long codAmount = "COD".equalsIgnoreCase(orderDetailResponse.getPaymentMethod()) ? total : 0L;

        Shipment.ShipmentBuilder base = Shipment.builder()
                .orderId(orderId)
                .orderCode(payload.getOrderCode())
                .userId(UUID.fromString(payload.getUserId()))
                .carrier(shippingProperties.getDefaultCarrier())
                .toName(addr.getFullName())
                .toPhone(addr.getPhone())
                .toAddress(addr.getStreetDetail())
                .toProvince(addr.getProvince())
                .toDistrict(addr.getDistrict())
                .toWard(addr.getWard())
                .weight(weightGram)
                .shippingFee(orderDetailResponse.getPricing().getShippingFee())
                .codAmount(codAmount)
                .insuranceValue(total)
                .paymentMethod(orderDetailResponse.getPaymentMethod())
                .note(orderDetailResponse.getNote());

        // ---- 2. RESOLVE ĐỊA CHỈ ----
        Optional<LocationMapping> loc =
                locationResolver.resolve(addr.getProvince(), addr.getDistrict(), addr.getWard());
        if(loc.isEmpty())
        {
            savePending(base, "ADDRESS_MAPPING_FAILED");
            markProcessed(eventId);
            log.warn("order {} map địa chỉ fail -> shipment PENDING", orderId);
            return;
        }

        LocationMapping locationMapping = loc.get();
        base.toProvinceId(locationMapping.getGhnProvinceId())
                .toDistrictId(locationMapping.getGhnDistrictId())
                .toWardCode(locationMapping.getGhnWardCode());

        // 3.  XÁC ĐỊNH CARRIER + SERVICE
        CarrierType carrier = CarrierType.valueOf(shippingProperties.getDefaultCarrier());
        CarrierClient client = carrierClientFactory.forCarrier(carrier);
        String serviceId = client.resolveServiceId(new ResolveServiceRequest(
                shippingProperties.getFrom().getDistrictId(), locationMapping.getGhnDistrictId(), weightGram));
        base.serviceId(serviceId);

        CreateOrderResult result;
        try {
            result = client.createOrder(buildCreateOrderRequest(payload, orderDetailResponse, locationMapping, serviceId, weightGram, codAmount, total));
        } catch (CarrierApiException e) {
            savePending(base, "CARRIER_API_ERROR");
            markProcessed(eventId);
            log.warn("order {} carrier tạo đơn lỗi -> shipment PENDING: {}", orderId, e.getMessage());
            return;
        }

        // ---- 7. INSERT shipment ----
        Shipment shipment = base
                .status(result.status())                 // READY_TO_PICK
                .trackingCode(result.trackingCode())
                .estimatedDate(result.estimatedDate())
                .shippingLabelUrl(result.labelUrl())
                .build();
        shipmentRepo.save(shipment);

        ShipmentTracking shipmentTracking = ShipmentTracking.builder()
                .shipment(shipment)
                .status(result.status())
                .description("Đơn hàng đã tạo, chờ lấy hàng")
                .happenedAt(LocalDateTime.now())
                .build();
        shipmentTrackingRepo.save(shipmentTracking);

        log.info("Tạo shipment {} cho order {} OK, trackingCode={}",
                shipment.getId(), orderId, shipment.getTrackingCode());

        kafkaProducerService.publishShipmentUpdate(new ShipmentUpdatePayload(
                orderId.toString(),
                shipment.getId().toString(),
                shipment.getTrackingCode(),
                shipment.getCarrier(),
                shipment.getStatus().name(),
                shipmentTracking.getDescription(),
                shipment.getEstimatedDate()
        ));

        markProcessed(eventId);
    }

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

    private void savePending(Shipment.ShipmentBuilder base, String reason) {
        shipmentRepo.save(base.status(ShipmentStatus.PENDING).failureReason(reason).build());
    }

    private CreateOrderRequest buildCreateOrderRequest(
            OrderConfirmPayload payload, OrderDetailResponse order, LocationMapping m,
            String serviceId, int weightGram, long codAmount, long total) {

        var addr = payload.getShippingAddress();
        var pkg  = shippingProperties.getDefaultPackage();

        Recipient to = new Recipient(
                addr.getFullName(), addr.getPhone(), addr.getStreetDetail(),
                addr.getProvince(), addr.getDistrict(), addr.getWard(),
                m.getGhnDistrictId(), m.getGhnWardCode());

        List<ItemLine> lines = order.getItems().stream()
                .map(i -> new ItemLine(i.getProductName(), i.getQty(),
                        shippingProperties.getDefaultItemWeightGrams()))
                .toList();

        return new CreateOrderRequest(
                payload.getOrderCode(), serviceId, to,
                weightGram, pkg.getLength(), pkg.getWidth(), pkg.getHeight(),
                codAmount, total, order.getNote(), lines);
    }
}
