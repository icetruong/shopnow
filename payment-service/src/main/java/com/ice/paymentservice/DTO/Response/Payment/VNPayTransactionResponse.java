package com.ice.paymentservice.DTO.Response.Payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Response chung cho cả refund và querydr — 2 API này của VNPay trả gần như cùng field set. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VNPayTransactionResponse {
    @JsonProperty("vnp_ResponseId")
    private String responseId;
    @JsonProperty("vnp_ResponseCode")
    private String responseCode;
    @JsonProperty("vnp_Message")
    private String message;
    @JsonProperty("vnp_TxnRef")
    private String txnRef;
    @JsonProperty("vnp_Amount")
    private Long amount;
    @JsonProperty("vnp_TransactionNo")
    private String transactionNo;
    @JsonProperty("vnp_TransactionType")
    private String transactionType;
    @JsonProperty("vnp_TransactionStatus")
    private String transactionStatus;
}
