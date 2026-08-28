package com.ice.shippingservice.Util;

import com.ice.shippingservice.Enum.ShipmentStatus;

import java.util.Map;

/**
 * State machine của shipment - webhook chỉ được đẩy status TIẾN, không lùi.
 *
 * PENDING -> READY_TO_PICK -> PICKED_UP -> IN_TRANSIT -> DELIVERED
 *                                              \-> FAILED -> RETURNED
 * CANCELLED: chỉ từ PENDING hoặc READY_TO_PICK.
 */
public final class ShipmentStatusMachine {

    /** Rank cho đoạn tuyến tính. */
    private static final Map<ShipmentStatus, Integer> RANK = Map.of(
            ShipmentStatus.PENDING, 0,
            ShipmentStatus.READY_TO_PICK, 1,
            ShipmentStatus.PICKED_UP, 2,
            ShipmentStatus.IN_TRANSIT, 3,
            ShipmentStatus.DELIVERED, 4
    );

    private ShipmentStatusMachine() {
    }

    public static boolean canAdvance(ShipmentStatus current, ShipmentStatus next) {
        if (current == next) {
            return false;                       // no-op
        }
        if (isTerminal(current)) {
            return false;                       // DELIVERED / CANCELLED / RETURNED: khoá
        }

        switch (next) {
            case CANCELLED -> {
                return current == ShipmentStatus.PENDING
                        || current == ShipmentStatus.READY_TO_PICK;
            }
            case FAILED -> {
                return current == ShipmentStatus.PICKED_UP
                        || current == ShipmentStatus.IN_TRANSIT;
            }
            case RETURNED -> {
                return current == ShipmentStatus.FAILED;
            }
            default -> {
                Integer c = RANK.get(current);
                Integer n = RANK.get(next);
                return c != null && n != null && n > c;   // chỉ cho tiến
            }
        }
    }

    private static boolean isTerminal(ShipmentStatus s) {
        return s == ShipmentStatus.DELIVERED
                || s == ShipmentStatus.CANCELLED
                || s == ShipmentStatus.RETURNED;
    }
}
