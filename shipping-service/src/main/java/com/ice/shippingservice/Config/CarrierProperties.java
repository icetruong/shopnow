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

    // getters/setters (hoặc @Data của Lombok)

    @Getter
    @Setter
    public static class Mock { private boolean autoAdvance = false; /* g/s */ }
    @Getter
    @Setter
    public static class Ghn  { private String token, shopId, defaultServiceId; /* g/s */ }
    @Getter
    @Setter
    public static class Ghtk { private String token; /* g/s */ }
}
