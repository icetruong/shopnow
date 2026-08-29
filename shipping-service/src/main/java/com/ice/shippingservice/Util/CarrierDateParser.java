package com.ice.shippingservice.Util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Parse ngày/giờ "lỏng" từ carrier - GHN/GHTK trả nhiều định dạng khác nhau
 * ("2024-01-17T00:00:00Z", "2024-01-17 09:00:00", "2024-01-17", ...).
 * Không parse được -> trả fallback thay vì ném lỗi (dữ liệu tracking không nên làm fail cả flow).
 */
public final class CarrierDateParser {

    private static final DateTimeFormatter SPACE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CarrierDateParser() {
    }

    /** null nếu không parse được (caller quyết định để null hay bỏ qua). */
    public static LocalDate toLocalDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        try {
            return OffsetDateTime.parse(s).toLocalDate();
        } catch (RuntimeException ignored) {
            // thử tiếp
        }
        try {
            return Instant.parse(s).atZone(ZoneOffset.UTC).toLocalDate();
        } catch (RuntimeException ignored) {
            // thử tiếp
        }
        try {
            return LocalDateTime.parse(s, SPACE_DATETIME).toLocalDate();
        } catch (RuntimeException ignored) {
            // thử tiếp
        }
        if (s.length() >= 10) {
            try {
                return LocalDate.parse(s.substring(0, 10));
            } catch (RuntimeException ignored) {
                // bỏ
            }
        }
        return null;
    }

    /** fallbackNow=true -> không parse được thì trả Instant.now(); ngược lại trả null. */
    public static Instant toInstant(String raw, boolean fallbackNow) {
        if (raw != null && !raw.isBlank()) {
            String s = raw.trim();
            try {
                return OffsetDateTime.parse(s).toInstant();
            } catch (RuntimeException ignored) {
                // thử tiếp
            }
            try {
                return Instant.parse(s);
            } catch (RuntimeException ignored) {
                // thử tiếp
            }
            try {
                return LocalDateTime.parse(s, SPACE_DATETIME).toInstant(ZoneOffset.UTC);
            } catch (RuntimeException ignored) {
                // thử tiếp
            }
        }
        return fallbackNow ? Instant.now() : null;
    }
}
