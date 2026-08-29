package com.ice.shippingservice.Config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/** Serve file nhãn PDF đã lưu (LabelStorage) tại /labels/**. */
@Configuration
@RequiredArgsConstructor
public class LabelResourceConfig implements WebMvcConfigurer {

    private final CarrierProperties carrierProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String dir = carrierProperties.getGhtk().getLabel().getDir();
        String location = Path.of(dir).toAbsolutePath().toUri().toString();   // "file:///.../labels/"
        registry.addResourceHandler("/labels/**")
                .addResourceLocations(location);
    }
}
