package com.demo.solana;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Solana交易广播系统主启动类
 * 
 * @author Demo Development Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableKafka
@EnableAsync
public class BroadcastDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(BroadcastDemoApplication.class, args);
    }
} 