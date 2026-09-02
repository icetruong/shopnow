package com.ice.reviewservice.DTO.Response.User;

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
