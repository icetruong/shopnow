package com.qlda.userservice.DTO.Response.Auth;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RegisterResponse {
    private UUID userId;
    private String email;
}
