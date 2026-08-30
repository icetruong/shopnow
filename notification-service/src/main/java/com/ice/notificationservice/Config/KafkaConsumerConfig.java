package com.ice.notificationservice.Config;

import com.ice.notificationservice.Exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

/**
 * Xử lý lỗi CHUNG cho mọi @KafkaListener.
 *
 * Vì sao cần class này:
 *  - @RestControllerAdvice (HandleGlobalException) chỉ áp cho request HTTP,
 *    KHÔNG bắt được exception ném ra từ consumer Kafka.
 *  - Mặc định Spring Kafka retry record lỗi 10 lần không delay rồi bỏ luôn -> mất event.
 *
 * Cấu hình ở đây:
 *  - Retry 3 lần, cách nhau 2s.
 *  - Hết retry -> đẩy record sang topic "<tên-topic-gốc>.DLT" rồi commit offset (không kẹt consumer).
 *  - Lỗi "retry cũng vô ích" -> bỏ qua retry, vào DLT ngay.
 *
 * Bean DefaultErrorHandler này được Spring Boot tự gắn vào listener container factory
 * (chỉ cần là bean CommonErrorHandler duy nhất).
 *
 * Lưu ý vận hành: các topic *.DLT cần tồn tại (hoặc broker bật auto-create ở môi trường dev).
 */
@Configuration
@Slf4j
public class KafkaConsumerConfig {

    private static final long RETRY_INTERVAL_MS = 2_000L;
    private static final long MAX_RETRIES = 3L;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    log.error("Đẩy vào DLT: topic={} partition={} offset={} nguyên nhân={}",
                            record.topic(), record.partition(), record.offset(), ex.toString());
                    // partition = -1 -> để Kafka tự chọn, tránh lỗi khi topic .DLT khác số partition
                    return new TopicPartition(record.topic() + ".DLT", -1);
                });

        DefaultErrorHandler handler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));

        // Lỗi không đáng retry -> vào DLT luôn:
        handler.addNotRetryableExceptions(
                JacksonException.class,          // message JSON hỏng, parse mãi vẫn hỏng
                IllegalArgumentException.class,  // vd UUID.fromString(...) sai định dạng
                ResourceNotFoundException.class  // user/order thật sự không tồn tại
        );

        // KHÔNG liệt kê UserServiceUnavailableException / OrderServiceUnavailableException
        // -> đó là lỗi tạm thời, vẫn cần được retry.

        return handler;
    }
}
