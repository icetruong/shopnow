package com.ice.shippingservice.Carrier;

import com.ice.shippingservice.DTO.Carrier.*;
import com.ice.shippingservice.Enum.CarrierType;

import java.util.List;

public interface CarrierClient {
    CarrierType carrierType();                       // GHN | GHTK

    List<FeeQuote> calculateFee(FeeRequest req);     // nhiều gói dịch vụ của hãng đó

    String resolveServiceId(ResolveServiceRequest req); // gói phù hợp nhất (rẻ nhất) cho tuyến from→to

    CreateOrderResult createOrder(CreateOrderRequest req);

    List<TrackingEvent> getTracking(String trackingCode);

    void cancelOrder(String trackingCode);          // ném CarrierCannotCancelException nếu đã lấy hàng

    String getLabelUrl(String trackingCode);        // link PDF label (reprint)
}
