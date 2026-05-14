package com.qlda.userservice.DTO.Response.User;

import com.qlda.userservice.DTO.Response.Address.AddressInternalResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InternalUserResponse {
    private String userId;
    private String fullName;
    private String email;
    private String phone;
    private AddressInternalResponse defaultAddress;
}
