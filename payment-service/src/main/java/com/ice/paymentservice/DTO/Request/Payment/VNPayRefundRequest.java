package com.ice.paymentservice.DTO.Request.Payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VNPayRefundRequest {
    @JsonProperty("vnp_RequestId")
    private String requestId;
    @JsonProperty("vnp_Version")
    private String version;
    @JsonProperty("vnp_Command")
    private String command;
    @JsonProperty("vnp_TmnCode")
    private String tmnCode;
    @JsonProperty("vnp_TransactionType")
    private String transactionType;
    @JsonProperty("vnp_TxnRef")
    private String txnRef;
    @JsonProperty("vnp_Amount")
    private long amount;
    @JsonProperty("vnp_OrderInfo")
    private String orderInfo;
    @JsonProperty("vnp_TransactionNo")
    private String transactionNo;
    @JsonProperty("vnp_TransactionDate")
    private String transactionDate;
    @JsonProperty("vnp_CreateBy")
    private String createBy;
    @JsonProperty("vnp_CreateDate")
    private String createDate;
    @JsonProperty("vnp_IpAddr")
    private String ipAddr;
    @JsonProperty("vnp_SecureHash")
    private String secureHash;
}
