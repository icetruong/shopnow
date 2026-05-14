package com.qlda.userservice.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(String topic, String key, Object event)
    {
        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null)
                        log.error("Kafka publish failed | topic={} key={} error={}", topic, key, ex.getMessage());
                    else
                        log.info("Kafka published | topic={} key={} offset={}", topic, key,
                                result.getRecordMetadata().offset());
                });


    }
}
