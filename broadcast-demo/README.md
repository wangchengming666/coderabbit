# Solana交易广播系统 (broadcast-demo)

## 项目简介

这是一个基于Spring Boot的高性能Solana交易广播系统，采用生产者-消费者架构模式，使用Kafka作为消息队列，实现异步处理和高并发的Solana交易广播。

## 系统架构

```
用户请求 -> Controller -> Kafka Producer -> Kafka Queue -> Kafka Consumer -> Solana RPC -> 区块链
```

### 核心组件

- **Controller层**: 处理HTTP请求，提供REST API接口
- **Producer服务**: 将交易请求发送到Kafka队列
- **Consumer服务**: 消费Kafka消息，处理Solana交易
- **Transaction服务**: 负责Solana交易签名和RPC调用
- **配置管理**: 统一管理Solana网络、Kafka等配置

## 功能特性

### ✅ 已实现功能

1. **交易广播**
   - 单笔交易广播
   - 批量交易广播（最多100笔）
   - 异步处理，快速响应

2. **高性能设计**
   - Kafka消息队列解耦
   - 多线程异步处理
   - 连接池优化
   - 批量处理支持

3. **监控与统计**
   - 实时处理统计
   - 成功/失败率监控
   - 系统健康检查

4. **完整的API接口**
   - 交易广播 (`POST /api/v1/solana/broadcast`)
   - 批量广播 (`POST /api/v1/solana/broadcast/batch`)
   - 交易状态查询 (`GET /api/v1/solana/transaction/{hash}/status`)
   - 账户余额查询 (`GET /api/v1/solana/account/{address}/balance`)
   - 系统统计 (`GET /api/v1/solana/stats`)
   - 健康检查 (`GET /api/v1/solana/health`)

5. **日志追踪**
   - 完整的请求链路追踪
   - 详细的错误日志
   - 性能指标记录

## 快速开始

### 环境要求

- Java 17+
- Maven 3.6+
- Kafka 2.8+
- Redis (可选)

### 1. 启动依赖服务

```bash
# 启动Kafka
docker run -d --name kafka \
  -p 9092:9092 \
  -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  confluentinc/cp-kafka:latest

# 启动Zookeeper (Kafka依赖)
docker run -d --name zookeeper \
  -p 2181:2181 \
  -e ZOOKEEPER_CLIENT_PORT=2181 \
  -e ZOOKEEPER_TICK_TIME=2000 \
  confluentinc/cp-zookeeper:latest
```

### 2. 编译运行

```bash
# 编译项目
mvn clean compile

# 运行应用
mvn spring-boot:run

# 或者打包后运行
mvn clean package
java -jar target/broadcast-demo-1.0.0-SNAPSHOT.jar
```

### 3. 验证服务

```bash
# 健康检查
curl http://localhost:8080/api/v1/solana/health

# 查看系统统计
curl http://localhost:8080/api/v1/solana/stats
```

## API使用示例

### 单笔交易广播

```bash
curl -X POST http://localhost:8080/api/v1/solana/broadcast \
  -H "Content-Type: application/json" \
  -d '{
    "to_address": "11111111111111111111111111111112",
    "private_key": "your_private_key_here",
    "amount": 0.01,
    "memo": "测试转账",
    "priority": "MEDIUM"
  }'
```

### 批量交易广播

```bash
curl -X POST http://localhost:8080/api/v1/solana/broadcast/batch \
  -H "Content-Type: application/json" \
  -d '[
    {
      "to_address": "11111111111111111111111111111112",
      "private_key": "your_private_key_here",
      "amount": 0.01
    },
    {
      "to_address": "11111111111111111111111111111113",
      "private_key": "your_private_key_here",
      "amount": 0.02
    }
  ]'
```

### 查询交易状态

```bash
curl http://localhost:8080/api/v1/solana/transaction/{transaction_hash}/status
```

### 查询账户余额

```bash
curl http://localhost:8080/api/v1/solana/account/{address}/balance
```

## 配置说明

### 主要配置项

```yaml
# Solana网络配置
solana:
  rpc:
    default-network: devnet  # mainnet, testnet, devnet

# 性能配置
broadcast:
  performance:
    max-tps: 1000           # 最大TPS
    batch-size: 50          # 批量处理大小
    batch-timeout: 100      # 批量等待时间(ms)

# Kafka配置
spring:
  kafka:
    listener:
      concurrency: 10       # 消费者并发数
```

## 性能优化

### 1. Kafka优化
- 批量发送: `batch-size: 65536`
- 压缩算法: `compression-type: lz4`
- 并发消费: `concurrency: 10`

### 2. 线程池配置
- 交易处理线程池: 20个核心线程
- 异步任务队列: 1000容量
- 拒绝策略: CallerRuns

### 3. 连接池优化
- HTTP连接池: 最大50个连接
- 连接超时: 30秒
- 重试机制: 最多3次

## 监控指标

系统提供丰富的监控指标：

- **处理统计**: 总处理数、成功数、失败数
- **性能指标**: 成功率、失败率、平均处理时间
- **系统状态**: 线程池状态、队列深度、连接状态

## 注意事项

### ⚠️ 重要提醒

1. **私钥安全**: 当前实现为演示版本，实际生产环境需要加强私钥安全管理
2. **网络配置**: 默认使用Solana devnet，生产环境需要配置mainnet
3. **签名算法**: 当前使用模拟签名，需要集成真实的Solana签名库
4. **错误处理**: 建议配置死信队列处理失败消息

### 生产环境部署建议

1. **安全配置**
   - 使用HSM或密钥管理服务
   - 启用HTTPS和API认证
   - 配置防火墙和访问控制

2. **高可用部署**
   - 多实例部署
   - Kafka集群配置
   - 数据库主从配置

3. **监控告警**
   - 集成Prometheus/Grafana
   - 配置关键指标告警
   - 日志聚合分析

## License

MIT License

## 联系方式

- 开发团队: OKX Development Team
- 版本: 1.0.0 