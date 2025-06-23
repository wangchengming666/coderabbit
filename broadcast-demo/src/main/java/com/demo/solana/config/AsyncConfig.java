package com.demo.solana.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步配置类
 * 配置线程池用于异步处理
 * 
 * @author Demo Development Team
 * @version 1.0.0
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * 配置异步任务执行器
     * 
     * @return 线程池执行器
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        logger.info("正在配置异步任务执行器...");
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(10);
        
        // 最大线程数
        executor.setMaxPoolSize(50);
        
        // 队列容量
        executor.setQueueCapacity(200);
        
        // 线程名前缀
        executor.setThreadNamePrefix("Demo-Async-");
        
        // 拒绝策略：调用者运行
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        
        logger.info("异步任务执行器配置完成 - 核心线程数: {}, 最大线程数: {}, 队列容量: {}", 
                   executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }
}