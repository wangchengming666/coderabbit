package com.demo.solana.controller;

import com.demo.solana.dto.TransactionBroadcastRequest;
import com.demo.solana.dto.TransactionBroadcastResponse;
import com.demo.solana.security.RateLimitingService;
import com.demo.solana.service.TransactionConsumerService;
import com.demo.solana.service.TransactionProducerService;
import com.demo.solana.service.SolanaTransactionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Solana交易广播控制器
 * 提供交易广播、查询等REST API接口
 * 
 * @author Demo Development Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/v1/solana")
@CrossOrigin(origins = "*")
public class TransactionBroadcastController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionBroadcastController.class);

    @Autowired
    private TransactionProducerService producerService;

    @Autowired
    private TransactionConsumerService consumerService;

    @Autowired
    private SolanaTransactionService solanaTransactionService;

    @Autowired
    private RateLimitingService rateLimitingService;

    /**
     * 广播单个交易
     * 
     * @param request 交易广播请求
     * @param bindingResult 验证结果
     * @return 交易广播响应
     */
    @PostMapping("/broadcast")
    public ResponseEntity<TransactionBroadcastResponse> broadcastTransaction(
            @Valid @RequestBody TransactionBroadcastRequest request,
            BindingResult bindingResult,
            HttpServletRequest httpRequest) {

        // 生成请求ID用于追踪
        final String requestId;
        if (request.getClientRequestId() == null || request.getClientRequestId().trim().isEmpty()) {
            requestId = UUID.randomUUID().toString();
            request.setClientRequestId(requestId);
        } else {
            requestId = request.getClientRequestId();
        }

        // 设置追踪ID
        MDC.put("requestId", requestId);

        try {
            logger.info("收到交易广播请求 - requestId: {}, IP: {}, fromAddress: {}, toAddress: {}, amount: {}, priority: {}", 
                       requestId, httpRequest.getRemoteAddr(), request.getFromAddress(), 
                       request.getToAddress(), request.getAmount(), request.getPriority());

            // 1. 检查限流
            String clientId = getClientId(httpRequest, request);
            if (!rateLimitingService.isAllowedPerMinute(clientId)) {
                logger.warn("请求被限流 - requestId: {}, clientId: {}", requestId, clientId);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(TransactionBroadcastResponse.validationError("请求过于频繁，请稍后再试"));
            }

            // 2. 验证请求参数
            if (bindingResult.hasErrors()) {
                StringBuilder errorMsg = new StringBuilder("请求参数验证失败: ");
                bindingResult.getFieldErrors().forEach(error -> 
                    errorMsg.append(error.getField()).append(" ").append(error.getDefaultMessage()).append("; ")
                );
                
                logger.warn("交易广播请求参数验证失败 - requestId: {}, errors: {}", requestId, errorMsg);
                return ResponseEntity.badRequest()
                    .body(TransactionBroadcastResponse.validationError(errorMsg.toString()));
            }

            // 3. 验证请求签名
            if (!validateRequestSignature(request)) {
                logger.warn("请求签名验证失败 - requestId: {}", requestId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(TransactionBroadcastResponse.validationError("请求签名验证失败"));
            }

            // 发送消息到Kafka队列
            CompletableFuture<String> messageFuture = producerService.sendTransactionMessage(request);
            
            // 异步处理，立即返回响应
            messageFuture.whenComplete((messageId, throwable) -> {
                if (throwable != null) {
                    logger.error("发送交易消息失败 - requestId: {}, error: {}", requestId, throwable.getMessage(), throwable);
                } else {
                    logger.info("交易消息发送成功 - requestId: {}, messageId: {}", requestId, messageId);
                }
            });

            // 生成模拟交易哈希（实际场景中在消费者处理后会更新）
            String mockTransactionHash = generateMockTransactionHash();

            TransactionBroadcastResponse response = TransactionBroadcastResponse.success(requestId, mockTransactionHash);
            
            logger.info("交易广播请求已接受 - requestId: {}, transactionHash: {}", requestId, mockTransactionHash);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("处理交易广播请求失败 - requestId: {}, error: {}", requestId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(TransactionBroadcastResponse.error(requestId, "系统内部错误: " + e.getMessage()));

        } finally {
            // 清理MDC
            MDC.remove("requestId");
        }
    }

    /**
     * 批量广播交易
     * 
     * @param requests 交易请求列表
     * @return 批量响应结果
     */
    @PostMapping("/broadcast/batch")
    public ResponseEntity<List<TransactionBroadcastResponse>> broadcastBatchTransactions(
            @Valid @RequestBody List<TransactionBroadcastRequest> requests) {

        String batchId = UUID.randomUUID().toString();
        MDC.put("batchId", batchId);

        try {
            logger.info("收到批量交易广播请求 - batchId: {}, 数量: {}", batchId, requests.size());

            if (requests.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(List.of(TransactionBroadcastResponse.validationError("请求列表不能为空")));
            }

            if (requests.size() > 100) {
                return ResponseEntity.badRequest()
                    .body(List.of(TransactionBroadcastResponse.validationError("批量请求数量不能超过100个")));
            }

            // 处理批量请求
            List<TransactionBroadcastResponse> responses = new java.util.ArrayList<>();
            
            for (TransactionBroadcastRequest request : requests) {
                try {
                    // 为每个请求生成ID
                    if (request.getClientRequestId() == null || request.getClientRequestId().trim().isEmpty()) {
                        request.setClientRequestId(UUID.randomUUID().toString());
                    }

                    // 发送到Kafka
                    producerService.sendTransactionMessage(request);
                    
                    // 生成响应
                    String mockHash = generateMockTransactionHash();
                    responses.add(TransactionBroadcastResponse.success(request.getClientRequestId(), mockHash));

                } catch (Exception e) {
                    logger.error("处理批量请求中的单个交易失败 - batchId: {}, requestId: {}, error: {}", 
                                batchId, request.getClientRequestId(), e.getMessage(), e);
                    responses.add(TransactionBroadcastResponse.error(request.getClientRequestId(), e.getMessage()));
                }
            }

            logger.info("批量交易广播请求处理完成 - batchId: {}, 成功: {}, 总数: {}", 
                       batchId, responses.stream().mapToLong(r -> r.getCode() == 200 ? 1L : 0L).sum(), requests.size());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            logger.error("处理批量交易广播请求失败 - batchId: {}, error: {}", batchId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(List.of(TransactionBroadcastResponse.error(batchId, "系统内部错误: " + e.getMessage())));

        } finally {
            MDC.remove("batchId");
        }
    }

    /**
     * 查询交易状态
     * 
     * @param transactionHash 交易哈希
     * @return 交易状态响应
     */
    @GetMapping("/transaction/{transactionHash}/status")
    public ResponseEntity<TransactionBroadcastResponse> getTransactionStatus(
            @PathVariable String transactionHash) {

        try {
            logger.info("查询交易状态 - transactionHash: {}", transactionHash);

            // 这里实现交易状态查询逻辑
            // 可以从数据库或缓存中查询，或直接调用Solana RPC
            
            TransactionBroadcastResponse response = TransactionBroadcastResponse.success(null, transactionHash);
            response.setStatus("confirmed"); // 模拟状态
            response.setData(java.util.Map.of(
                "confirmations", 32,
                "blockHeight", 123456789L,
                "fee", 0.000005
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("查询交易状态失败 - transactionHash: {}, error: {}", transactionHash, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(TransactionBroadcastResponse.error(null, "查询失败: " + e.getMessage()));
        }
    }

    /**
     * 查询账户余额
     * 
     * @param address 账户地址
     * @return 余额查询响应
     */
    @GetMapping("/account/{address}/balance")
    public ResponseEntity<TransactionBroadcastResponse> getAccountBalance(
            @PathVariable String address) {

        try {
            logger.info("查询账户余额 - address: {}", address);

            Double balance = solanaTransactionService.getAccountBalance(address);
            
            if (balance != null) {
                TransactionBroadcastResponse response = TransactionBroadcastResponse.success(null, null);
                response.setData(java.util.Map.of(
                    "address", address,
                    "balance", balance,
                    "unit", "SOL"
                ));
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(TransactionBroadcastResponse.error(null, "账户不存在或查询失败"));
            }

        } catch (Exception e) {
            logger.error("查询账户余额失败 - address: {}, error: {}", address, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(TransactionBroadcastResponse.error(null, "查询失败: " + e.getMessage()));
        }
    }

    /**
     * 获取系统统计信息
     * 
     * @return 统计信息响应
     */
    @GetMapping("/stats")
    public ResponseEntity<TransactionBroadcastResponse> getSystemStats() {

        try {
            TransactionConsumerService.ProcessingStats stats = consumerService.getProcessingStats();
            
            TransactionBroadcastResponse response = TransactionBroadcastResponse.success(null, null);
            response.setData(java.util.Map.of(
                "processed", stats.getProcessedCount(),
                "success", stats.getSuccessCount(),
                "failure", stats.getFailureCount(),
                "successRate", stats.getSuccessRate()
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("获取系统统计信息失败 - error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(TransactionBroadcastResponse.error(null, "获取统计信息失败: " + e.getMessage()));
        }
    }

    /**
     * 健康检查
     * 
     * @return 健康状态响应
     */
    @GetMapping("/health")
    public ResponseEntity<TransactionBroadcastResponse> healthCheck() {
        TransactionBroadcastResponse response = TransactionBroadcastResponse.success(null, null);
        response.setMessage("系统运行正常");
        response.setData(java.util.Map.of(
            "status", "UP",
            "timestamp", System.currentTimeMillis()
        ));
        return ResponseEntity.ok(response);
    }

    // 私有辅助方法

    private String generateMockTransactionHash() {
        return "demo_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String getClientId(HttpServletRequest httpRequest, TransactionBroadcastRequest request) {
        // 优先使用from地址作为客户端ID，其次使用IP地址
        String clientId = request.getFromAddress();
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = httpRequest.getRemoteAddr();
        }
        return clientId;
    }

    private boolean validateRequestSignature(TransactionBroadcastRequest request) {
        // 简化的签名验证逻辑
        // 实际实现中应该验证请求签名的真实性
        
        if (request.getRequestSignature() == null || request.getRequestSignature().trim().isEmpty()) {
            return false;
        }

        if (request.getTimestamp() == null) {
            return false;
        }

        // 检查时间戳是否在合理范围内（5分钟内）
        long currentTime = System.currentTimeMillis() / 1000;
        long timeDiff = Math.abs(currentTime - request.getTimestamp());
        
        return timeDiff <= 300; // 5分钟容差
    }
} 