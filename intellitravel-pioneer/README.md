# IntelliTravel Pioneer - 后端服务

> 基于Spring Boot 4.0.3的AI旅行规划Agent后端服务，提供行程生成、智能修改、对话管理等核心功能。

## 项目概述

IntelliTravel Pioneer后端是AI旅行规划系统的核心服务，采用ReAct Agent架构，通过SSE实时推送Agent执行过程，支持多轮对话和智能意图识别。

### 核心特性

- **ReAct Agent架构**: 三阶段自主规划（Plan → Execute → Verify）
- **MCP工具集成**: 通过SSE连接高德地图MCP服务，实时验证地理数据
- **SSE实时推送**: Agent执行过程、工具调用实时可视化
- **智能意图识别**: 关键词快速匹配 + AI分类器兜底
- **多轮对话**: 支持持续对话优化行程
- **JWT认证**: 用户身份验证和授权
- **文件上传**: 支持阿里云OSS文件存储
- **跨对话知识共享**: 用户可设置全局偏好，在所有对话中生效，AI回复遵守自定义规则

## 智能意图识别

### 双通道架构

意图识别采用双通道架构，确保准确性和响应速度：

```
用户消息
    │
    ├── 第一层：关键词快速匹配（IntentStateMachine.detectIntent）
    │     ├── 52个修改关键词（"改成""删除""增加"等）
    │     ├── 0延迟，立即响应
    │     └── 匹配成功 → 直接返回意图
    │
    └── 第二层：AI分类器兜底（HttpTripPlanningAiClient.classifyIntent）
          ├── 轻量级LLM调用（max_tokens=10, temperature=0）
          ├── 处理自然表达（"多玩一天""想换个地方"等）
          └── 分类结果：CHAT 或 MODIFY
```

### 意图类型

| 意图 | 说明 | 触发条件 |
|------|------|----------|
| `PLAN_START` | 开始规划 | 用户请求生成行程 |
| `PLAN_MODIFY` | 修改行程 | 用户要求修改现有行程 |
| `PLAN_QUERY` | 查询信息 | 用户询问行程相关信息 |
| `PLAN_CONFIRM` | 确认操作 | 用户确认当前操作 |
| `PLAN_COMPLETE` | 完成规划 | 用户表示行程已完成 |
| `PLAN_REPEAT_LAST` | 重复上一次 | 用户要求重复上一次操作 |
| `PLAN_REPEAT_PREVIOUS` | 重复上上次 | 用户要求重复上上次操作 |
| `PLAN_REPEAT_BY_NUMBER` | 按序号重复 | 用户要求按序号重复操作 |
| `PLAN_SHOW_HISTORY` | 显示历史 | 用户查看操作历史 |

### 状态机管理

`IntentStateMachine` 维护对话状态和意图历史：

- **状态类型**：`IDLE`、`GATHERING_INFO`、`PLANNING`、`MODIFYING`、`REVIEWING`
- **状态转换**：根据意图自动转换状态
- **上下文保持**：跨消息保持对话上下文

### 操作类型识别

`detectActionType()` 方法识别具体的修改操作类型：

| 操作类型 | 说明 | 触发关键词 |
|----------|------|------------|
| `DELETE` | 删除条目 | "删除""去掉""移除""删掉" |
| `ADD` | 新增条目 | "增加""添加""新增" |
| `UPDATE_DAYS` | 更新天数 | "改为X天""多玩一天""少玩一天""延长""缩短""添加一天""增加一天""多出一天""多玩一天" |
| `MODIFY` | 修改条目 | "改成""换成""修改""变为" |
| `UNKNOWN` | 未知操作 | Agent自行判断 |

#### 天数变更识别

- **明确数字**：`改为5天` → 5天
- **相对表达**：`多玩一天` → 当前天数 + 1
- **自然语言**：`再加一天`、`延长行程`、`缩短行程`、`添加一天`、`增加一天`、`多出一天`、`多玩一天`

#### 操作记录

`ActionTracker` 记录用户操作历史，支持：
- 操作历史查询
- 重复上一次操作
- 按序号重复操作
- **上下文保持**：跨消息保持对话上下文

### AI分类器优化

1. **修改意图识别**：
   - 明确指令："把故宫换成天坛"、"删掉第二天"
   - 自然表达："多玩一天"、"想换个地方"
   - 关键词："推荐"≠MODIFY，除非说"把XX加到行程中"

2. **生成意图识别**：
   - 正则快速判断：城市名 + 时间信息
   - AI兜底：处理模糊请求
   - 默认策略：无法确定时返回CHAT，避免意外修改

### 性能优化

- **关键词匹配**：0延迟，优先使用
- **AI分类器**：轻量级调用，max_tokens=10
- **缓存机制**：状态机缓存对话状态
- **降级策略**：AI分类失败时默认CHAT

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 4.0.3 | Web框架 |
| Java | 21 | 编程语言 |
| Spring Data JPA | - | ORM框架 |
| MySQL | 8.0.41 | 数据库 |
| JWT (java-jwt) | 4.4.0 | 认证 |
| BCrypt | 0.10.2 | 密码加密 |
| DeepSeek API | v4-pro | AI模型 |
| 高德地图MCP | SSE协议 | 地图工具 |
| Thymeleaf | - | 模板引擎 |
| Lombok | - | 代码简化 |
| Maven | - | 构建工具 |

## 项目结构

```
intellitravel-pioneer/
├── request/                      # 请求示例
├── src/main/java/com/travel/intellitravel_pioneer/
│   ├── controller/           # REST控制器
│   │   ├── AmapController.java          # 地图API
│   │   ├── CapsuleController.java       # 胶囊管理
│   │   ├── CommunityController.java     # 社区管理
│   │   ├── ConversationController.java  # 对话管理
│   │   ├── ItineraryController.java     # 行程规划（SSE）
│   │   ├── RealTimeTripController.java  # 实时行程
│   │   ├── SkillController.java         # 技能管理
│   │   ├── SocialController.java        # 社交管理
│   │   ├── ToolsController.java         # 工具管理
│   │   ├── UserController.java          # 用户管理
│   │   ├── UserHealthController.java    # 用户健康
│   │   └── UserGlobalPreferenceController.java  # 全局偏好设置
│   ├── service/              # 业务逻辑层（包含多个服务类）
│   │   ├── ai/               # AI客户端（Agent Loop + Prompt + 意图识别）
│   │   │   ├── TripPlanningAiClient.java       # AI客户端接口
│   │   │   ├── HttpTripPlanningAiClient.java   # 实现（含全部Prompt模板）
│   │   │   ├── TripSummaryAiClient.java        # 行程摘要AI接口
│   │   │   ├── HttpTripSummaryAiClient.java    # 行程摘要AI实现
│   │   │   ├── AiImageClient.java              # AI图片生成接口
│   │   │   └── HttpAiImageClient.java          # AI图片生成实现
│   │   ├── serviceImpl/      # 服务实现
│   │   │   └── ItineraryServiceImpl.java       # 行程服务（意图识别入口）
│   │   ├── IntentStateMachine.java    # 意图状态机
│   │   ├── EntityTracker.java         # 实体追踪
│   │   ├── ActionTracker.java         # 操作历史
│   │   ├── MemoryManager.java         # 工作记忆
│   │   ├── ConversationContextManager.java  # 上下文管理（滑动窗口+摘要压缩）
│   │   ├── AmapService.java           # 地图服务
│   │   ├── CapsuleService.java        # 胶囊服务
│   │   ├── CommunityService.java      # 社区服务
│   │   ├── ConversationService.java   # 对话服务
│   │   ├── ImageService.java          # 图片服务
│   │   ├── ItineraryService.java      # 行程服务
│   │   ├── RealTimeTripService.java   # 实时行程服务
│   │   ├── SkillService.java          # 技能服务
│   │   ├── SocialService.java         # 社交服务
│   │   ├── ToolManagementService.java # 工具管理服务
│   │   ├── TripSummaryService.java    # 行程摘要服务
│   │   ├── TripSummaryGenerationEvent.java      # 行程摘要生成事件
│   │   ├── TripSummaryGenerationListener.java   # 行程摘要生成监听器
│   │   ├── TripSummaryGenerationStateStore.java # 行程摘要生成状态存储
│   │   ├── UserHealthService.java     # 用户健康服务
│   │   ├── UserService.java           # 用户服务
│   │   └── UserGlobalPreferenceService.java  # 全局偏好服务
│   ├── repository/           # 数据访问层
│   ├── po/                   # 持久化对象
│   ├── vo/                   # 视图对象
│   ├── dto/                  # 数据传输对象
│   ├── configure/            # 配置类
│   ├── enums/                # 枚举类
│   ├── exception/            # 异常处理
│   └── util/                 # 工具类
├── src/main/resources/
│   ├── application.properties # 应用配置
│   └── templates/            # Thymeleaf模板
├── pom.xml                   # Maven配置
├── intellitravel.sql         # 数据库脚本
├── mvnw                      # Maven Wrapper (Unix)
└── mvnw.cmd                  # Maven Wrapper (Windows)
```

## 安装要求

- **Java**: 21+
- **Maven**: 3.8+
- **MySQL**: 8.0+
- **操作系统**: Windows/Linux/macOS

## 配置说明

### 环境变量

在启动前需要配置以下环境变量：

```bash
# 数据库配置
export DB_URL=jdbc:mysql://localhost:3306/intellitravel?serverTimezone=Asia/Shanghai
export DB_USERNAME=root
export DB_PASSWORD=your_password

# AI服务配置
export DEEPSEEK_API_KEY=your_deepseek_api_key

# 高德地图配置
export AMAP_KEY=your_amap_key
export AMAP_MCP_KEY=your_amap_mcp_key

# 阿里云OSS配置（可选）
export OSS_ACCESS_KEY_ID=your_oss_access_key_id
export OSS_ACCESS_KEY_SECRET=your_oss_access_key_secret
```

### 配置文件

主要配置在 `src/main/resources/application.properties`：

```properties
# 数据库配置
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/intellitravel}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:123456}

# AI服务配置
trip.ai.llm.base-url=https://api.deepseek.com
trip.ai.llm.api-key=${DEEPSEEK_API_KEY:***REMOVED***}
trip.ai.llm.model=deepseek-v4-flash

# 高德地图配置
amap.key=${AMAP_KEY}
trip.ai.amap.mcp-url=https://mcp.amap.com/sse?key=${AMAP_MCP_KEY}

# JWT配置
jwt.secret=IntelliTravel-Pioneer-JWT-Secret-Key-2026
```

## 数据库初始化

1. 创建MySQL数据库：
```sql
CREATE DATABASE intellitravel CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 执行数据库脚本：
```bash
mysql -u root -p intellitravel < intellitravel.sql
```

## 运行方式

### 开发环境

```bash
# 使用Maven Wrapper启动（推荐）
./mvnw spring-boot:run

# 或使用本地Maven
mvn spring-boot:run
```

### 生产环境

```bash
# 打包
./mvnw clean package

# 运行JAR文件
java -jar target/intellitravel-pioneer-0.0.1-SNAPSHOT.jar
```

### 访问地址

- 后端API: http://localhost:8080
- API文档: http://localhost:8080/swagger-ui.html（如果启用了Swagger）

## API端点

### 用户模块 (`/api/user`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/user` | 用户注册 |
| POST | `/api/user/login` | 用户登录 |
| GET | `/api/user/{username}` | 获取用户信息 |

### 行程规划模块 (`/api/itinerary`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/itinerary/generate/stream` | SSE流式生成行程 |
| POST | `/api/itinerary/modify/stream` | SSE流式修改行程 |
| POST | `/api/itinerary/save` | 保存行程 |
| PUT | `/api/itinerary/{id}` | 更新行程 |
| GET | `/api/itinerary/detail/{id}` | 获取行程详情 |
| GET | `/api/itinerary/list` | 获取行程列表 |

### 对话模块 (`/api/conversation`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/conversation` | 创建对话 |
| GET | `/api/conversation/list` | 获取对话列表 |
| GET | `/api/conversation/{id}/messages` | 获取对话消息 |
| POST | `/api/conversation/{id}/link` | 关联行程到对话 |

### 技能模块 (`/api/skill`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/skill/list` | 获取技能列表 |
| POST | `/api/skill` | 创建技能 |
| PUT | `/api/skill/{id}` | 更新技能 |
| DELETE | `/api/skill/{id}` | 删除技能 |

### 工具模块 (`/api/tools`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tools/list` | 获取工具列表 |
| POST | `/api/tools/{id}/toggle` | 启用/禁用工具 |

### 全局偏好模块 (`/api/preference`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/preference` | 获取用户所有偏好 |
| GET | `/api/preference/{category}` | 获取指定类别偏好 |
| GET | `/api/preference/categories` | 获取所有可用类别定义 |
| POST | `/api/preference` | 保存或更新偏好 |
| POST | `/api/preference/batch` | 批量保存偏好 |
| PUT | `/api/preference/{category}/{key}/toggle` | 切换偏好启用状态 |
| DELETE | `/api/preference/{category}/{key}` | 删除偏好 |

### 地图模块 (`/api/amap`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/amap/geocode` | 地理编码 |
| GET | `/api/amap/weather` | 天气查询 |

## 核心架构

### Agent执行流程

```
用户请求
    │
    ├── 意图识别（双通道架构）
    │     ├── 第一层：关键词快速匹配（IntentStateMachine.detectIntent）
    │     │     ├── 52个修改关键词
    │     │     └── 0延迟，立即响应
    │     │
    │     └── 第二层：AI分类器兜底（HttpTripPlanningAiClient.classifyIntent）
    │           ├── 轻量级LLM调用（max_tokens=10, temperature=0）
    │           └── 处理自然表达
    │
    ├── 新建行程：Plan → Execute → Verify
    │     ├── Phase 1: PLAN（规划阶段）
    │     ├── Phase 2: EXECUTE（执行阶段，Agent Loop最多15轮）
    │     └── Phase 3: VERIFY（审核阶段）
    │
    └── 修改行程：意图识别 → Agent Loop → 结果返回
          ├── 操作类型识别（detectActionType）
          │     ├── DELETE：删除条目
          │     ├── ADD：新增条目
          │     ├── UPDATE_DAYS：更新天数
          │     ├── MODIFY：修改条目
          │     └── UNKNOWN：未知操作
          │
          └── Agent Loop（修改专用 Prompt）
                ├── 天数变更：自动搜索填充新景点
                ├── 增删替换：调用高德地图MCP验证
                └── final_output：返回修正后的完整行程
```

### SSE事件类型

- `phase`: 阶段进度
- `thought`: Agent思考过程
- `tool`: 工具调用详情
- `result`: 最终行程JSON
- `chat`: AI自然语言回复（行程生成/修改后的总结）
- `stats`: Token使用统计
- `conversationId`: 对话ID

## 测试

```bash
# 运行单元测试
./mvnw test

# 运行集成测试
./mvnw verify
```

## 部署

### Docker部署（推荐）

```dockerfile
FROM openjdk:21-jdk-slim
COPY target/intellitravel-pioneer-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### 传统部署

1. 打包项目：`./mvnw clean package`
2. 复制JAR文件到服务器
3. 使用systemd或init脚本运行

## 监控与日志

- 日志级别：在`application.properties`中配置`logging.level`
- 健康检查：`/actuator/health`（如果启用了Actuator）

## 故障排除

### 常见问题

1. **数据库连接失败**
   - 检查MySQL服务是否运行
   - 验证数据库连接参数

2. **AI服务调用失败**
   - 检查API密钥是否正确
   - 验证网络连接

3. **SSE连接中断**
   - 检查客户端网络稳定性
   - 查看服务器日志

## 贡献指南

1. Fork项目
2. 创建功能分支
3. 提交更改
4. 推送到分支
5. 创建Pull Request

## 许可证

本项目仅供学习交流使用

---

> 本项目是IntelliTravel AI旅行规划系统的后端服务，基于ReAct Agent架构实现智能行程规划。
