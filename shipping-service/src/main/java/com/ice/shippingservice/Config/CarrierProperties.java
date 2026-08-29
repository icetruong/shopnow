package com.ice.shippingservice.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "carrier")
@Getter
@Setter
public class CarrierProperties {
    private String mode = "mock";
    private Mock mock = new Mock();
    private Ghn ghn = new Ghn();
    private Ghtk ghtk = new Ghtk();
    private Resilience resilience = new Resilience();

    @Getter
    @Setter
    public static class Mock {
        private boolean autoAdvance = false;
    }

    @Getter
    @Setter
    public static class Ghn {
        private String token;
        private String shopId;
        private String defaultServiceId;
        /** Base URL GHN (sandbox mặc định). */
        private String baseUrl = "https://dev-online-gateway.ghn.vn/shiip/public-api";
        /** Endpoint in nhãn A5 (khác host với base-url). */
        private String labelPrintUrl = "https://dev-online-gateway.ghn.vn/a5/public-api/printA5";
        /** Secret verify chữ ký webhook GHN khi carrier.mode=real. */
        private String webhookSecret;
    }

    @Getter
    @Setter
    public static class Ghtk {
        private String token;
        private String baseUrl = "https://services.giaohangtietkiem.vn";
        private String webhookSecret;
        /** GHTK không có service_id rõ ràng -> giá trị cố định ("road" đường bộ / "fly"). */
        private String defaultService = "road";
        private Label label = new Label();

        @Getter
        @Setter
        public static class Label {
            /** Thư mục local lưu file PDF nhãn tải từ GHTK. */
            private String dir = "labels";
            /** Base URL public để build link nhãn đã lưu. */
            private String publicBaseUrl = "http://localhost:8087/labels";
        }
    }

    /** Cấu hình retry / timeout / circuit breaker cho mọi call sang carrier (spec PHẦN 3). */
    @Getter
    @Setter
    public static class Resilience {
        private int maxAttempts = 3;
        private long backoffMs = 2000;
        private long connectTimeoutMs = 10000;
        private long readTimeoutMs = 10000;
        private int cbFailureThreshold = 5;
        private long cbOpenMs = 30000;
    }
}
