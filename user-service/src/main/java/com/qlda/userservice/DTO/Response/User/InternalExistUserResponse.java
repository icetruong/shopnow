package com.qlda.userservice.DTO.Response.User;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InternalExistUserResponse {
    private Boolean exists;
    private Boolean isActive;
}
