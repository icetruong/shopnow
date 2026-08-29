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
    private String defaultCarrier;
    private Integer defaultItemWeightGrams;
    private Track track = new Track();

    @Getter
    @Setter
    public static class Track
    {
        /** GET /track (mode=real): chỉ hỏi carrier realtime nếu bản ghi tracking mới nhất cũ hơn ngần này. */
        private int realtimeRefreshMinutes = 30;
    }

    @Getter
    @Setter
    public static class From
    {
        private Integer districtId;
        private String wardCode;
        // Điểm gửi dạng text - GHTK cần tên, không dùng id
        private String name;
        private String phone;
        private String address;
        private String province;
        private String district;
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
