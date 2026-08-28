package com.ice.shippingservice.Util;

import com.ice.shippingservice.Enum.ShipmentStatus;

import java.util.Map;

/** Map status_id (số) của GHTK -> ShipmentStatus nội bộ (theo bảng trong ApiSpec mục 3). */
public final class GhtkStatusMapper {

    private static final Map<Integer, ShipmentStatus> MAP = Map.of(
            -1, ShipmentStatus.CANCELLED,
             1, ShipmentStatus.READY_TO_PICK,
             2, ShipmentStatus.PICKED_UP,
             3, ShipmentStatus.IN_TRANSIT,
             5, ShipmentStatus.DELIVERED,
             6, ShipmentStatus.FAILED
    );

    private GhtkStatusMapper() {
    }

    /** null = status_id lạ -> caller ack 200 + bỏ qua. */
    public static ShipmentStatus map(Integer statusId) {
        return statusId == null ? null : MAP.get(statusId);
    }
}
