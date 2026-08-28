package com.ice.shippingservice.Service;

import com.ice.shippingservice.DTO.Location.DistrictResponse;
import com.ice.shippingservice.DTO.Location.DistrictSeed;
import com.ice.shippingservice.DTO.Location.ProvinceResponse;
import com.ice.shippingservice.DTO.Location.ProvinceSeed;
import com.ice.shippingservice.DTO.Location.WardResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Proxy master-data địa giới cho FE đổ dropdown địa chỉ.
 *
 * NGUỒN HIỆN TẠI: file tĩnh resources/location/vn-locations.json.
 * Khi có GHN_TOKEN: thay phần đọc file bằng GhnMasterDataClient gọi GHN master-data,
 * giữ nguyên phần cache + response.
 */
@Service
@Slf4j
public class LocationService {

    private static final String DATA_FILE = "location/vn-locations.json";
    private static final String KEY_PROVINCES = "shipping:provinces";
    private static final String KEY_DISTRICTS_PREFIX = "shipping:districts:";
    private static final String KEY_WARDS_PREFIX = "shipping:wards:";
    private static final Duration TTL = Duration.ofHours(24);

    private final RedisTemplate<String, Object> redisTemplate;
    private final List<ProvinceSeed> data;

    public LocationService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        try (InputStream is = new ClassPathResource(DATA_FILE).getInputStream()) {
            this.data = objectMapper.readValue(is, new TypeReference<List<ProvinceSeed>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Không đọc được " + DATA_FILE, e);
        }
        log.info("Nạp {} tỉnh/thành từ {}", data.size(), DATA_FILE);
    }

    @SuppressWarnings("unchecked")
    public List<ProvinceResponse> getProvinces() {
        Object cached = redisTemplate.opsForValue().get(KEY_PROVINCES);
        if (cached != null) {
            return (List<ProvinceResponse>) cached;
        }

        List<ProvinceResponse> result = new ArrayList<>(data.stream()
                .map(p -> new ProvinceResponse(p.provinceId(), p.provinceName()))
                .toList());

        redisTemplate.opsForValue().set(KEY_PROVINCES, result, TTL);
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<DistrictResponse> getDistricts(int provinceId) {
        String key = KEY_DISTRICTS_PREFIX + provinceId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return (List<DistrictResponse>) cached;
        }

        List<DistrictResponse> result = new ArrayList<>(data.stream()
                .filter(p -> p.provinceId() == provinceId)
                .findFirst()
                .map(ProvinceSeed::districts)
                .orElseGet(List::of)
                .stream()
                .map(d -> new DistrictResponse(d.districtId(), d.districtName()))
                .toList());

        redisTemplate.opsForValue().set(key, result, TTL);
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<WardResponse> getWards(int districtId) {
        String key = KEY_WARDS_PREFIX + districtId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return (List<WardResponse>) cached;
        }

        List<WardResponse> result = new ArrayList<>(data.stream()
                .flatMap(p -> p.districts().stream())
                .filter(d -> d.districtId() == districtId)
                .findFirst()
                .map(DistrictSeed::wards)
                .orElseGet(List::of)
                .stream()
                .map(w -> new WardResponse(w.wardCode(), w.wardName()))
                .toList());

        redisTemplate.opsForValue().set(key, result, TTL);
        return result;
    }
}
