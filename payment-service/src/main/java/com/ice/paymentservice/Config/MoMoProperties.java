package com.ice.paymentservice.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "momo")
public class MoMoProperties {
    private String partnerCode;
    private String accessKey;
    private String secretKey;
    private String endpoint;
    private String refundEndpoint;
    private String queryEndpoint;
    private String ipnUrl;
    private String redirectUrl;
    private String requestType;
    private String lang;
}
