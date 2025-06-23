package com.demo.solana.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * 交易广播请求DTO
 * 
 * @author Demo Development Team
 * @version 1.0.0
 */
public class TransactionBroadcastRequest {

    @JsonProperty("client_request_id")
    private String clientRequestId;

    @NotBlank(message = "发送方地址不能为空")
    @JsonProperty("from_address")
    private String fromAddress;

    @NotBlank(message = "接收方地址不能为空")
    @JsonProperty("to_address")
    private String toAddress;

    @NotNull(message = "转账金额不能为空")
    @DecimalMin(value = "0.000000001", message = "转账金额必须大于0")
    @JsonProperty("amount")
    private BigDecimal amount;

    @NotBlank(message = "已签名交易不能为空")
    @JsonProperty("signed_transaction")
    private String signedTransaction;

    @JsonProperty("priority")
    private String priority = "normal"; // normal, high, urgent

    @JsonProperty("max_retries")
    private Integer maxRetries = 3;

    @JsonProperty("timeout_seconds")
    private Integer timeoutSeconds = 30;

    @JsonProperty("callback_url")
    private String callbackUrl;

    @JsonProperty("metadata")
    private String metadata;

    // 安全相关字段
    @NotBlank(message = "请求签名不能为空")
    @JsonProperty("request_signature")
    private String requestSignature;

    @NotNull(message = "时间戳不能为空")
    @JsonProperty("timestamp")
    private Long timestamp;

    // 构造函数
    public TransactionBroadcastRequest() {}

    // Getters and Setters
    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getSignedTransaction() {
        return signedTransaction;
    }

    public void setSignedTransaction(String signedTransaction) {
        this.signedTransaction = signedTransaction;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getRequestSignature() {
        return requestSignature;
    }

    public void setRequestSignature(String requestSignature) {
        this.requestSignature = requestSignature;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "TransactionBroadcastRequest{" +
                "clientRequestId='" + clientRequestId + '\'' +
                ", fromAddress='" + fromAddress + '\'' +
                ", toAddress='" + toAddress + '\'' +
                ", amount=" + amount +
                ", priority='" + priority + '\'' +
                ", maxRetries=" + maxRetries +
                ", timeoutSeconds=" + timeoutSeconds +
                ", callbackUrl='" + callbackUrl + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
} 