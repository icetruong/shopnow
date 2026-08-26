package com.ice.orderservice.DTO.Response.Payment;

public sealed interface PaymentCreationResult
    permits PaymentCreationResult.Online, PaymentCreationResult.Cod
{
    record Online(CreatePaymentOnlineResponse response) implements PaymentCreationResult {}
    record Cod(CreatePaymentCODResponse response) implements PaymentCreationResult {}
}
