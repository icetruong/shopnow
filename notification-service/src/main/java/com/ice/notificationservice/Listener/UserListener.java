package com.ice.notificationservice.Listener;

import com.ice.notificationservice.DTO.Event.Consumer.KafkaEvent;
import com.ice.notificationservice.DTO.Event.Consumer.UserPasswordResetPayload;
import com.ice.notificationservice.DTO.Event.Consumer.UserRegisteredPayload;
import com.ice.notificationservice.Service.UserEventNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserListener {
    private final ObjectMapper objectMapper;
    private final UserEventNotificationService userEventNotificationService;

    @KafkaListener(topics = "user.password_reset_requested", groupId = "notification-service")
    public void handlePasswordReset(String message)
    {
        KafkaEvent<UserPasswordResetPayload> kafkaEvent
                = objectMapper.readValue(message, new TypeReference<KafkaEvent<UserPasswordResetPayload>>() {});

        userEventNotificationService.onPasswordResetRequested(kafkaEvent.getEventId(), kafkaEvent.getPayload());
    }

    @KafkaListener(topics = "user.registered", groupId = "notification-service")
    public void handleRegistered(String message)
    {
        KafkaEvent<UserRegisteredPayload> kafkaEvent =
                objectMapper.readValue(message, new TypeReference<KafkaEvent<UserRegisteredPayload>>() {});

        userEventNotificationService.onUserRegistered(kafkaEvent.getEventId(), kafkaEvent.getPayload());
    }
}
