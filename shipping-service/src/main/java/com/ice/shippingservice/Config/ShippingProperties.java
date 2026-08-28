package com.ice.shippingservice.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "shipping")
@Getter
@Setter
public class ShippingProperties {
    private From from = new From();
    private DefaultPackage defaultPackage = new DefaultPackage();

    @Getter
    @Setter
    public static class From
    {
        private Integer districtId;
        private String wardCode;
    }

    @Getter
    @Setter
    public static class DefaultPackage
    {
        private Integer length;
        private Integer width;
        private Integer height;
    }
}
