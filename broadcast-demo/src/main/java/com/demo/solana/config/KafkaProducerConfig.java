package com.demo.solana.config;

import com.demo.solana.dto.TransactionMessage;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka生产者配置类
 * 
 * @author Demo Development Team
 * @version 1.0.0
 */
@Configuration
public class KafkaProducerConfig {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerConfig.class);

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.retries:3}")
    private int retries;

    @Value("${spring.kafka.producer.batch-size:16384}")
    private int batchSize;

    @Value("${spring.kafka.producer.linger-ms:1}")
    private int lingerMs;

    @Value("${spring.kafka.producer.buffer-memory:33554432}")
    private long bufferMemory;

    /**
     * 生产者工厂配置
     */
    @Bean
    public ProducerFactory<String, TransactionMessage> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // 基本配置
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // 性能配置
        configProps.put(ProducerConfig.RETRIES_CONFIG, retries);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);
        
        // 可靠性配置
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        
        // 压缩配置
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        
        // 超时配置
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        
        logger.info("Kafka生产者配置完成 - servers: {}, retries: {}, batchSize: {}", 
                   bootstrapServers, retries, batchSize);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka模板
     */
    @Bean
    public KafkaTemplate<String, TransactionMessage> kafkaTemplate() {
        KafkaTemplate<String, TransactionMessage> template = new KafkaTemplate<>(producerFactory());
        
        // 设置默认主题
        template.setDefaultTopic("solana-transactions");
        
        logger.info("KafkaTemplate配置完成");
        
        return template;
    }
} 