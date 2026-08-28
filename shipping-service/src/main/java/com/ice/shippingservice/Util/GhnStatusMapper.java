package com.ice.shippingservice.Util;

import com.ice.shippingservice.Enum.ShipmentStatus;

import java.util.Map;

/** Map status string của GHN -> ShipmentStatus nội bộ (theo bảng trong ApiSpec mục 3). */
public final class GhnStatusMapper {

    private static final Map<String, ShipmentStatus> MAP = Map.of(
            "ready_to_pick", ShipmentStatus.READY_TO_PICK,
            "picking",       ShipmentStatus.PICKED_UP,
            "delivering",    ShipmentStatus.IN_TRANSIT,
            "delivered",     ShipmentStatus.DELIVERED,
            "return",        ShipmentStatus.RETURNED,
            "cancel",        ShipmentStatus.CANCELLED
    );

    private GhnStatusMapper() {
    }

    /** null = status lạ -> caller ack 200 + bỏ qua. */
    public static ShipmentStatus map(String ghnStatus) {
        return ghnStatus == null ? null : MAP.get(ghnStatus.toLowerCase());
    }
}
