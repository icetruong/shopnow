package com.ice.shippingservice.Schedule;

import com.ice.shippingservice.Service.LocationMappingSeedService;
import com.ice.shippingservice.Service.LocationMappingSeedService.SeedResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job định kỳ đồng bộ location_mappings từ GHN master-data (bước 7).
 * Tắt mặc định: bật bằng {@code shipping.location-sync.enabled=true}; lịch: {@code shipping.location-sync.cron}.
 */
@Component
@ConditionalOnProperty(name = "shipping.location-sync.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class LocationMappingSyncJob {

    private final LocationMappingSeedService seedService;

    @Scheduled(cron = "${shipping.location-sync.cron}")
    public void run() {
        log.info("LocationMappingSyncJob: bắt đầu đồng bộ location_mappings từ GHN master-data");
        try {
            SeedResult r = seedService.sync();
            log.info("LocationMappingSyncJob xong: {} tỉnh, +{} mới, ~{} cập nhật",
                    r.provinces(), r.inserted(), r.updated());
        } catch (RuntimeException e) {
            log.error("LocationMappingSyncJob thất bại - giữ dữ liệu location_mappings hiện có", e);
        }
    }
}
