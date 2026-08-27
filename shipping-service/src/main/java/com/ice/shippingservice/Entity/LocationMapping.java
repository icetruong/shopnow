package com.ice.shippingservice.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table(
        name = "location_mappings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "idx_location_mappings_exact",
                        columnNames = {"province_name", "district_name", "ward_name"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_location_mappings_normalized",
                        columnList = "province_name_normalized, district_name_normalized, ward_name_normalized"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Tên gốc như GHN trả. */
    @Column(name = "province_name", nullable = false, length = 100)
    private String provinceName;

    @Column(name = "district_name", nullable = false, length = 100)
    private String districtName;

    @Column(name = "ward_name", nullable = false, length = 100)
    private String wardName;

    /** Bỏ dấu, lowercase, bỏ tiền tố "TP."/"Tỉnh"/"Quận"/"Phường" - để khớp gần đúng. */
    @Column(name = "province_name_normalized", nullable = false, length = 100)
    private String provinceNameNormalized;

    @Column(name = "district_name_normalized", nullable = false, length = 100)
    private String districtNameNormalized;

    @Column(name = "ward_name_normalized", nullable = false, length = 100)
    private String wardNameNormalized;

    @Column(name = "ghn_province_id", nullable = false)
    private Integer ghnProvinceId;

    @Column(name = "ghn_district_id", nullable = false)
    private Integer ghnDistrictId;

    @Column(name = "ghn_ward_code", nullable = false, length = 20)
    private String ghnWardCode;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
