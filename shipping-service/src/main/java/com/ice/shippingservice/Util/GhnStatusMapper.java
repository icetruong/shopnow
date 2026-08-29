package com.ice.shippingservice.Util;

import com.ice.shippingservice.Enum.ShipmentStatus;

import java.util.Map;

/**
 * Map status string của GHN -> ShipmentStatus nội bộ.
 *
 * <p>{@link #map(String)}: dùng cho webhook (spec mục 3) - 6 status chuẩn; giá trị lạ -> null
 * (controller ack 200 + bỏ qua).
 *
 * <p>{@link #mapOrInTransit(String)}: dùng khi đọc {@code log[]} từ /v2/shipping-order/detail
 * (spec mục 4, ~30 status granular) - giá trị lạ -> IN_TRANSIT (không ném lỗi).
 */
public final class GhnStatusMapper {

    private static final Map<String, ShipmentStatus> MAP = Map.ofEntries(
            // --- 6 status webhook chuẩn (spec mục 3) - GIỮ NGUYÊN, là hợp đồng webhook ---
            Map.entry("ready_to_pick", ShipmentStatus.READY_TO_PICK),
            Map.entry("picking", ShipmentStatus.PICKED_UP),
            Map.entry("delivering", ShipmentStatus.IN_TRANSIT),
            Map.entry("delivered", ShipmentStatus.DELIVERED),
            Map.entry("return", ShipmentStatus.RETURNED),
            Map.entry("cancel", ShipmentStatus.CANCELLED),

            // --- status granular từ /detail log[] (spec mục 4) ---
            Map.entry("money_collect_picking", ShipmentStatus.READY_TO_PICK),
            Map.entry("picked", ShipmentStatus.PICKED_UP),
            Map.entry("storing", ShipmentStatus.PICKED_UP),
            Map.entry("transporting", ShipmentStatus.IN_TRANSIT),
            Map.entry("sorting", ShipmentStatus.IN_TRANSIT),
            Map.entry("money_collect_delivering", ShipmentStatus.IN_TRANSIT),
            Map.entry("delivery_fail", ShipmentStatus.FAILED),
            Map.entry("exception", ShipmentStatus.FAILED),
            Map.entry("damage", ShipmentStatus.FAILED),
            Map.entry("lost", ShipmentStatus.FAILED),
            Map.entry("return_fail", ShipmentStatus.FAILED),
            Map.entry("waiting_to_return", ShipmentStatus.RETURNED),
            Map.entry("return_transporting", ShipmentStatus.RETURNED),
            Map.entry("return_sorting", ShipmentStatus.RETURNED),
            Map.entry("returning", ShipmentStatus.RETURNED),
            Map.entry("returned", ShipmentStatus.RETURNED)
    );

    private GhnStatusMapper() {
    }

    /** null = status lạ -> caller ack 200 + bỏ qua. */
    public static ShipmentStatus map(String ghnStatus) {
        return ghnStatus == null ? null : MAP.get(ghnStatus.toLowerCase());
    }

    /** Không bao giờ null: status lạ -> IN_TRANSIT (spec mục 4). */
    public static ShipmentStatus mapOrInTransit(String ghnStatus) {
        ShipmentStatus s = map(ghnStatus);
        return s != null ? s : ShipmentStatus.IN_TRANSIT;
    }
}
