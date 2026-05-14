package com.qlda.userservice.Event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserRegisteredPayload {
    private String userId;
    private String email;
    private String fullName;
    private String provider;
    private String createdAt;
}
