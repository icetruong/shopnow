package com.ice.shippingservice.Util;

import com.ice.shippingservice.Enum.ShipmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GhtkStatusMapperTest {

    @Test
    void map_webhookTableUnchanged() {
        assertThat(GhtkStatusMapper.map(-1)).isEqualTo(ShipmentStatus.CANCELLED);
        assertThat(GhtkStatusMapper.map(1)).isEqualTo(ShipmentStatus.READY_TO_PICK);
        assertThat(GhtkStatusMapper.map(2)).isEqualTo(ShipmentStatus.PICKED_UP);
        assertThat(GhtkStatusMapper.map(3)).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(GhtkStatusMapper.map(5)).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(GhtkStatusMapper.map(6)).isEqualTo(ShipmentStatus.FAILED);
        assertThat(GhtkStatusMapper.map(999)).isNull();
        assertThat(GhtkStatusMapper.map(null)).isNull();
    }

    @Test
    void mapOrInTransit_extendedTablePlusFallback() {
        assertThat(GhtkStatusMapper.mapOrInTransit(45)).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(GhtkStatusMapper.mapOrInTransit(10)).isEqualTo(ShipmentStatus.RETURNED);
        assertThat(GhtkStatusMapper.mapOrInTransit(9)).isEqualTo(ShipmentStatus.FAILED);
        assertThat(GhtkStatusMapper.mapOrInTransit(777)).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(GhtkStatusMapper.mapOrInTransit(null)).isEqualTo(ShipmentStatus.IN_TRANSIT);
    }
}
