package com.ice.shippingservice.Service;

import com.ice.shippingservice.Config.CarrierProperties;
import com.ice.shippingservice.Exception.CarrierApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Lưu file PDF nhãn tải từ carrier (GHTK trả file trực tiếp) ra thư mục local,
 * trả về URL public ({@code carrier.ghtk.label.public-base-url}).
 * D3: bản đơn giản cho môi trường học tập - production thay bằng S3/MinIO.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LabelStorage {

    private final CarrierProperties carrierProperties;

    public String save(String trackingCode, byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new CarrierApiException("Nhãn PDF rỗng cho " + trackingCode);
        }
        CarrierProperties.Ghtk.Label cfg = carrierProperties.getGhtk().getLabel();
        String fileName = sanitize(trackingCode) + ".pdf";
        try {
            Path dir = Path.of(cfg.getDir());
            Files.createDirectories(dir);
            Path file = dir.resolve(fileName);
            Files.write(file, pdf,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            log.info("Đã lưu nhãn {} ({} bytes)", file, pdf.length);
        } catch (IOException e) {
            throw new CarrierApiException("Không lưu được nhãn PDF cho " + trackingCode + ": " + e.getMessage());
        }
        return trimTrailingSlash(cfg.getPublicBaseUrl()) + "/" + fileName;
    }

    private static String sanitize(String s) {
        // giữ chữ/số/. _ - (mã vận đơn hay có dấu chấm); ký tự path (/ \) bị thay _
        return s == null ? "label" : s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
