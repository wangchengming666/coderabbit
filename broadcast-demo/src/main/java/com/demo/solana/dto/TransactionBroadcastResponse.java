package com.demo.solana.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 交易广播响应DTO
 * 
 * @author Demo Development Team
 * @version 1.0.0
 */
public class TransactionBroadcastResponse {

    @JsonProperty("code")
    private int code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("transaction_hash")
    private String transactionHash;

    @JsonProperty("status")
    private String status; // pending, confirmed, failed

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("data")
    private Object data;

    // 构造函数
    public TransactionBroadcastResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    public TransactionBroadcastResponse(int code, String message, String requestId) {
        this();
        this.code = code;
        this.message = message;
        this.requestId = requestId;
    }

    // 静态工厂方法
    public static TransactionBroadcastResponse success(String requestId, String transactionHash) {
        TransactionBroadcastResponse response = new TransactionBroadcastResponse(200, "交易提交成功", requestId);
        response.setTransactionHash(transactionHash);
        response.setStatus("pending");
        return response;
    }

    public static TransactionBroadcastResponse error(String requestId, String errorMessage) {
        return new TransactionBroadcastResponse(500, errorMessage, requestId);
    }

    public static TransactionBroadcastResponse validationError(String errorMessage) {
        return new TransactionBroadcastResponse(400, errorMessage, null);
    }

    public static TransactionBroadcastResponse rateLimitError(String requestId) {
        return new TransactionBroadcastResponse(429, "请求过于频繁，请稍后再试", requestId);
    }

    public static TransactionBroadcastResponse unauthorized(String requestId) {
        return new TransactionBroadcastResponse(401, "未授权访问", requestId);
    }

    // Getters and Setters
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public void setTransactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "TransactionBroadcastResponse{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", requestId='" + requestId + '\'' +
                ", transactionHash='" + transactionHash + '\'' +
                ", status='" + status + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
} 