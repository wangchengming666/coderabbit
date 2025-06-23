package com.demo.solana.service;

import com.demo.solana.dto.TransactionBroadcastRequest;
import com.demo.solana.dto.TransactionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 交易生产者服务
 * 负责将交易消息发送到Kafka队列
 * 
 * @author Demo Development Team
 * @version 1.0.0
 */
@Service
public class TransactionProducerService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionProducerService.class);

    @Autowired
    private KafkaTemplate<String, TransactionMessage> kafkaTemplate;

    @Value("${app.kafka.topic.transaction:solana-transactions}")
    private String transactionTopic;

    @Value("${app.kafka.topic.priority-transaction:solana-priority-transactions}")
    private String priorityTransactionTopic;

    @Value("${app.kafka.topic.dlq:solana-transactions-dlq}")
    private String deadLetterTopic;

    /**
     * 发送交易消息到Kafka
     * 
     * @param request 交易广播请求
     * @return 消息发送结果的CompletableFuture
     */
    public CompletableFuture<String> sendTransactionMessage(TransactionBroadcastRequest request) {
        try {
            // 将请求转换为消息
            TransactionMessage message = TransactionMessage.fromRequest(request);
            
            // 根据优先级选择主题
            String topic = getTopicByPriority(request.getPriority());
            
            // 生成消息键（用于分区）
            String messageKey = generateMessageKey(message);

            logger.info("准备发送交易消息到Kafka - requestId: {}, topic: {}, key: {}, priority: {}", 
                       message.getRequestId(), topic, messageKey, message.getPriority());

            // 发送消息
            CompletableFuture<SendResult<String, TransactionMessage>> future = 
                kafkaTemplate.send(topic, messageKey, message);

            return future.thenApply(result -> {
                String messageId = String.format("%s-%d-%d", 
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
                );

                logger.info("交易消息发送成功 - requestId: {}, messageId: {}, topic: {}, partition: {}, offset: {}", 
                           message.getRequestId(), messageId, 
                           result.getRecordMetadata().topic(),
                           result.getRecordMetadata().partition(),
                           result.getRecordMetadata().offset());

                return messageId;

            }).exceptionally(throwable -> {
                logger.error("交易消息发送失败 - requestId: {}, topic: {}, error: {}", 
                            message.getRequestId(), topic, throwable.getMessage(), throwable);
                
                // 发送到死信队列
                sendToDeadLetterQueue(message, throwable.getMessage());
                
                throw new RuntimeException("消息发送失败: " + throwable.getMessage(), throwable);
            });

        } catch (Exception e) {
            logger.error("发送交易消息时发生异常 - requestId: {}, error: {}", 
                        request.getClientRequestId(), e.getMessage(), e);
            
            CompletableFuture<String> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    /**
     * 批量发送交易消息
     * 
     * @param requests 交易请求列表
     * @return 批量发送结果
     */
    public CompletableFuture<java.util.List<String>> sendBatchTransactionMessages(
            java.util.List<TransactionBroadcastRequest> requests) {
        
        logger.info("准备批量发送交易消息 - 数量: {}", requests.size());

        java.util.List<CompletableFuture<String>> futures = requests.stream()
                .map(this::sendTransactionMessage)
                .collect(java.util.stream.Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(java.util.stream.Collectors.toList()))
                .whenComplete((results, throwable) -> {
                    if (throwable != null) {
                        logger.error("批量发送交易消息失败 - error: {}", throwable.getMessage(), throwable);
                    } else {
                        logger.info("批量发送交易消息完成 - 成功数量: {}", results.size());
                    }
                });
    }

    /**
     * 发送到死信队列
     * 
     * @param message 交易消息
     * @param errorMessage 错误信息
     */
    private void sendToDeadLetterQueue(TransactionMessage message, String errorMessage) {
        try {
            // 添加错误信息到消息元数据
            String originalMetadata = message.getMetadata();
            String errorMetadata = String.format("{\"error\":\"%s\",\"timestamp\":%d,\"original_metadata\":\"%s\"}", 
                                               errorMessage, System.currentTimeMillis(), 
                                               originalMetadata != null ? originalMetadata : "");
            message.setMetadata(errorMetadata);

            kafkaTemplate.send(deadLetterTopic, message.getRequestId(), message);
            
            logger.warn("交易消息已发送到死信队列 - requestId: {}, dlqTopic: {}", 
                       message.getRequestId(), deadLetterTopic);

        } catch (Exception e) {
            logger.error("发送到死信队列失败 - requestId: {}, error: {}", 
                        message.getRequestId(), e.getMessage(), e);
        }
    }

    /**
     * 根据优先级获取主题
     * 
     * @param priority 优先级
     * @return Kafka主题名称
     */
    private String getTopicByPriority(String priority) {
        if ("high".equalsIgnoreCase(priority) || "urgent".equalsIgnoreCase(priority)) {
            return priorityTransactionTopic;
        }
        return transactionTopic;
    }

    /**
     * 生成消息键用于分区
     * 
     * @param message 交易消息
     * @return 消息键
     */
    private String generateMessageKey(TransactionMessage message) {
        // 使用发送地址作为分区键，确保同一地址的交易有序处理
        return message.getFromAddress();
    }

    /**
     * 获取Kafka模板统计信息
     * 
     * @return 统计信息
     */
    public java.util.Map<String, Object> getProducerStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("defaultTopic", kafkaTemplate.getDefaultTopic());
        stats.put("transactionTopic", transactionTopic);
        stats.put("priorityTransactionTopic", priorityTransactionTopic);
        stats.put("deadLetterTopic", deadLetterTopic);
        return stats;
    }
} 