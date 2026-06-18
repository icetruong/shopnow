package com.ice.cartservice.Exception;

import com.ice.cartservice.DTO.Response.Issue.CartValidationIssue;
import lombok.Getter;

import java.util.List;

@Getter
public class CartValidationException extends RuntimeException {
    private final List<CartValidationIssue> issues;
    public CartValidationException(String message, List<CartValidationIssue> issues) {
        super(message);
        this.issues = issues;
    }
}
