package com.ice.shippingservice.Util;

import com.ice.shippingservice.Enum.ShipmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GhnStatusMapperTest {

    @Test
    void map_returnsNullForUnknownWebhookStatus() {
        assertThat(GhnStatusMapper.map("khong_ton_tai")).isNull();
        assertThat(GhnStatusMapper.map(null)).isNull();
    }

    @Test
    void map_coversSixWebhookStatuses() {
        assertThat(GhnStatusMapper.map("ready_to_pick")).isEqualTo(ShipmentStatus.READY_TO_PICK);
        assertThat(GhnStatusMapper.map("delivering")).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(GhnStatusMapper.map("delivered")).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(GhnStatusMapper.map("cancel")).isEqualTo(ShipmentStatus.CANCELLED);
    }

    @Test
    void mapOrInTransit_mapsGranularDetailStatuses() {
        assertThat(GhnStatusMapper.mapOrInTransit("storing")).isEqualTo(ShipmentStatus.PICKED_UP);
        assertThat(GhnStatusMapper.mapOrInTransit("sorting")).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(GhnStatusMapper.mapOrInTransit("delivery_fail")).isEqualTo(ShipmentStatus.FAILED);
        assertThat(GhnStatusMapper.mapOrInTransit("returned")).isEqualTo(ShipmentStatus.RETURNED);
    }

    @Test
    void mapOrInTransit_fallsBackToInTransitForUnknown() {
        assertThat(GhnStatusMapper.mapOrInTransit("gia_tri_la")).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(GhnStatusMapper.mapOrInTransit(null)).isEqualTo(ShipmentStatus.IN_TRANSIT);
    }
}
