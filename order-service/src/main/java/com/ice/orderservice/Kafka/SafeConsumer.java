package com.ice.orderservice.Kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Bọc phần xử lý của mỗi @KafkaListener.
 *
 * Nếu {@code body} ném lỗi -> log ERROR kèm TOÀN BỘ message rồi NUỐT (không ném lại).
 * Listener return êm => Spring Kafka commit offset => KHÔNG retry, KHÔNG nghẽn partition.
 *
 * Đánh đổi: message lỗi bị BỎ HẲN (không retry, không DLT). Dựa vào:
 *  - log [KAFKA-DROP] đủ to để thấy và replay tay;
 *  - SagaRecoveryScheduler (việc B) dọn đơn bị kẹt hậu quả;
 *  - IdempotencyService (việc D) để lần replay tay không xử lý trùng.
 *
 * LƯU Ý: {@code body} phải gọi sang một bean @Transactional KHÁC (không phải method
 * cùng class listener). Có vậy khi lỗi thì DB rollback xong exception mới nổi lên tới
 * đây; nếu try/catch nằm ngay trong method @Transactional thì nuốt lỗi = commit dở dang.
 */
@Component
@Slf4j
public class SafeConsumer {

    public void run(String topic, String rawMessage, Runnable body) {
        try {
            body.run();
        } catch (Exception ex) {
            log.error("[KAFKA-DROP] topic={} message={} -- bỏ qua message sau lỗi", topic, rawMessage, ex);
        }
    }
}
