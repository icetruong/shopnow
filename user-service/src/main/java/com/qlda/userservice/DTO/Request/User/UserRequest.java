package com.qlda.userservice.DTO.Request.User;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserRequest {
    @NotBlank(message = "full name not blank")
    @Size(min = 2, max = 50, message = "full name must be between 2 and 50 characters")
    private String fullName;
    @NotBlank(message = "phone not blank")
    @Pattern(
            regexp = "^(0|\\+84)(\\d{9})$",
            message = "phone must correct format phone in Viet Name"
    )
    private String phone;
}
