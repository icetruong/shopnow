package com.ice.shippingservice.Carrier;

import com.ice.shippingservice.Carrier.Support.CarrierCallExecutor;
import com.ice.shippingservice.Config.CarrierProperties;
import com.ice.shippingservice.DTO.Carrier.CreateOrderRequest;
import com.ice.shippingservice.DTO.Carrier.CreateOrderResult;
import com.ice.shippingservice.DTO.Carrier.FeeQuote;
import com.ice.shippingservice.DTO.Carrier.FeeRequest;
import com.ice.shippingservice.DTO.Carrier.ItemLine;
import com.ice.shippingservice.DTO.Carrier.Recipient;
import com.ice.shippingservice.DTO.Carrier.ResolveServiceRequest;
import com.ice.shippingservice.DTO.Carrier.TrackingEvent;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnAvailableServicesRequest;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnCancelResultItem;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnCreateOrderData;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnCreateOrderRequest;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnEnvelope;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnFeeData;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnFeeRequest;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnGenTokenData;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnOrderCodeRequest;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnOrderCodesRequest;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnOrderDetailData;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnServiceItem;
import com.ice.shippingservice.Enum.CarrierType;
import com.ice.shippingservice.Enum.ShipmentStatus;
import com.ice.shippingservice.Exception.CarrierApiException;
import com.ice.shippingservice.Exception.CarrierCannotCancelException;
import com.ice.shippingservice.Util.CarrierDateParser;
import com.ice.shippingservice.Util.GhnStatusMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

/**
 * CarrierClient thật cho GHN (bước 8). Chỉ được CarrierClientFactory chọn khi carrier.mode=real.
 * Mọi call bọc qua {@link CarrierCallExecutor} (retry + circuit breaker); lỗi hạ tầng -> CarrierApiException.
 *
 * <p>VERIFY: field/endpoint dựng theo skeleton spec PHẦN 5 mục 4 + GHN v2 API phổ biến.
 * Phải đối chiếu tài liệu GHN hiện hành khi có GHN_TOKEN sandbox.
 */
@Component
@Slf4j
public class GhnClient implements CarrierClient {

    private static final String BREAKER = "GHN";
    private static final int PAYMENT_TYPE_SHOP_PAYS = 1;          // VERIFY
    private static final String REQUIRED_NOTE = "KHONGCHOXEMHANG"; // VERIFY
    private static final int GHN_OK = 200;

    private final RestClient http;
    private final CarrierCallExecutor call;
    private final CarrierProperties.Ghn cfg;
    private final int shopId;

    public GhnClient(RestClient ghnRestClient, CarrierCallExecutor call, CarrierProperties props) {
        this.http = ghnRestClient;
        this.call = call;
        this.cfg = props.getGhn();
        this.shopId = parseShopId(cfg.getShopId());
    }

    @Override
    public CarrierType carrierType() {
        return CarrierType.GHN;
    }

    @Override
    public String resolveServiceId(ResolveServiceRequest req) {
        try {
            return availableServices(req.fromDistrictId(), req.toDistrictId()).stream()
                    .filter(s -> s.serviceId() > 0)
                    .findFirst()
                    .map(s -> String.valueOf(s.serviceId()))
                    .orElseGet(this::defaultServiceId);
        } catch (CarrierApiException e) {
            log.warn("GHN available-services lỗi, dùng default-service-id: {}", e.getMessage());
            return defaultServiceId();
        }
    }

    @Override
    public List<FeeQuote> calculateFee(FeeRequest req) {
        List<GhnServiceItem> services = availableServices(req.fromDistrictId(), req.toDistrictId());
        if (services.isEmpty()) {
            throw new CarrierApiException("GHN không trả gói dịch vụ nào cho tuyến này");
        }
        return services.stream()
                .map(svc -> {
                    GhnFeeData fee = fee(svc.serviceId(), req);
                    // VERIFY: GHN fee response không kèm số ngày -> 0 / null (bổ sung khi gọi leadtime).
                    return new FeeQuote(
                            CarrierType.GHN,
                            String.valueOf(svc.serviceId()),
                            svc.shortName() != null ? svc.shortName() : "GHN " + svc.serviceId(),
                            fee.totalOrZero(),
                            0,
                            null);
                })
                .toList();
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest req) {
        Recipient to = req.to();
        GhnCreateOrderRequest body = new GhnCreateOrderRequest(
                req.orderCode(),
                to.name(), to.phone(), to.address(),
                to.wardCode(),
                to.districtId() != null ? to.districtId() : 0,
                req.weightGram(), req.lengthCm(), req.widthCm(), req.heightCm(),
                parseServiceId(req.serviceId()),
                PAYMENT_TYPE_SHOP_PAYS,
                REQUIRED_NOTE,
                req.codAmount(),
                req.insuranceValue(),
                req.note(),
                req.items().stream().map(this::toGhnItem).toList());

        GhnCreateOrderData data = call.call(BREAKER, "createOrder", () -> unwrap(
                http.post().uri("/v2/shipping-order/create")
                        .headers(this::auth).contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, GhnClient::raiseHttp)
                        .body(new ParameterizedTypeReference<GhnEnvelope<GhnCreateOrderData>>() { }),
                "createOrder"));

        if (data == null || data.orderCode() == null || data.orderCode().isBlank()) {
            throw new CarrierApiException("GHN create không trả order_code");
        }
        return new CreateOrderResult(
                data.orderCode(),
                ShipmentStatus.READY_TO_PICK,
                CarrierDateParser.toLocalDate(data.expectedDeliveryTime()),
                null);
    }

    @Override
    public List<TrackingEvent> getTracking(String trackingCode) {
        GhnOrderDetailData data = call.call(BREAKER, "getTracking", () -> unwrap(
                http.post().uri("/v2/shipping-order/detail")
                        .headers(this::auth).contentType(MediaType.APPLICATION_JSON)
                        .body(new GhnOrderCodeRequest(trackingCode))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, GhnClient::raiseHttp)
                        .body(new ParameterizedTypeReference<GhnEnvelope<GhnOrderDetailData>>() { }),
                "getTracking"));

        if (data == null) {
            return List.of();
        }
        return data.log().stream()
                .map(l -> new TrackingEvent(
                        GhnStatusMapper.mapOrInTransit(l.status()),
                        l.status(),
                        null,
                        l.locationName(),
                        CarrierDateParser.toInstant(l.updatedDate(), true)))
                .toList();
    }

    @Override
    public void cancelOrder(String trackingCode) {
        List<GhnCancelResultItem> results = call.call(BREAKER, "cancelOrder", () -> unwrap(
                http.post().uri("/v2/switch-status/cancel")
                        .headers(this::auth).contentType(MediaType.APPLICATION_JSON)
                        .body(GhnOrderCodesRequest.of(trackingCode))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, GhnClient::raiseHttp)
                        .body(new ParameterizedTypeReference<GhnEnvelope<List<GhnCancelResultItem>>>() { }),
                "cancelOrder"));

        boolean ok = results != null && results.stream()
                .filter(r -> trackingCode.equals(r.orderCode()))
                .findFirst()
                .map(GhnCancelResultItem::result)
                .orElse(false);

        if (!ok) {
            String msg = (results == null || results.isEmpty()) ? null : results.get(0).message();
            throw new CarrierCannotCancelException(
                    "GHN không cho huỷ " + trackingCode + (msg != null ? ": " + msg : ""));
        }
    }

    @Override
    public String getLabelUrl(String trackingCode) {
        GhnGenTokenData data = call.call(BREAKER, "getLabelUrl", () -> unwrap(
                http.post().uri("/v2/a5/gen-token")   // VERIFY: GHN dùng POST cho gen-token
                        .headers(this::auth).contentType(MediaType.APPLICATION_JSON)
                        .body(GhnOrderCodesRequest.of(trackingCode))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, GhnClient::raiseHttp)
                        .body(new ParameterizedTypeReference<GhnEnvelope<GhnGenTokenData>>() { }),
                "getLabelUrl"));

        if (data == null || data.token() == null) {
            throw new CarrierApiException("GHN gen-token không trả token cho " + trackingCode);
        }
        return cfg.getLabelPrintUrl() + "?token=" + data.token();
    }

    // ------------------------------------------------------------------

    private List<GhnServiceItem> availableServices(int fromDistrict, int toDistrict) {
        List<GhnServiceItem> data = call.call(BREAKER, "availableServices", () -> unwrap(
                http.post().uri("/v2/shipping-order/available-services")
                        .headers(this::auth).contentType(MediaType.APPLICATION_JSON)
                        .body(new GhnAvailableServicesRequest(shopId, fromDistrict, toDistrict))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, GhnClient::raiseHttp)
                        .body(new ParameterizedTypeReference<GhnEnvelope<List<GhnServiceItem>>>() { }),
                "availableServices"));
        return data != null ? data : List.of();
    }

    private GhnFeeData fee(long serviceId, FeeRequest req) {
        GhnFeeRequest body = new GhnFeeRequest(
                serviceId,
                req.fromDistrictId(), req.fromWardCode(),
                req.toDistrictId(), req.toWardCode(),
                req.weightGram(), req.lengthCm(), req.widthCm(), req.heightCm(),
                req.insuranceValue());

        GhnFeeData data = call.call(BREAKER, "fee", () -> unwrap(
                http.post().uri("/v2/shipping-order/fee")
                        .headers(this::auth).contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, GhnClient::raiseHttp)
                        .body(new ParameterizedTypeReference<GhnEnvelope<GhnFeeData>>() { }),
                "fee"));

        if (data == null) {
            throw new CarrierApiException("GHN fee không trả data cho service " + serviceId);
        }
        return data;
    }

    private void auth(HttpHeaders h) {
        h.set("Token", cfg.getToken());
        h.set("ShopId", cfg.getShopId());
    }

    private <T> T unwrap(GhnEnvelope<T> env, String op) {
        if (env == null) {
            throw new CarrierApiException("GHN " + op + " trả body rỗng");
        }
        if (env.code() != GHN_OK) {
            throw new CarrierApiException(
                    "GHN " + op + " lỗi code=" + env.code() + " msg=" + env.message());
        }
        return env.data();
    }

    private static void raiseHttp(HttpRequest request, ClientHttpResponse response) throws IOException {
        throw new CarrierApiException("GHN trả HTTP lỗi " + response.getStatusCode());
    }

    private GhnCreateOrderRequest.Item toGhnItem(ItemLine l) {
        return new GhnCreateOrderRequest.Item(l.name(), l.quantity(), l.weightGram());
    }

    private String defaultServiceId() {
        String d = cfg.getDefaultServiceId();
        if (d == null || d.isBlank()) {
            throw new CarrierApiException(
                    "Không resolve được GHN service và carrier.ghn.default-service-id trống");
        }
        return d;
    }

    private static long parseServiceId(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (RuntimeException e) {
            throw new CarrierApiException("serviceId GHN không hợp lệ: " + raw);
        }
    }

    private static int parseShopId(String raw) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (RuntimeException e) {
            return 0;   // mode=mock / chưa cấu hình; GhnClient chỉ hoạt động khi mode=real
        }
    }
}
