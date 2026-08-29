package com.ice.shippingservice.Util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierDateParserTest {

    @Test
    void toLocalDate_handlesIsoOffsetInstantSpaceAndDateOnly() {
        assertThat(CarrierDateParser.toLocalDate("2024-01-17T00:00:00Z")).isEqualTo(LocalDate.of(2024, 1, 17));
        assertThat(CarrierDateParser.toLocalDate("2024-01-17T09:00:00+07:00")).isEqualTo(LocalDate.of(2024, 1, 17));
        assertThat(CarrierDateParser.toLocalDate("2024-01-17 09:00:00")).isEqualTo(LocalDate.of(2024, 1, 17));
        assertThat(CarrierDateParser.toLocalDate("2024-01-17")).isEqualTo(LocalDate.of(2024, 1, 17));
    }

    @Test
    void toLocalDate_returnsNullWhenUnparseable() {
        assertThat(CarrierDateParser.toLocalDate("Sáng thứ 5")).isNull();
        assertThat(CarrierDateParser.toLocalDate(null)).isNull();
        assertThat(CarrierDateParser.toLocalDate("")).isNull();
    }

    @Test
    void toInstant_fallbackNowControlsNullBehaviour() {
        assertThat(CarrierDateParser.toInstant("2024-01-17T09:00:00Z", false)).isNotNull();
        assertThat(CarrierDateParser.toInstant("rác", false)).isNull();
        assertThat(CarrierDateParser.toInstant("rác", true)).isNotNull();
    }
}
