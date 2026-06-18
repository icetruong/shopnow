package com.ice.cartservice.DTO.Response.Issue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ice.cartservice.Enum.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartValidationIssue {
    private String cartItemId;
    private ErrorCode type;
    private String message;
    private Integer requestedQty;
    private Integer availableQty;
}
