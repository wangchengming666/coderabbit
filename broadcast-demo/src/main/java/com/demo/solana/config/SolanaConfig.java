package com.demo.solana.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Solana网络配置类
 * 
 * @author Demo Development Team
 * @version 1.0.0
 */
@Configuration
@ConfigurationProperties(prefix = "solana")
public class SolanaConfig {

    private Rpc rpc = new Rpc();

    public Rpc getRpc() {
        return rpc;
    }

    public void setRpc(Rpc rpc) {
        this.rpc = rpc;
    }

    /**
     * RPC配置
     */
    public static class Rpc {
        private String mainnetUrl = "https://api.mainnet-beta.solana.com";
        private String devnetUrl = "https://api.devnet.solana.com";
        private String testnetUrl = "https://api.testnet.solana.com";
        private String environment = "devnet";
        private int timeout = 30000;
        private int retryCount = 3;
        private ConnectionPool connectionPool = new ConnectionPool();

        public String getMainnetUrl() {
            return mainnetUrl;
        }

        public void setMainnetUrl(String mainnetUrl) {
            this.mainnetUrl = mainnetUrl;
        }

        public String getDevnetUrl() {
            return devnetUrl;
        }

        public void setDevnetUrl(String devnetUrl) {
            this.devnetUrl = devnetUrl;
        }

        public String getTestnetUrl() {
            return testnetUrl;
        }

        public void setTestnetUrl(String testnetUrl) {
            this.testnetUrl = testnetUrl;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }

        public ConnectionPool getConnectionPool() {
            return connectionPool;
        }

        public void setConnectionPool(ConnectionPool connectionPool) {
            this.connectionPool = connectionPool;
        }

        /**
         * 获取当前环境的RPC URL
         * 
         * @return RPC URL
         */
        public String getCurrentUrl() {
            switch (environment.toLowerCase()) {
                case "mainnet":
                    return mainnetUrl;
                case "testnet":
                    return testnetUrl;
                case "devnet":
                default:
                    return devnetUrl;
            }
        }

        /**
         * 连接池配置
         */
        public static class ConnectionPool {
            private int maxTotal = 20;
            private int maxPerRoute = 10;
            private int connectionTimeout = 5000;
            private int socketTimeout = 30000;
            private int connectionRequestTimeout = 5000;

            public int getMaxTotal() {
                return maxTotal;
            }

            public void setMaxTotal(int maxTotal) {
                this.maxTotal = maxTotal;
            }

            public int getMaxPerRoute() {
                return maxPerRoute;
            }

            public void setMaxPerRoute(int maxPerRoute) {
                this.maxPerRoute = maxPerRoute;
            }

            public int getConnectionTimeout() {
                return connectionTimeout;
            }

            public void setConnectionTimeout(int connectionTimeout) {
                this.connectionTimeout = connectionTimeout;
            }

            public int getSocketTimeout() {
                return socketTimeout;
            }

            public void setSocketTimeout(int socketTimeout) {
                this.socketTimeout = socketTimeout;
            }

            public int getConnectionRequestTimeout() {
                return connectionRequestTimeout;
            }

            public void setConnectionRequestTimeout(int connectionRequestTimeout) {
                this.connectionRequestTimeout = connectionRequestTimeout;
            }
        }
    }
} 