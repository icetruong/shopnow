package com.ice.shippingservice.DTO.Location;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProvinceResponse {
    private Integer provinceId;
    private String provinceName;
}
