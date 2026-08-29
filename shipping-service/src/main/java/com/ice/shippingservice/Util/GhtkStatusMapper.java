package com.ice.shippingservice.Util;

import com.ice.shippingservice.Enum.ShipmentStatus;

import java.util.Map;

/**
 * Map status_id (số) của GHTK -> ShipmentStatus nội bộ.
 *
 * <p>{@link #map(Integer)}: cho webhook (spec mục 3) - 6 id chuẩn; id lạ -> null (ack 200 + bỏ qua).
 * <p>{@link #mapOrInTransit(Integer)}: cho khi đọc tracking từ API (bảng mở rộng mục 4);
 * id lạ -> IN_TRANSIT.
 *
 * <p>VERIFY: bảng status_id GHTK phải đối chiếu tài liệu chính thức khi tích hợp thật.
 */
public final class GhtkStatusMapper {

    /** 6 id webhook chuẩn (spec mục 3) - GIỮ NGUYÊN, là hợp đồng webhook. */
    private static final Map<Integer, ShipmentStatus> WEBHOOK_MAP = Map.of(
            -1, ShipmentStatus.CANCELLED,
            1, ShipmentStatus.READY_TO_PICK,
            2, ShipmentStatus.PICKED_UP,
            3, ShipmentStatus.IN_TRANSIT,
            5, ShipmentStatus.DELIVERED,
            6, ShipmentStatus.FAILED
    );

    /** Bảng mở rộng cho tracking API (superset của WEBHOOK_MAP). */
    private static final Map<Integer, ShipmentStatus> EXTENDED_MAP = Map.ofEntries(
            Map.entry(-1, ShipmentStatus.CANCELLED),
            Map.entry(1, ShipmentStatus.READY_TO_PICK),
            Map.entry(2, ShipmentStatus.PICKED_UP),
            Map.entry(3, ShipmentStatus.IN_TRANSIT),
            Map.entry(4, ShipmentStatus.IN_TRANSIT),
            Map.entry(5, ShipmentStatus.DELIVERED),
            Map.entry(45, ShipmentStatus.DELIVERED),
            Map.entry(6, ShipmentStatus.FAILED),
            Map.entry(9, ShipmentStatus.FAILED),
            Map.entry(10, ShipmentStatus.RETURNED),
            Map.entry(11, ShipmentStatus.RETURNED),
            Map.entry(20, ShipmentStatus.RETURNED),
            Map.entry(21, ShipmentStatus.RETURNED)
    );

    private GhtkStatusMapper() {
    }

    /** null = id lạ -> caller ack 200 + bỏ qua. */
    public static ShipmentStatus map(Integer statusId) {
        return statusId == null ? null : WEBHOOK_MAP.get(statusId);
    }

    /** Không bao giờ null: id lạ -> IN_TRANSIT. */
    public static ShipmentStatus mapOrInTransit(Integer statusId) {
        if (statusId == null) {
            return ShipmentStatus.IN_TRANSIT;
        }
        return EXTENDED_MAP.getOrDefault(statusId, ShipmentStatus.IN_TRANSIT);
    }
}
