package com.demo.solana.config;

import com.demo.solana.security.ApiKeyAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安全配置类
 * 
 * @author Demo Development Team
 * @version 1.0.0
 */
@Configuration
public class SecurityConfig {

    @Autowired
    private ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    /**
     * 注册API密钥认证过滤器
     */
    @Bean
    public FilterRegistrationBean<ApiKeyAuthenticationFilter> apiKeyFilterRegistration() {
        FilterRegistrationBean<ApiKeyAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(apiKeyAuthenticationFilter);
        registration.addUrlPatterns("/v1/*");
        registration.setName("ApiKeyAuthenticationFilter");
        registration.setOrder(1);
        return registration;
    }
}