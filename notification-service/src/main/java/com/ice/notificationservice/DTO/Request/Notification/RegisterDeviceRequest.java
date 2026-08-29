package com.ice.notificationservice.DTO.Request.Notification;

import com.ice.notificationservice.Enum.DeviceTokenPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RegisterDeviceRequest {
    @NotBlank(message = "device Token must be not blank")
    private String deviceToken;

    @NotNull(message = "platform must be not null")
    private DeviceTokenPlatform platform;

    @NotBlank(message = "device name must be not blank")
    private String deviceName;
}
