package com.ice.shippingservice.Carrier;

import com.ice.shippingservice.Carrier.Support.CarrierCallExecutor;
import com.ice.shippingservice.Config.CarrierProperties;
import com.ice.shippingservice.Config.ShippingProperties;
import com.ice.shippingservice.DTO.Carrier.CreateOrderRequest;
import com.ice.shippingservice.DTO.Carrier.CreateOrderResult;
import com.ice.shippingservice.DTO.Carrier.ItemLine;
import com.ice.shippingservice.DTO.Carrier.Recipient;
import com.ice.shippingservice.DTO.Carrier.TrackingEvent;
import com.ice.shippingservice.Enum.ShipmentStatus;
import com.ice.shippingservice.Exception.CarrierCannotCancelException;
import com.ice.shippingservice.Service.LabelStorage;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GhtkClientTest {

    @TempDir
    Path labelDir;

    private MockRestServiceServer server;
    private GhtkClient ghtk;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient http = builder.build();

        CarrierProperties props = new CarrierProperties();
        props.setMode("real");
        props.getResilience().setBackoffMs(0);
        props.getResilience().setMaxAttempts(1);
        props.getGhtk().setToken("token");
        props.getGhtk().setDefaultService("road");
        props.getGhtk().getLabel().setDir(labelDir.toString());
        props.getGhtk().getLabel().setPublicBaseUrl("http://localhost:8087/labels");

        ShippingProperties shipping = new ShippingProperties();
        shipping.getFrom().setName("Kho Q1");
        shipping.getFrom().setAddress("1 Le Loi");
        shipping.getFrom().setProvince("TP. HCM");
        shipping.getFrom().setDistrict("Quan 1");
        shipping.getFrom().setPhone("0900000000");

        ghtk = new GhtkClient(http, new CarrierCallExecutor(props), props, shipping, new LabelStorage(props));
    }

    @Test
    void createOrder_readsLabelAsTrackingCode() {
        server.expect(requestTo("/services/shipment/order"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"success":true,"message":"OK","order":{"label":"S1.A1.99","status_id":1,
                         "estimated_deliver_time":"2024-01-18"}}""", MediaType.APPLICATION_JSON));

        CreateOrderResult r = ghtk.createOrder(sampleCreateReq());

        assertThat(r.trackingCode()).isEqualTo("S1.A1.99");
        assertThat(r.status()).isEqualTo(ShipmentStatus.READY_TO_PICK);
        assertThat(r.estimatedDate()).isEqualTo("2024-01-18");
        server.verify();
    }

    @Test
    void getTracking_returnsSingleEventFromCurrentStatus() {
        server.expect(requestTo("/services/shipment/v2/S1.A1.99"))
                .andRespond(withSuccess("""
                        {"success":true,"order":{"label_id":"S1.A1.99","status":3,"status_text":"Đang giao",
                         "modified":"2024-01-17 09:00:00"}}""", MediaType.APPLICATION_JSON));

        List<TrackingEvent> events = ghtk.getTracking("S1.A1.99");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).status()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(events.get(0).description()).isEqualTo("Đang giao");
        server.verify();
    }

    @Test
    void cancelOrder_throwsWhenNotSuccessful() {
        server.expect(requestTo("/services/shipment/cancel/S1.A1.99"))
                .andRespond(withSuccess("{\"success\":false,\"message\":\"đơn đang giao\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> ghtk.cancelOrder("S1.A1.99"))
                .isInstanceOf(CarrierCannotCancelException.class)
                .hasMessageContaining("đơn đang giao");
        server.verify();
    }

    @Test
    void getLabelUrl_savesPdfAndReturnsPublicUrl() throws Exception {
        byte[] pdf = "%PDF-1.4 fake".getBytes();
        server.expect(requestTo("/services/label/S1.A1.99"))
                .andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF));

        String url = ghtk.getLabelUrl("S1.A1.99");

        assertThat(url).isEqualTo("http://localhost:8087/labels/S1.A1.99.pdf");
        assertThat(Files.readAllBytes(labelDir.resolve("S1.A1.99.pdf"))).isEqualTo(pdf);
        // (dấu chấm trong mã vận đơn được giữ nguyên trong tên file)
        server.verify();
    }

    @Test
    void calculateFee_returnsOneQuote() {
        server.expect(requestTo(Matchers.containsString("/services/shipment/fee")))
                .andRespond(withSuccess("""
                        {"success":true,"fee":{"name":"Đường bộ","fee":25000,"insurance_fee":0}}""",
                        MediaType.APPLICATION_JSON));

        var quotes = ghtk.calculateFee(new com.ice.shippingservice.DTO.Carrier.FeeRequest(
                1442, "21012", 1450, "21112",
                "TP. HCM", "Quận 1", "Phường Bến Nghé", 500, 20, 15, 10, 448200));

        assertThat(quotes).hasSize(1);
        assertThat(quotes.get(0).fee()).isEqualTo(25000);
        assertThat(quotes.get(0).serviceId()).isEqualTo("road");
        server.verify();
    }

    private CreateOrderRequest sampleCreateReq() {
        Recipient to = new Recipient("Nguyen Van A", "0901234567", "123 Le Loi",
                "TP. Hồ Chí Minh", "Quận 1", "Phường Bến Nghé", null, null);
        return new CreateOrderRequest("SN1", "road", to, 500, 20, 15, 10,
                0, 448200, "note", List.of(new ItemLine("Áo", 2, 250)));
    }
}
