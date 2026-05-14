package com.qlda.userservice.DTO.Request.Auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {
    @NotBlank(message = "email must not blank")
    @Email(message = "email is invalid")
    private String email;

    @NotBlank(message = "Password must not be blank")
    @Size(min = 8, max = 32, message = "Password must be between 8 and 32 characters")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).+$",
            message = "Password must contain at least 1 letter and 1 number"
    )
    private String password;

    @NotBlank(message = "full name not blank")
    @Size(min = 2, max = 50, message = "full name must be between 2 and 50 characters")
    private String fullName;
    private String phone;
}
