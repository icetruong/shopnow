package com.qlda.userservice.Event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserPasswordResetPayload {
    private String userId;
    private String email;
    private String resetToken;
    private String expiresAt;
}
