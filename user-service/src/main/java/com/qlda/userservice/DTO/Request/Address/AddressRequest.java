package com.qlda.userservice.DTO.Request.Address;

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
public class AddressRequest {
    @NotBlank
    private String province;
    @NotBlank
    private String district;
    @NotBlank
    private String ward;
    @NotBlank
    private String streetDetail;
    @NotNull
    private boolean isDefault;
}

