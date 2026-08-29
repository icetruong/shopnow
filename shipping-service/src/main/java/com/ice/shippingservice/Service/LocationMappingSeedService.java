package com.ice.shippingservice.Service;

import com.ice.shippingservice.Client.GhnMasterDataClient;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnDistrict;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnProvince;
import com.ice.shippingservice.DTO.Carrier.Ghn.GhnWard;
import com.ice.shippingservice.Entity.LocationMapping;
import com.ice.shippingservice.Repository.LocationMappingRepo;
import com.ice.shippingservice.Util.AddressNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Bước 7: seed / cập nhật bảng location_mappings từ GHN master-data.
 * Duyệt tỉnh -> huyện -> xã, upsert theo unique (province_name, district_name, ward_name).
 *
 * <p>Lưu ý: chạy full VN ra ~10k+ dòng và nhiều call GHN - nên gọi qua job định kỳ / admin chủ động,
 * không phải mỗi request. Không bọc 1 transaction lớn: saveAll theo từng tỉnh.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocationMappingSeedService {

    private final GhnMasterDataClient masterData;
    private final LocationMappingRepo repo;

    public record SeedResult(int provinces, int districts, int wards, int inserted, int updated) {
    }

    public SeedResult sync() {
        List<GhnProvince> provinces = masterData.provinces();
        int districtCount = 0;
        int wardCount = 0;
        int inserted = 0;
        int updated = 0;

        for (GhnProvince p : provinces) {
            if (isBlank(p.provinceName())) {
                continue;
            }
            List<GhnDistrict> districts = masterData.districts(p.provinceId());
            districtCount += districts.size();

            List<LocationMapping> batch = new ArrayList<>();
            for (GhnDistrict d : districts) {
                if (isBlank(d.districtName())) {
                    continue;
                }
                List<GhnWard> wards = masterData.wards(d.districtId());
                for (GhnWard w : wards) {
                    if (isBlank(w.wardName()) || isBlank(w.wardCode())) {
                        continue;
                    }
                    wardCount++;
                    boolean isNew = upsert(batch, p, d, w);
                    if (isNew) {
                        inserted++;
                    } else {
                        updated++;
                    }
                }
            }
            repo.saveAll(batch);
            log.info("Seed location_mappings: tỉnh '{}' xong ({} huyện, {} dòng)",
                    p.provinceName(), districts.size(), batch.size());
        }

        log.info("Seed location_mappings HOÀN TẤT: {} tỉnh / {} huyện / {} xã | +{} mới, ~{} cập nhật",
                provinces.size(), districtCount, wardCount, inserted, updated);
        return new SeedResult(provinces.size(), districtCount, wardCount, inserted, updated);
    }

    /** true = dòng mới, false = cập nhật dòng cũ. Thêm entity đã dựng vào {@code batch}. */
    private boolean upsert(List<LocationMapping> batch, GhnProvince p, GhnDistrict d, GhnWard w) {
        return repo.findByProvinceNameAndDistrictNameAndWardName(
                        p.provinceName(), d.districtName(), w.wardName())
                .map(existing -> {
                    existing.setProvinceNameNormalized(AddressNormalizer.normalize(p.provinceName()));
                    existing.setDistrictNameNormalized(AddressNormalizer.normalize(d.districtName()));
                    existing.setWardNameNormalized(AddressNormalizer.normalize(w.wardName()));
                    existing.setGhnProvinceId(p.provinceId());
                    existing.setGhnDistrictId(d.districtId());
                    existing.setGhnWardCode(w.wardCode());
                    existing.setUpdatedAt(LocalDateTime.now());
                    batch.add(existing);
                    return false;
                })
                .orElseGet(() -> {
                    batch.add(LocationMapping.builder()
                            .provinceName(p.provinceName())
                            .districtName(d.districtName())
                            .wardName(w.wardName())
                            .provinceNameNormalized(AddressNormalizer.normalize(p.provinceName()))
                            .districtNameNormalized(AddressNormalizer.normalize(d.districtName()))
                            .wardNameNormalized(AddressNormalizer.normalize(w.wardName()))
                            .ghnProvinceId(p.provinceId())
                            .ghnDistrictId(d.districtId())
                            .ghnWardCode(w.wardCode())
                            .updatedAt(LocalDateTime.now())
                            .build());
                    return true;
                });
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
