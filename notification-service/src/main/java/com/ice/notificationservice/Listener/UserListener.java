package com.ice.notificationservice.Listener;

import com.ice.notificationservice.DTO.Event.Consumer.KafkaEvent;
import com.ice.notificationservice.DTO.Event.Consumer.UserPasswordResetPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserPasswordResetListener {
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user.password_reset_requested", groupId = "notification-service")
    public void handlePasswordReset(String message)
    {
        KafkaEvent<UserPasswordResetPayload> kafkaEvent
                = objectMapper.readValue(message, new TypeReference<KafkaEvent<UserPasswordResetPayload>>() {});

        UserPasswordResetPayload payload = kafkaEvent.getPayload();

        // TODO: mai làm tiếp
    }
}
