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
import com.ice.shippingservice.Enum.ShipmentStatus;
import com.ice.shippingservice.Exception.CarrierApiException;
import com.ice.shippingservice.Exception.CarrierCannotCancelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.HttpMethod;

class GhnClientTest {

    private MockRestServiceServer server;
    private GhnClient ghn;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient http = builder.build();

        CarrierProperties props = new CarrierProperties();
        props.setMode("real");
        props.getResilience().setBackoffMs(0);
        props.getResilience().setMaxAttempts(2);
        props.getGhn().setToken("token");
        props.getGhn().setShopId("123");
        props.getGhn().setDefaultServiceId("53321");
        props.getGhn().setLabelPrintUrl("https://ghn.test/printA5");

        ghn = new GhnClient(http, new CarrierCallExecutor(props), props);
    }

    private static String env(String data) {
        return "{\"code\":200,\"message\":\"OK\",\"data\":" + data + "}";
    }

    @Test
    void resolveServiceId_picksFirstServiceFromAvailableServices() {
        server.expect(requestTo("/v2/shipping-order/available-services"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(env("[{\"service_id\":53320,\"short_name\":\"Nhanh\"}]"),
                        MediaType.APPLICATION_JSON));

        assertThat(ghn.resolveServiceId(new ResolveServiceRequest(1442, 1450, 500)))
                .isEqualTo("53320");
        server.verify();
    }

    @Test
    void resolveServiceId_fallsBackToDefaultWhenListEmpty() {
        server.expect(requestTo("/v2/shipping-order/available-services"))
                .andRespond(withSuccess(env("[]"), MediaType.APPLICATION_JSON));

        assertThat(ghn.resolveServiceId(new ResolveServiceRequest(1442, 1450, 500)))
                .isEqualTo("53321");
        server.verify();
    }

    @Test
    void calculateFee_combinesAvailableServicesWithPerServiceFee() {
        server.expect(requestTo("/v2/shipping-order/available-services"))
                .andRespond(withSuccess(env("[{\"service_id\":53320,\"short_name\":\"Nhanh\"}]"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("/v2/shipping-order/fee"))
                .andRespond(withSuccess(env("{\"total\":31500,\"service_fee\":30000,\"insurance_fee\":1500}"),
                        MediaType.APPLICATION_JSON));

        List<FeeQuote> quotes = ghn.calculateFee(new FeeRequest(
                1442, "21012", 1450, "21112", null, null, null, 500, 20, 15, 10, 448200));

        assertThat(quotes).hasSize(1);
        assertThat(quotes.get(0).serviceId()).isEqualTo("53320");
        assertThat(quotes.get(0).fee()).isEqualTo(31500);
        server.verify();
    }

    @Test
    void createOrder_returnsTrackingCodeAndEstimatedDate() {
        server.expect(requestTo("/v2/shipping-order/create"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        env("{\"order_code\":\"GHN999\",\"expected_delivery_time\":\"2024-01-17T00:00:00Z\"}"),
                        MediaType.APPLICATION_JSON));

        CreateOrderResult r = ghn.createOrder(sampleCreateReq());

        assertThat(r.trackingCode()).isEqualTo("GHN999");
        assertThat(r.status()).isEqualTo(ShipmentStatus.READY_TO_PICK);
        assertThat(r.estimatedDate()).isEqualTo("2024-01-17");
        server.verify();
    }

    @Test
    void getTracking_mapsGhnLogToInternalEvents() {
        server.expect(requestTo("/v2/shipping-order/detail"))
                .andRespond(withSuccess(env("""
                        {"order_code":"GHN999","status":"delivering","log":[
                          {"status":"picked","updated_date":"2024-01-15T15:00:00Z","location_name":"Kho HCM"},
                          {"status":"delivering","updated_date":"2024-01-16T08:00:00Z","location_name":"TT phân loại"}
                        ]}"""), MediaType.APPLICATION_JSON));

        List<TrackingEvent> events = ghn.getTracking("GHN999");

        assertThat(events).hasSize(2);
        assertThat(events.get(0).status()).isEqualTo(ShipmentStatus.PICKED_UP);
        assertThat(events.get(1).status()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(events.get(1).location()).isEqualTo("TT phân loại");
        server.verify();
    }

    @Test
    void cancelOrder_throwsCannotCancelWhenGhnResultFalse() {
        server.expect(requestTo("/v2/switch-status/cancel"))
                .andRespond(withSuccess(
                        env("[{\"order_code\":\"GHN999\",\"result\":false,\"message\":\"đã lấy hàng\"}]"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> ghn.cancelOrder("GHN999"))
                .isInstanceOf(CarrierCannotCancelException.class)
                .hasMessageContaining("đã lấy hàng");
        server.verify();
    }

    @Test
    void getLabelUrl_buildsPrintUrlWithToken() {
        server.expect(requestTo("/v2/a5/gen-token"))
                .andRespond(withSuccess(env("{\"token\":\"abc123\"}"), MediaType.APPLICATION_JSON));

        assertThat(ghn.getLabelUrl("GHN999")).isEqualTo("https://ghn.test/printA5?token=abc123");
        server.verify();
    }

    @Test
    void serverError_isRetriedThenWrappedAsCarrierApiException() {
        server.expect(times(2), requestTo("/v2/shipping-order/available-services"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> ghn.calculateFee(new FeeRequest(
                1442, "21012", 1450, "21112", null, null, null, 500, 20, 15, 10, 0)))
                .isInstanceOf(CarrierApiException.class);
        server.verify();
    }

    private CreateOrderRequest sampleCreateReq() {
        Recipient to = new Recipient("Nguyen Van A", "0901234567", "123 Le Loi",
                "TP. Hồ Chí Minh", "Quận 1", "Phường Bến Nghé", 1450, "21112");
        return new CreateOrderRequest("SN1", "53320", to, 500, 20, 15, 10,
                0, 448200, "giao giờ hành chính", List.of(new ItemLine("Áo thun", 2, 250)));
    }
}
