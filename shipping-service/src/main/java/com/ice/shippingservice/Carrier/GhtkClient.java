package com.ice.shippingservice.Carrier;

import com.ice.shippingservice.Carrier.Support.CarrierCallExecutor;
import com.ice.shippingservice.Config.CarrierProperties;
import com.ice.shippingservice.Config.ShippingProperties;
import com.ice.shippingservice.DTO.Carrier.CreateOrderRequest;
import com.ice.shippingservice.DTO.Carrier.CreateOrderResult;
import com.ice.shippingservice.DTO.Carrier.FeeQuote;
import com.ice.shippingservice.DTO.Carrier.FeeRequest;
import com.ice.shippingservice.DTO.Carrier.ItemLine;
import com.ice.shippingservice.DTO.Carrier.Recipient;
import com.ice.shippingservice.DTO.Carrier.ResolveServiceRequest;
import com.ice.shippingservice.DTO.Carrier.TrackingEvent;
import com.ice.shippingservice.DTO.Carrier.Ghtk.GhtkAck;
import com.ice.shippingservice.DTO.Carrier.Ghtk.GhtkCreateOrderRequest;
import com.ice.shippingservice.DTO.Carrier.Ghtk.GhtkFeeResponse;
import com.ice.shippingservice.DTO.Carrier.Ghtk.GhtkOrderData;
import com.ice.shippingservice.DTO.Carrier.Ghtk.GhtkOrderResponse;
import com.ice.shippingservice.Enum.CarrierType;
import com.ice.shippingservice.Enum.ShipmentStatus;
import com.ice.shippingservice.Exception.CarrierApiException;
import com.ice.shippingservice.Exception.CarrierCannotCancelException;
import com.ice.shippingservice.Service.LabelStorage;
import com.ice.shippingservice.Util.CarrierDateParser;
import com.ice.shippingservice.Util.GhtkStatusMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * CarrierClient thật cho GHTK (bước 9). Chỉ được factory chọn khi carrier.mode=real và shipment.carrier=GHTK.
 * GHTK dùng TÊN tỉnh/quận/xã (không dùng id) và không có service_id rõ ràng.
 *
 * <p>VERIFY: endpoint / field / bảng status_id GHTK phải đối chiếu tài liệu chính thức khi có GHTK_TOKEN.
 */
@Component
@Slf4j
public class GhtkClient implements CarrierClient {

    private static final String BREAKER = "GHTK";

    private final RestClient http;
    private final CarrierCallExecutor call;
    private final CarrierProperties.Ghtk cfg;
    private final ShippingProperties shipping;
    private final LabelStorage labelStorage;

    public GhtkClient(RestClient ghtkRestClient, CarrierCallExecutor call,
                      CarrierProperties props, ShippingProperties shipping, LabelStorage labelStorage) {
        this.http = ghtkRestClient;
        this.call = call;
        this.cfg = props.getGhtk();
        this.shipping = shipping;
        this.labelStorage = labelStorage;
    }

    @Override
    public CarrierType carrierType() {
        return CarrierType.GHTK;
    }

    @Override
    public String resolveServiceId(ResolveServiceRequest req) {
        return cfg.getDefaultService();   // GHTK không có service_id -> giá trị cố định
    }

    @Override
    public List<FeeQuote> calculateFee(FeeRequest req) {
        ShippingProperties.From from = shipping.getFrom();
        GhtkFeeResponse res = call.call(BREAKER, "fee", () ->
                http.get()
                        .uri(u -> u.path("/services/shipment/fee")
                                .queryParam("pick_province", from.getProvince())
                                .queryParam("pick_district", from.getDistrict())
                                .queryParam("province", req.toProvinceName())
                                .queryParam("district", req.toDistrictName())
                                .queryParam("weight", req.weightGram())
                                .queryParam("value", req.insuranceValue())
                                .queryParam("transport", cfg.getDefaultService())
                                .build())
                        .headers(this::auth)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, GhtkClient::raiseHttp)
                        .body(GhtkFeeResponse.class));

        if (res == null || !res.success() || res.fee() == null) {
            throw new CarrierApiException("GHTK fee lỗi: " + (res != null ? res.message() : "body rỗng"));
        }
        return List.of(new FeeQuote(
                CarrierType.GHTK,
                cfg.getDefaultService(),
                res.fee().name() != null ? res.fee().name() : "GHTK " + cfg.getDefaultService(),
                res.fee().fee(),
                0,
                null));
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest req) {
        Recipient to = req.to();
        ShippingProperties.From from = shipping.getFrom();

        GhtkCreateOrderRequest.Order order = new GhtkCreateOrderRequest.Order(
                req.orderCode(),
                from.getName(), from.getAddress(), from.getProvince(), from.getDistrict(), from.getPhone(),
                to.name(), to.address(), to.provinceName(), to.districtName(), to.wardName(), "Khác",
                to.phone(), req.note(),
                req.insuranceValue(),
                req.codAmount(),
                1,
                cfg.getDefaultService());

        List<GhtkCreateOrderRequest.Product> products = req.items().stream()
                .map(this::toProduct)
                .toList();

        GhtkOrderResponse res = call.call(BREAKER, "createOrder", () ->
                http.post().uri("/services/shipment/order")
                        .headers(this::auth).contentType(MediaType.APPLICATION_JSON)
                        .body(new GhtkCreateOrderRequest(products, order))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, GhtkClient::raiseHttp)
                        .body(GhtkOrderResponse.class));

        if (res == null || !res.success() || res.order() == null || res.order().trackingCode() == null) {
            throw new CarrierApiException("GHTK create lỗi: " + (res != null ? res.message() : "body rỗng"));
        }
        GhtkOrderData o = res.order();
        return new CreateOrderResult(
                o.trackingCode(),
                ShipmentStatus.READY_TO_PICK,
                CarrierDateParser.toLocalDate(o.estimatedDeliverTime()),
                null);
    }

    @Override
    public List<TrackingEvent> getTracking(String trackingCode) {
        GhtkOrderResponse res = call.call(BREAKER, "getTracking", () ->
                http.get().uri("/services/shipment/v2/{code}", trackingCode)
                        .headers(this::auth)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, GhtkClient::raiseHttp)
                        .body(GhtkOrderResponse.class));

        if (res == null || !res.success() || res.order() == null) {
            return List.of();
        }
        GhtkOrderData o = res.order();
        // GHTK v2 tracking cơ bản chỉ trả trạng thái hiện tại -> 1 event.
        return List.of(new TrackingEvent(
                GhtkStatusMapper.mapOrInTransit(o.effectiveStatusId()),
                String.valueOf(o.effectiveStatusId()),
                o.statusText(),
                null,
                CarrierDateParser.toInstant(o.modified(), true)));
    }

    @Override
    public void cancelOrder(String trackingCode) {
        GhtkAck ack = call.call(BREAKER, "cancelOrder", () ->
                http.post().uri("/services/shipment/cancel/{code}", trackingCode)
                        .headers(this::auth)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, GhtkClient::raiseHttp)
                        .body(GhtkAck.class));

        if (ack == null || !ack.success()) {
            throw new CarrierCannotCancelException(
                    "GHTK không cho huỷ " + trackingCode + (ack != null ? ": " + ack.message() : ""));
        }
    }

    @Override
    public String getLabelUrl(String trackingCode) {
        byte[] pdf = call.call(BREAKER, "getLabelUrl", () ->
                http.get().uri("/services/label/{code}", trackingCode)
                        .headers(this::auth)
                        .accept(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, GhtkClient::raiseHttp)
                        .body(byte[].class));

        if (pdf == null || pdf.length == 0) {
            throw new CarrierApiException("GHTK label không trả file cho " + trackingCode);
        }
        return labelStorage.save(trackingCode, pdf);
    }

    // ------------------------------------------------------------------

    private void auth(HttpHeaders h) {
        h.set("Token", cfg.getToken());
    }

    private GhtkCreateOrderRequest.Product toProduct(ItemLine l) {
        return new GhtkCreateOrderRequest.Product(l.name(), l.weightGram() / 1000.0, l.quantity());
    }

    private static void raiseHttp(HttpRequest request, ClientHttpResponse response) throws IOException {
        throw new CarrierApiException("GHTK trả HTTP lỗi " + response.getStatusCode());
    }
}
