package com.ice.shippingservice.Controller;

import com.ice.shippingservice.DTO.Response.Common.ApiResponse;
import com.ice.shippingservice.Service.LocationMappingSeedService;
import com.ice.shippingservice.Service.LocationMappingSeedService.SeedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ROLE_ADMIN (qua SecurityConfig /api/v1/admin/**). Chạy tay seed location_mappings từ GHN
 * master-data - dùng khi mới có GHN_TOKEN, hoặc sau khi GHN cập nhật địa giới.
 */
@RestController
@RequestMapping("/api/v1/admin/shipping/location-mappings")
@RequiredArgsConstructor
public class AdminLocationMappingController {

    private final LocationMappingSeedService seedService;

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<SeedResult>> sync() {
        return ResponseEntity.ok(
                ApiResponse.success("Seed location_mappings hoàn tất", seedService.sync()));
    }
}
