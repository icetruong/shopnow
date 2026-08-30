package com.qlda.userservice.DTO.Response.Address;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AddressInternalResponse {
    private String fullName;
    private String phone;
    private String province;
    private String district;
    private String ward;
    private String streetDetail;
}
