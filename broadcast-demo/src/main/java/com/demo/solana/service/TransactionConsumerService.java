package com.demo.solana.service;

import com.demo.solana.dto.TransactionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 交易消费者服务
 * 负责消费Kafka中的交易消息并处理
 * 
 * @author Demo Development Team
 * @version 1.0.0
 */
@Service
public class TransactionConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionConsumerService.class);

    @Autowired
    private SolanaTransactionService solanaTransactionService;

    // 统计信息
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);

    /**
     * 消费普通优先级交易消息
     * 
     * @param message 交易消息
     * @param partition 分区
     * @param offset 偏移量
     * @param acknowledgment 确认机制
     */
    @KafkaListener(topics = "${app.kafka.topic.transaction:solana-transactions}", 
                   groupId = "${app.kafka.consumer.group-id:solana-transaction-consumer}",
                   concurrency = "${app.kafka.consumer.concurrency:3}")
    public void consumeTransactionMessage(@Payload TransactionMessage message,
                                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                        @Header(KafkaHeaders.OFFSET) long offset,
                                        Acknowledgment acknowledgment) {
        
        processTransactionMessage(message, partition, offset, acknowledgment, "normal");
    }

    /**
     * 消费高优先级交易消息
     * 
     * @param message 交易消息
     * @param partition 分区
     * @param offset 偏移量
     * @param acknowledgment 确认机制
     */
    @KafkaListener(topics = "${app.kafka.topic.priority-transaction:solana-priority-transactions}", 
                   groupId = "${app.kafka.consumer.priority-group-id:solana-priority-transaction-consumer}",
                   concurrency = "${app.kafka.consumer.priority-concurrency:5}")
    public void consumePriorityTransactionMessage(@Payload TransactionMessage message,
                                                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                                @Header(KafkaHeaders.OFFSET) long offset,
                                                Acknowledgment acknowledgment) {
        
        processTransactionMessage(message, partition, offset, acknowledgment, "priority");
    }

    /**
     * 处理交易消息的通用方法
     * 
     * @param message 交易消息
     * @param partition 分区
     * @param offset 偏移量
     * @param acknowledgment 确认机制
     * @param queueType 队列类型
     */
    private void processTransactionMessage(TransactionMessage message, int partition, long offset,
                                         Acknowledgment acknowledgment, String queueType) {
        
        long startTime = System.currentTimeMillis();
        processedCount.incrementAndGet();

        try {
            logger.info("开始处理交易消息 - requestId: {}, partition: {}, offset: {}, queueType: {}, retryCount: {}", 
                       message.getRequestId(), partition, offset, queueType, message.getRetryCount());

            // 检查消息是否过期
            if (isMessageExpired(message)) {
                logger.warn("交易消息已过期，跳过处理 - requestId: {}, createdTime: {}, timeoutSeconds: {}", 
                           message.getRequestId(), message.getCreatedTime(), message.getTimeoutSeconds());
                acknowledgment.acknowledge();
                return;
            }

            // 调用Solana交易服务处理
            boolean success = solanaTransactionService.processTransaction(message);

            if (success) {
                successCount.incrementAndGet();
                logger.info("交易消息处理成功 - requestId: {}, 处理时间: {}ms", 
                           message.getRequestId(), System.currentTimeMillis() - startTime);
                acknowledgment.acknowledge();
            } else {
                handleProcessingFailure(message, acknowledgment, "交易处理失败");
            }

        } catch (Exception e) {
            logger.error("处理交易消息时发生异常 - requestId: {}, error: {}", 
                        message.getRequestId(), e.getMessage(), e);
            handleProcessingFailure(message, acknowledgment, e.getMessage());
        }
    }

    /**
     * 处理消息处理失败的情况
     * 
     * @param message 交易消息
     * @param acknowledgment 确认机制
     * @param errorMessage 错误信息
     */
    private void handleProcessingFailure(TransactionMessage message, Acknowledgment acknowledgment, String errorMessage) {
        failureCount.incrementAndGet();

        // 检查是否需要重试
        if (message.getRetryCount() < message.getMaxRetries()) {
            message.incrementRetryCount();
            logger.warn("交易消息处理失败，准备重试 - requestId: {}, retryCount: {}, maxRetries: {}, error: {}", 
                       message.getRequestId(), message.getRetryCount(), message.getMaxRetries(), errorMessage);
            
            // 这里可以实现延迟重试逻辑，比如发送到延迟队列
            // 暂时先确认消息，避免无限重试阻塞队列
            acknowledgment.acknowledge();
        } else {
            logger.error("交易消息重试次数已达上限，放弃处理 - requestId: {}, retryCount: {}, error: {}", 
                        message.getRequestId(), message.getRetryCount(), errorMessage);
            acknowledgment.acknowledge();
        }
    }

    /**
     * 检查消息是否已过期
     * 
     * @param message 交易消息
     * @return 是否过期
     */
    private boolean isMessageExpired(TransactionMessage message) {
        if (message.getTimeoutSeconds() == null || message.getTimeoutSeconds() <= 0) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long messageAge = (currentTime - message.getCreatedTime()) / 1000;
        
        return messageAge > message.getTimeoutSeconds();
    }

    /**
     * 获取处理统计信息
     * 
     * @return 统计信息
     */
    public ProcessingStats getProcessingStats() {
        return new ProcessingStats(
            processedCount.get(),
            successCount.get(),
            failureCount.get()
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        processedCount.set(0);
        successCount.set(0);
        failureCount.set(0);
        logger.info("消费者统计信息已重置");
    }

    /**
     * 处理统计信息内部类
     */
    public static class ProcessingStats {
        private final long processedCount;
        private final long successCount;
        private final long failureCount;

        public ProcessingStats(long processedCount, long successCount, long failureCount) {
            this.processedCount = processedCount;
            this.successCount = successCount;
            this.failureCount = failureCount;
        }

        public long getProcessedCount() {
            return processedCount;
        }

        public long getSuccessCount() {
            return successCount;
        }

        public long getFailureCount() {
            return failureCount;
        }

        public double getSuccessRate() {
            return processedCount > 0 ? (double) successCount / processedCount * 100 : 0.0;
        }

        @Override
        public String toString() {
            return String.format("ProcessingStats{processed=%d, success=%d, failure=%d, successRate=%.2f%%}", 
                               processedCount, successCount, failureCount, getSuccessRate());
        }
    }
} 