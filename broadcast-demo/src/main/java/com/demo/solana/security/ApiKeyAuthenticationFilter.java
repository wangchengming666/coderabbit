package com.demo.solana.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * API密钥认证过滤器
 * 
 * @author Demo Development Team
 * @version 1.0.0
 */
@Component
public class ApiKeyAuthenticationFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    @Value("${app.security.api-keys:demo-key-123,demo-key-456}")
    private String apiKeys;

    @Value("${app.security.ip-whitelist:}")
    private String ipWhitelist;

    @Value("${app.security.enabled:true}")
    private boolean securityEnabled;

    @Value("${app.security.timestamp-tolerance-seconds:300}")
    private long timestampToleranceSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 跳过健康检查和静态资源
        String requestPath = httpRequest.getRequestURI();
        if (shouldSkipAuthentication(requestPath)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            if (!securityEnabled) {
                logger.debug("安全认证已禁用，跳过验证");
                chain.doFilter(request, response);
                return;
            }

            // 1. 检查API密钥
            if (!validateApiKey(httpRequest)) {
                sendErrorResponse(httpResponse, 401, "无效的API密钥");
                return;
            }

            // 2. 检查IP白名单
            if (!validateIpWhitelist(httpRequest)) {
                sendErrorResponse(httpResponse, 403, "IP地址不在白名单中");
                return;
            }

            // 3. 检查时间戳
            if (!validateTimestamp(httpRequest)) {
                sendErrorResponse(httpResponse, 400, "请求时间戳无效或已过期");
                return;
            }

            logger.debug("安全验证通过 - IP: {}, Path: {}", 
                        httpRequest.getRemoteAddr(), requestPath);

            chain.doFilter(request, response);

        } catch (Exception e) {
            logger.error("安全验证过程中发生异常 - IP: {}, Path: {}, Error: {}", 
                        httpRequest.getRemoteAddr(), requestPath, e.getMessage(), e);
            sendErrorResponse(httpResponse, 500, "服务器内部错误");
        }
    }

    /**
     * 验证API密钥
     */
    private boolean validateApiKey(HttpServletRequest request) {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("缺少API密钥 - IP: {}", request.getRemoteAddr());
            return false;
        }

        List<String> validApiKeys = Arrays.asList(apiKeys.split(","));
        boolean isValid = validApiKeys.contains(apiKey.trim());

        if (!isValid) {
            logger.warn("无效的API密钥 - IP: {}, Key: {}", request.getRemoteAddr(), apiKey);
        }

        return isValid;
    }

    /**
     * 验证IP白名单
     */
    private boolean validateIpWhitelist(HttpServletRequest request) {
        if (ipWhitelist == null || ipWhitelist.trim().isEmpty()) {
            return true; // 未配置白名单时允许所有IP
        }

        String clientIp = getClientIpAddress(request);
        List<String> allowedIps = Arrays.asList(ipWhitelist.split(","));

        boolean isAllowed = allowedIps.stream()
                .anyMatch(allowedIp -> allowedIp.trim().equals(clientIp) || 
                         allowedIp.trim().equals("*"));

        if (!isAllowed) {
            logger.warn("IP地址不在白名单中 - IP: {}, 白名单: {}", clientIp, ipWhitelist);
        }

        return isAllowed;
    }

    /**
     * 验证时间戳
     */
    private boolean validateTimestamp(HttpServletRequest request) {
        String timestampStr = request.getHeader("X-Timestamp");
        if (timestampStr == null || timestampStr.trim().isEmpty()) {
            logger.warn("缺少时间戳 - IP: {}", request.getRemoteAddr());
            return false;
        }

        try {
            long timestamp = Long.parseLong(timestampStr);
            long currentTime = System.currentTimeMillis() / 1000; // 转换为秒
            long timeDiff = Math.abs(currentTime - timestamp);

            if (timeDiff > timestampToleranceSeconds) {
                logger.warn("时间戳已过期 - IP: {}, Timestamp: {}, Current: {}, Diff: {}s", 
                           request.getRemoteAddr(), timestamp, currentTime, timeDiff);
                return false;
            }

            return true;

        } catch (NumberFormatException e) {
            logger.warn("时间戳格式无效 - IP: {}, Timestamp: {}", 
                       request.getRemoteAddr(), timestampStr);
            return false;
        }
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * 判断是否需要跳过认证
     */
    private boolean shouldSkipAuthentication(String requestPath) {
        return requestPath.startsWith("/actuator/") ||
               requestPath.equals("/health") ||
               requestPath.equals("/") ||
               requestPath.startsWith("/static/") ||
               requestPath.startsWith("/css/") ||
               requestPath.startsWith("/js/") ||
               requestPath.startsWith("/images/");
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) 
            throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json;charset=UTF-8");
        
        String errorResponse = String.format(
            "{\"code\":%d,\"message\":\"%s\",\"timestamp\":%d}", 
            statusCode, message, System.currentTimeMillis()
        );
        
        response.getWriter().write(errorResponse);
        response.getWriter().flush();
    }
} 