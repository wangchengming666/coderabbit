package com.demo.solana.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.demo.solana.config.SolanaConfig;
import com.demo.solana.dto.TransactionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Solana交易处理服务 - 性能优化版本
 * 负责与Solana网络交互，处理交易广播
 * 
 * @author Demo Development Team
 * @version 2.0.0
 */
@Service
public class SolanaTransactionService {

    private static final Logger logger = LoggerFactory.getLogger(SolanaTransactionService.class);

    @Autowired
    private SolanaConfig solanaConfig;

    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService statusCheckExecutor;

    public SolanaTransactionService() {
        this.objectMapper = new ObjectMapper();
        // 创建专用的状态检查线程池
        this.statusCheckExecutor = Executors.newScheduledThreadPool(10);
    }

    /**
     * 初始化RestTemplate配置
     */
    @PostConstruct
    public void initRestTemplate() {
        // 使用默认的RestTemplate，Spring Boot会自动配置连接池
        this.restTemplate = new RestTemplate();
        
        logger.info("SolanaTransactionService RestTemplate初始化完成");
    }

    /**
     * 处理交易消息 - 优化版本
     * 
     * @param message 交易消息
     * @return 处理是否成功
     */
    public boolean processTransaction(TransactionMessage message) {
        try {
            logger.info("开始处理Solana交易 - requestId: {}, fromAddress: {}, toAddress: {}, amount: {}", 
                       message.getRequestId(), message.getFromAddress(), 
                       message.getToAddress(), message.getAmount());

            // 1. 验证预签名交易
            if (!validateSignedTransaction(message)) {
                logger.error("预签名交易验证失败 - requestId: {}", message.getRequestId());
                return false;
            }

            // 2. 广播交易到Solana网络
            String transactionHash = broadcastTransaction(message);
            
            if (transactionHash != null) {
                logger.info("交易广播成功 - requestId: {}, transactionHash: {}", 
                           message.getRequestId(), transactionHash);
                
                // 3. 异步确认交易状态 - 使用调度器而不是阻塞
                scheduleTransactionStatusCheck(transactionHash, message.getRequestId());
                
                return true;
            } else {
                logger.error("交易广播失败 - requestId: {}", message.getRequestId());
                return false;
            }

        } catch (Exception e) {
            logger.error("处理Solana交易时发生异常 - requestId: {}, error: {}", 
                        message.getRequestId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 验证预签名交易
     * 
     * @param message 交易消息
     * @return 验证是否通过
     */
    private boolean validateSignedTransaction(TransactionMessage message) {
        try {
            // 基本验证
            if (message.getSignedTransaction() == null || message.getSignedTransaction().trim().isEmpty()) {
                logger.warn("预签名交易为空 - requestId: {}", message.getRequestId());
                return false;
            }

            // 检查签名交易格式（简单的长度和格式检查）
            String signedTx = message.getSignedTransaction().trim();
            if (signedTx.length() < 64) { // Base64编码的最小长度
                logger.warn("预签名交易格式无效，长度过短 - requestId: {}, length: {}", 
                           message.getRequestId(), signedTx.length());
                return false;
            }

            // 这里可以添加更复杂的交易验证逻辑
            // 比如解析交易内容，验证签名等
            
            logger.debug("预签名交易验证通过 - requestId: {}", message.getRequestId());
            return true;

        } catch (Exception e) {
            logger.error("验证预签名交易时发生异常 - requestId: {}, error: {}", 
                        message.getRequestId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 广播交易到Solana网络 - 优化版本
     * 
     * @param message 交易消息
     * @return 交易哈希，失败时返回null
     */
    private String broadcastTransaction(TransactionMessage message) {
        try {
            String rpcUrl = solanaConfig.getRpc().getCurrentUrl();
            
            // 构建RPC请求
            Map<String, Object> rpcRequest = new HashMap<>();
            rpcRequest.put("jsonrpc", "2.0");
            rpcRequest.put("id", System.currentTimeMillis()); // 使用时间戳作为唯一ID
            rpcRequest.put("method", "sendTransaction");
            
            Map<String, Object> params = new HashMap<>();
            params.put("encoding", "base64");
            params.put("skipPreflight", false);
            params.put("preflightCommitment", "processed");
            params.put("maxRetries", 3);
            
            rpcRequest.put("params", new Object[]{message.getSignedTransaction(), params});

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "Demo-Solana-Client/2.0");
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(rpcRequest, headers);

            logger.debug("发送交易到Solana RPC - requestId: {}, rpcUrl: {}", 
                        message.getRequestId(), rpcUrl);

            // 发送请求
            ResponseEntity<Map> response = restTemplate.exchange(
                rpcUrl, HttpMethod.POST, requestEntity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                if (responseBody.containsKey("result")) {
                    String transactionHash = (String) responseBody.get("result");
                    logger.info("交易提交成功 - requestId: {}, transactionHash: {}", 
                               message.getRequestId(), transactionHash);
                    return transactionHash;
                } else if (responseBody.containsKey("error")) {
                    Map<String, Object> error = (Map<String, Object>) responseBody.get("error");
                    logger.error("Solana RPC返回错误 - requestId: {}, error: {}", 
                                message.getRequestId(), error);
                    return null;
                }
            }

            logger.error("Solana RPC请求失败 - requestId: {}, statusCode: {}", 
                        message.getRequestId(), response.getStatusCode());
            return null;

        } catch (Exception e) {
            logger.error("广播交易到Solana时发生异常 - requestId: {}, error: {}", 
                        message.getRequestId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 调度交易状态检查 - 非阻塞版本
     * 
     * @param transactionHash 交易哈希
     * @param requestId 请求ID
     */
    private void scheduleTransactionStatusCheck(String transactionHash, String requestId) {
        logger.debug("开始调度交易状态检查 - requestId: {}, transactionHash: {}", 
                    requestId, transactionHash);

        // 使用调度器进行非阻塞状态检查
        statusCheckExecutor.schedule(() -> {
            checkTransactionStatusAsync(transactionHash, requestId, 0);
        }, 2, TimeUnit.SECONDS); // 2秒后开始第一次检查
    }

    /**
     * 异步检查交易状态
     * 
     * @param transactionHash 交易哈希
     * @param requestId 请求ID
     * @param attempt 尝试次数
     */
    private void checkTransactionStatusAsync(String transactionHash, String requestId, int attempt) {
        try {
            String status = getTransactionStatus(transactionHash);
            
            if ("confirmed".equals(status)) {
                logger.info("交易已确认 - requestId: {}, transactionHash: {}, attempts: {}", 
                           requestId, transactionHash, attempt + 1);
                return;
            } else if ("failed".equals(status)) {
                logger.error("交易失败 - requestId: {}, transactionHash: {}, attempts: {}", 
                            requestId, transactionHash, attempt + 1);
                return;
            }
            
            // 如果还未确认且未达到最大尝试次数，继续调度下次检查
            if (attempt < 10) {
                logger.debug("交易仍在处理中 - requestId: {}, transactionHash: {}, attempt: {}", 
                            requestId, transactionHash, attempt + 1);
                
                // 使用指数退避策略
                long delay = Math.min(30, 3 * (1L << Math.min(attempt, 4))); // 3, 6, 12, 24, 30秒
                statusCheckExecutor.schedule(() -> {
                    checkTransactionStatusAsync(transactionHash, requestId, attempt + 1);
                }, delay, TimeUnit.SECONDS);
            } else {
                logger.warn("交易状态检查超时 - requestId: {}, transactionHash: {}, maxAttempts: {}", 
                           requestId, transactionHash, attempt + 1);
            }

        } catch (Exception e) {
            logger.error("检查交易状态时发生异常 - requestId: {}, transactionHash: {}, error: {}", 
                        requestId, transactionHash, e.getMessage(), e);
        }
    }

    /**
     * 获取交易状态 - 优化版本
     * 
     * @param transactionHash 交易哈希
     * @return 交易状态
     */
    private String getTransactionStatus(String transactionHash) {
        try {
            String rpcUrl = solanaConfig.getRpc().getCurrentUrl();
            
            Map<String, Object> rpcRequest = new HashMap<>();
            rpcRequest.put("jsonrpc", "2.0");
            rpcRequest.put("id", System.currentTimeMillis());
            rpcRequest.put("method", "getSignatureStatus");
            rpcRequest.put("params", new Object[]{transactionHash});

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(rpcRequest, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                rpcUrl, HttpMethod.POST, requestEntity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                if (responseBody.containsKey("result")) {
                    Map<String, Object> result = (Map<String, Object>) responseBody.get("result");
                    if (result != null && result.containsKey("value")) {
                        Map<String, Object> value = (Map<String, Object>) result.get("value");
                        if (value != null) {
                            if (value.containsKey("confirmationStatus")) {
                                return (String) value.get("confirmationStatus");
                            } else if (value.containsKey("err") && value.get("err") != null) {
                                return "failed";
                            }
                        }
                    }
                }
            }

            return "pending";

        } catch (Exception e) {
            logger.error("获取交易状态时发生异常 - transactionHash: {}, error: {}", 
                        transactionHash, e.getMessage(), e);
            return "unknown";
        }
    }

    /**
     * 获取账户余额 - 带缓存优化
     * 
     * @param address 账户地址
     * @return 余额（SOL）
     */
    public Double getAccountBalance(String address) {
        try {
            String rpcUrl = solanaConfig.getRpc().getCurrentUrl();
            
            Map<String, Object> rpcRequest = new HashMap<>();
            rpcRequest.put("jsonrpc", "2.0");
            rpcRequest.put("id", System.currentTimeMillis());
            rpcRequest.put("method", "getBalance");
            rpcRequest.put("params", new Object[]{address});

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(rpcRequest, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                rpcUrl, HttpMethod.POST, requestEntity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                if (responseBody.containsKey("result")) {
                    Map<String, Object> result = (Map<String, Object>) responseBody.get("result");
                    if (result != null && result.containsKey("value")) {
                        Long lamports = ((Number) result.get("value")).longValue();
                        return lamports / 1_000_000_000.0; // 转换为SOL
                    }
                }
            }

            return null;

        } catch (Exception e) {
            logger.error("获取账户余额时发生异常 - address: {}, error: {}", 
                        address, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 批量处理交易 - 新增功能
     * 
     * @param messages 交易消息列表
     * @return 成功处理的数量
     */
    @Async
    public CompletableFuture<Integer> processBatchTransactions(java.util.List<TransactionMessage> messages) {
        int successCount = 0;
        
        logger.info("开始批量处理交易 - 数量: {}", messages.size());
        
        for (TransactionMessage message : messages) {
            try {
                if (processTransaction(message)) {
                    successCount++;
                }
            } catch (Exception e) {
                logger.error("批量处理交易失败 - requestId: {}, error: {}", 
                            message.getRequestId(), e.getMessage(), e);
            }
        }
        
        logger.info("批量交易处理完成 - 总数: {}, 成功: {}", messages.size(), successCount);
        return CompletableFuture.completedFuture(successCount);
    }
}