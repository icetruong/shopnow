package com.ice.shippingservice.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * RestClient riêng cho từng carrier: base URL + timeout theo carrier.resilience.*.
 * Luôn tạo bean (kể cả carrier.mode=mock) - GhnClient/GhtkClient chỉ được factory dùng khi mode=real.
 */
@Configuration
public class CarrierHttpConfig {

    @Bean
    public RestClient ghnRestClient(CarrierProperties props) {
        return build(props.getGhn().getBaseUrl(), props.getResilience());
    }

    @Bean
    public RestClient ghtkRestClient(CarrierProperties props) {
        return build(props.getGhtk().getBaseUrl(), props.getResilience());
    }

    private RestClient build(String baseUrl, CarrierProperties.Resilience r) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(r.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(r.getReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
