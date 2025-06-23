package com.demo.solana.controller;

import com.demo.solana.dto.TransactionBroadcastRequest;
import com.demo.solana.dto.TransactionBroadcastResponse;
import com.demo.solana.security.RateLimitingService;
import com.demo.solana.service.TransactionProducerService;
import com.demo.solana.service.SolanaTransactionService;
import com.demo.solana.service.TransactionConsumerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TransactionBroadcastController 测试类
 * 
 * @author Demo Development Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
public class TransactionBroadcastControllerTest {

    @Mock
    private TransactionProducerService producerService;

    @Mock
    private TransactionConsumerService consumerService;

    @Mock
    private SolanaTransactionService solanaTransactionService;

    @Mock
    private RateLimitingService rateLimitingService;

    @InjectMocks
    private TransactionBroadcastController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testBroadcastTransaction_Success() throws Exception {
        // 准备测试数据
        TransactionBroadcastRequest request = createValidRequest();
        
        // Mock 服务行为
        when(rateLimitingService.isAllowedPerMinute(anyString())).thenReturn(true);
        when(producerService.sendTransactionMessage(any(TransactionBroadcastRequest.class)))
            .thenReturn(CompletableFuture.completedFuture("test-message-id"));

        // 执行测试
        mockMvc.perform(post("/v1/solana/broadcast")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("交易提交成功"))
                .andExpect(jsonPath("$.request_id").exists())
                .andExpect(jsonPath("$.transaction_hash").exists());
    }

    @Test
    void testBroadcastTransaction_RateLimited() throws Exception {
        // 准备测试数据
        TransactionBroadcastRequest request = createValidRequest();
        
        // Mock 限流触发
        when(rateLimitingService.isAllowedPerMinute(anyString())).thenReturn(false);

        // 执行测试
        mockMvc.perform(post("/v1/solana/broadcast")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求过于频繁，请稍后再试"));
    }

    @Test
    void testBroadcastTransaction_ValidationError() throws Exception {
        // 准备无效的测试数据（缺少必填字段）
        TransactionBroadcastRequest request = new TransactionBroadcastRequest();
        request.setFromAddress(""); // 空地址
        request.setToAddress(""); // 空地址
        
        // Mock 服务行为
        when(rateLimitingService.isAllowedPerMinute(anyString())).thenReturn(true);

        // 执行测试
        mockMvc.perform(post("/v1/solana/broadcast")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void testGetAccountBalance_Success() throws Exception {
        String testAddress = "DemoAddress123456789";
        Double testBalance = 1.5;
        
        // Mock 服务行为
        when(solanaTransactionService.getAccountBalance(testAddress)).thenReturn(testBalance);

        // 执行测试
        mockMvc.perform(get("/v1/solana/account/{address}/balance", testAddress))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.address").value(testAddress))
                .andExpect(jsonPath("$.data.balance").value(testBalance))
                .andExpect(jsonPath("$.data.unit").value("SOL"));
    }

    @Test
    void testGetAccountBalance_NotFound() throws Exception {
        String testAddress = "InvalidAddress";
        
        // Mock 服务返回null（账户不存在）
        when(solanaTransactionService.getAccountBalance(testAddress)).thenReturn(null);

        // 执行测试
        mockMvc.perform(get("/v1/solana/account/{address}/balance", testAddress))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("账户不存在或查询失败"));
    }

    @Test
    void testHealthCheck() throws Exception {
        // 执行测试
        mockMvc.perform(get("/v1/solana/health"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("系统运行正常"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.timestamp").exists());
    }

    @Test
    void testGetSystemStats() throws Exception {
        // Mock 统计数据
        TransactionConsumerService.ProcessingStats stats = 
            new TransactionConsumerService.ProcessingStats(100, 95, 5);
        when(consumerService.getProcessingStats()).thenReturn(stats);

        // 执行测试
        mockMvc.perform(get("/v1/solana/stats"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.processed").value(100))
                .andExpect(jsonPath("$.data.success").value(95))
                .andExpect(jsonPath("$.data.failure").value(5))
                .andExpect(jsonPath("$.data.successRate").value(95.0));
    }

    /**
     * 创建有效的测试请求
     */
    private TransactionBroadcastRequest createValidRequest() {
        TransactionBroadcastRequest request = new TransactionBroadcastRequest();
        request.setClientRequestId("test-request-123");
        request.setFromAddress("DemoFromAddress123456789");
        request.setToAddress("DemoToAddress123456789");
        request.setAmount(new BigDecimal("0.1"));
        request.setSignedTransaction("dGVzdC1zaWduZWQtdHJhbnNhY3Rpb24tZGF0YQ=="); // Base64编码的测试数据
        request.setPriority("normal");
        request.setMaxRetries(3);
        request.setTimeoutSeconds(30);
        request.setRequestSignature("test-signature-123");
        request.setTimestamp(System.currentTimeMillis() / 1000);
        return request;
    }
} 