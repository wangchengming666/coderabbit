package com.demo.solana.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Kafka交易消息DTO
 * 
 * @author Demo Development Team
 * @version 1.0.0
 */
public class TransactionMessage {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("from_address")
    private String fromAddress;

    @JsonProperty("to_address")
    private String toAddress;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("signed_transaction")
    private String signedTransaction;

    @JsonProperty("priority")
    private String priority;

    @JsonProperty("max_retries")
    private Integer maxRetries;

    @JsonProperty("timeout_seconds")
    private Integer timeoutSeconds;

    @JsonProperty("callback_url")
    private String callbackUrl;

    @JsonProperty("metadata")
    private String metadata;

    @JsonProperty("created_time")
    private long createdTime;

    @JsonProperty("retry_count")
    private int retryCount = 0;

    // 构造函数
    public TransactionMessage() {
        this.createdTime = System.currentTimeMillis();
    }

    // 从请求创建消息的静态方法
    public static TransactionMessage fromRequest(TransactionBroadcastRequest request) {
        TransactionMessage message = new TransactionMessage();
        message.setRequestId(request.getClientRequestId());
        message.setFromAddress(request.getFromAddress());
        message.setToAddress(request.getToAddress());
        message.setAmount(request.getAmount());
        message.setSignedTransaction(request.getSignedTransaction());
        message.setPriority(request.getPriority());
        message.setMaxRetries(request.getMaxRetries());
        message.setTimeoutSeconds(request.getTimeoutSeconds());
        message.setCallbackUrl(request.getCallbackUrl());
        message.setMetadata(request.getMetadata());
        return message;
    }

    // Getters and Setters
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
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

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    @Override
    public String toString() {
        return "TransactionMessage{" +
                "requestId='" + requestId + '\'' +
                ", fromAddress='" + fromAddress + '\'' +
                ", toAddress='" + toAddress + '\'' +
                ", amount=" + amount +
                ", priority='" + priority + '\'' +
                ", maxRetries=" + maxRetries +
                ", retryCount=" + retryCount +
                ", createdTime=" + createdTime +
                '}';
    }
}