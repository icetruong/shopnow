package com.ice.cartservice.DTO.Response.Issue;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CartValidationIssuesData {
    private List<CartValidationIssue> issues;
}
