package com.ice.shippingservice.Carrier;

import com.ice.shippingservice.DTO.Carrier.*;
import com.ice.shippingservice.Entity.Shipment;
import com.ice.shippingservice.Enum.CarrierType;
import com.ice.shippingservice.Enum.ShipmentStatus;
import com.ice.shippingservice.Exception.CarrierCannotCancelException;
import com.ice.shippingservice.Exception.ResourceNotFoundException;
import com.ice.shippingservice.Repository.ShipmentRepo;
import com.ice.shippingservice.Repository.ShipmentTrackingRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class MockCarrierClient implements CarrierClient {

    private static final int FAST_DAYS = 2;
    private static final int STD_DAYS  = 4;
    private static final int CREATE_ORDER_ETA_DAYS = 3;
    private static final String LABEL_BASE = "https://mock.local/labels/";

    private final ShipmentRepo shipmentRepo;
    private final ShipmentTrackingRepo trackingRepo;

    @Override
    public CarrierType carrierType() {
        return null;
    }

    @Override
    public List<FeeQuote> calculateFee(FeeRequest req) {
        LocalDate today = LocalDate.now();
        return List.of(
                new FeeQuote(CarrierType.GHN, "MOCK_FAST", "Giao nhanh (mock)",
                        30000, FAST_DAYS, today.plusDays(FAST_DAYS)),
                new FeeQuote(CarrierType.GHN, "MOCK_STD", "Giao tiêu chuẩn (mock)",
                        22000, STD_DAYS, today.plusDays(STD_DAYS))
        );
    }

    @Override
    public String resolveServiceId(ResolveServiceRequest req) {
        return "MOCK_STD";
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest req) {
        String trackingCode = randomTrackingCode();
        return new CreateOrderResult(
                trackingCode,
                ShipmentStatus.READY_TO_PICK,
                LocalDate.now().plusDays(CREATE_ORDER_ETA_DAYS),
                LABEL_BASE + trackingCode + ".pdf"
        );
    }

    @Override
    public List<TrackingEvent> getTracking(String trackingCode) {
        Shipment shipment = shipmentRepo.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy shipment cho trackingCode=" + trackingCode));

        return trackingRepo.findAllByShipmentIdOrderByHappenedAtAsc(shipment.getId()).stream()
                .map(shipmentTracking -> new TrackingEvent(
                        shipmentTracking.getStatus(),
                        shipmentTracking.getCarrierStatus(),
                        shipmentTracking.getDescription(),
                        shipmentTracking.getLocation(),
                        shipmentTracking.getHappenedAt().toInstant(ZoneOffset.UTC)))
                .toList();
    }

    @Override
    public void cancelOrder(String trackingCode) {
        char last = trackingCode.charAt(trackingCode.length() - 1);
        if (Character.isDigit(last) && (last - '0') % 2 == 0) {
            throw new CarrierCannotCancelException(
                    "Đơn đã được lấy hàng, không thể huỷ: " + trackingCode);
        }
        // huỷ được → no-op
    }

    @Override
    public String getLabelUrl(String trackingCode) {
        return LABEL_BASE + trackingCode + ".pdf";
    }

    private String randomTrackingCode() {
        int n = ThreadLocalRandom.current().nextInt(0, 1_000_000_000);
        return "MOCK" + String.format("%09d", n); // "MOCK" + đúng 9 chữ số
    }
}
