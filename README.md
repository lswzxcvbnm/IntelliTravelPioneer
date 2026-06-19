# IntelliTravel Pioneer - AI旅行规划Agent系统

> 基于ReAct Agent架构的智能旅行规划系统，支持AI自主规划、智能修改、自然对话的完整闭环

## 项目概述

IntelliTravel Pioneer 是一个**AI Agent应用**，核心流程分两条路径：

- **新建行程**：Plan → Execute → Verify 三阶段架构，通过MCP协议集成高德地图工具验证真实数据
- **修改行程**：智能意图识别 → Agent Loop 直接修改，无需重新走完整三阶段

系统通过SSE实时推送Agent执行进度，前端以自然对话形式展示结果（内部思考过程默认隐藏，可在实时日志面板中查看）。

### 核心特性

- **ReAct Agent架构**: 三阶段自主规划（Plan → Execute → Verify）
- **MCP工具集成**: 通过SSE连接高德地图，实时验证地理数据
- **SSE实时推送**: Agent执行过程、工具调用实时可视化
- **自愈能力**: 工具失败自动重试、JSON截断自动修复
- **智能意图识别**: 关键词快速匹配 + AI分类器兜底，支持自然语言修改指令
- **自然对话体验**: 修改行程时隐藏原始思考过程，输出结构化自然语言总结
- **多轮对话**: 支持持续对话优化行程
- **技能系统**: 可扩展的技能插件机制
- **跨对话知识共享**: 用户可设置全局偏好，在所有对话中生效

## 技术架构

### 后端技术栈

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
| Playwright | 1.40.0 | 截图服务 |
| Thymeleaf | - | 模板引擎 |
| Maven | - | 构建工具 |

### 前端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5.25 | 前端框架 |
| TypeScript | 5.9.3 | 编程语言 |
| Element Plus | 2.13.2 | UI组件库 |
| Vite | 7.3.1 | 构建工具 |
| Pinia | 3.0.4 | 状态管理 |
| Vue Router | 5.0.2 | 路由管理 |
| Axios | 1.13.5 | HTTP客户端 |

## 项目结构

```
agent-project/
├── intellitravel-pioneer/        # 后端Spring Boot项目
│   ├── request/                  # 请求示例
│   ├── src/main/java/            # Java源码
│   │   ├── controller/           # REST控制器
│   │   │   ├── AmapController.java          # 地图API
│   │   │   ├── CapsuleController.java       # 胶囊管理
│   │   │   ├── CommunityController.java     # 社区管理
│   │   │   ├── ConversationController.java  # 对话管理
│   │   │   ├── ItineraryController.java     # 行程规划（SSE）
│   │   │   ├── RealTimeTripController.java  # 实时行程
│   │   │   ├── SkillController.java         # 技能管理
│   │   │   ├── SocialController.java        # 社交管理
│   │   │   ├── ToolsController.java         # 工具管理
│   │   │   ├── UserController.java          # 用户管理
│   │   │   ├── UserHealthController.java    # 用户健康
│   │   │   └── UserGlobalPreferenceController.java  # 全局偏好设置
│   │   ├── service/              # 业务逻辑层（包含多个服务类）
│   │   │   ├── ai/               # AI客户端（Agent Loop + Prompt + 意图识别）
│   │   │   │   ├── TripPlanningAiClient.java       # AI客户端接口
│   │   │   │   ├── HttpTripPlanningAiClient.java   # 实现（含全部Prompt模板）
│   │   │   │   ├── TripSummaryAiClient.java        # 行程摘要AI接口
│   │   │   │   ├── HttpTripSummaryAiClient.java    # 行程摘要AI实现
│   │   │   │   ├── AiImageClient.java              # AI图片生成接口
│   │   │   │   └── HttpAiImageClient.java          # AI图片生成实现
│   │   │   ├── serviceImpl/      # 服务实现
│   │   │   │   └── ItineraryServiceImpl.java       # 行程服务（意图识别入口）
│   │   │   ├── IntentStateMachine.java    # 意图状态机
│   │   │   ├── EntityTracker.java         # 实体追踪
│   │   │   ├── ActionTracker.java         # 操作历史
│   │   │   ├── MemoryManager.java         # 工作记忆
│   │   │   ├── ConversationContextManager.java  # 上下文管理（滑动窗口+摘要压缩）
│   │   │   ├── AmapService.java           # 地图服务
│   │   │   ├── CapsuleService.java        # 胶囊服务
│   │   │   ├── CommunityService.java      # 社区服务
│   │   │   ├── ConversationService.java   # 对话服务
│   │   │   ├── ImageService.java          # 图片服务
│   │   │   ├── ItineraryService.java      # 行程服务
│   │   │   ├── RealTimeTripService.java   # 实时行程服务
│   │   │   ├── SkillService.java          # 技能服务
│   │   │   ├── SocialService.java         # 社交服务
│   │   │   ├── ToolManagementService.java # 工具管理服务
│   │   │   ├── TripSummaryService.java    # 行程摘要服务
│   │   │   ├── TripSummaryGenerationEvent.java      # 行程摘要生成事件
│   │   │   ├── TripSummaryGenerationListener.java   # 行程摘要生成监听器
│   │   │   ├── TripSummaryGenerationStateStore.java # 行程摘要生成状态存储
│   │   │   ├── UserHealthService.java     # 用户健康服务
│   │   │   ├── UserService.java           # 用户服务
│   │   │   └── UserGlobalPreferenceService.java  # 全局偏好服务
│   │   ├── repository/           # 数据访问层
│   │   ├── po/                   # 持久化对象
│   │   ├── vo/                   # 视图对象
│   │   ├── dto/                  # 数据传输对象
│   │   ├── configure/            # 配置类
│   │   ├── enums/                # 枚举类
│   │   ├── exception/            # 异常处理
│   │   └── util/                 # 工具类
│   ├── src/main/resources/       # 配置文件
│   ├── pom.xml                   # Maven配置
│   └── intellitravel.sql         # 数据库脚本
│
├── planclient/                   # 前端Vue.js项目
│   ├── src/
│   │   ├── api/                  # API接口封装
│   │   │   ├── itinerary.ts           # 行程API
│   │   │   ├── conversation.ts        # 对话API
│   │   │   ├── preference.ts          # 全局偏好API
│   │   │   ├── skill.ts               # 技能API
│   │   │   ├── tools.ts               # 工具API
│   │   │   ├── trips.ts               # 行程API
│   │   │   ├── user.ts                # 用户API
│   │   │   └── _prefix.ts             # API前缀配置
│   │   ├── components/           # 通用组件
│   │   ├── composables/          # 组合式函数
│   │   │   ├── useBackHandler.ts      # 返回处理
│   │   │   ├── useConversation.ts     # 对话管理
│   │   │   ├── useDragDrop.ts         # 拖拽功能
│   │   │   └── useItinerary.ts        # 行程管理
│   │   ├── config/               # 配置文件
│   │   │   └── api.ts                 # API基础URL配置
│   │   ├── constants/            # 常量定义（工具名称/图标映射）
│   │   ├── router/               # 路由配置
│   │   ├── stores/               # Pinia状态管理
│   │   ├── types/                # TypeScript类型
│   │   ├── utils/                # 工具函数
│   │   └── views/                # 页面组件
│   │       ├── battle/           # 行程仓库
│   │       ├── conversation/     # 对话历史列表
│   │       ├── user/             # 用户登录/注册/偏好设置
│   │       │   ├── Dashboard.vue       # 首页仪表盘
│   │       │   └── UserPreferences.vue # 全局偏好设置页面
│   │       └── workspace/        # 工作区（核心页面集合）
│   │           ├── Workspace.vue         # AI行程规划工作台
│   │           ├── ItineraryCanvas.vue   # 行程画布（时间线+拖拽编辑）
│   │           ├── ItineraryCard.vue     # 行程卡片
│   │           ├── FormInput.vue         # 结构化表单输入
│   │           ├── ChatInput.vue         # 自然语言输入
│   │           └── ToolCallCard.vue      # 工具调用展示
│   ├── package.json
│   └── vite.config.ts
│
└── README.md
```

## 前端路由

| 路径 | 页面 | 说明 |
|------|------|------|
| `/` | - | 重定向至 `/dashboard` |
| `/login` | Login | 用户登录 |
| `/register` | Register | 用户注册 |
| `/dashboard` | Dashboard | 首页仪表盘 |
| `/workspace` | Workspace | **核心页面**，支持 `?convId=` 恢复对话、`?itineraryId=` 编辑行程 |
| `/battle` | BattleCockpit | 行程仓库，管理已保存的行程 |
| `/battle/skills` | SkillManage | 技能工作台 |
| `/tools` | ToolsManagement | 工具管理 |
| `/conversation` | ConversationList | 对话历史列表 |
| `/preferences` | UserPreferences | 全局偏好设置 |

## 核心功能

### 1. AI Agent行程规划

**新建行程—三阶段架构**:
```
Phase 1: PLAN（规划阶段）
    ├── 输入：用户需求（目的地、天数、兴趣、预算）
    ├── LLM制定策略：需求分析 → 任务分解 → 执行策略
    └── 输出：规划策略文本

Phase 2: EXECUTE（执行阶段）
    ├── Agent Loop（最多15轮）：
    │   ├── Thought：分析当前进度
    │   ├── Tool Call：调用高德地图MCP工具验证真实数据
    │   ├── SelfCheck：自检完整性/真实性/合理性
    │   └── final_output：输出完整JSON行程
    └── 工具失败自动重试（最多2轮）

Phase 3: VERIFY（审核阶段）
    ├── 独立审核员检查：完整性、真实性、合理性、预算匹配
    ├── 不通过则自动修正（最多2轮）
    └── 输出审核结论
```

**修改行程—意图识别 + Agent Loop**:
```
用户消息（如"我想多玩一天""把故宫换成天坛"）
  │
  ├── 第一层：关键词快速匹配（IntentStateMachine.detectIntent）
  │     ├── 52个修改关键词（"改成""删除""增加"等）
  │     ├── 0延迟，立即响应
  │     └── 匹配成功 → 直接返回意图
  │
  ├── 第二层：AI分类器兜底（HttpTripPlanningAiClient.classifyIntent）
  │     ├── 轻量级LLM调用（max_tokens=10, temperature=0）
  │     ├── 处理自然表达（"多玩一天""想换个地方"等）
  │     └── 分类结果：CHAT 或 MODIFY
  │
  ├── 操作类型识别（detectActionType）
  │     ├── DELETE：删除条目（"删除""去掉""移除""删掉"）
  │     ├── ADD：新增条目（"增加""添加""新增"）
  │     ├── UPDATE_DAYS：更新天数（"改为5天""多玩一天""延长"）
  │     ├── MODIFY：修改条目（"改成""换成""修改""变为"）
  │     └── UNKNOWN：未知操作（Agent自行判断）
  │
  └── Agent Loop（修改专用 Prompt）
        ├── 天数变更：自动搜索填充新景点
        ├── 增删替换：调用高德地图MCP验证
        └── final_output：返回修正后的完整行程
```

### 2. MCP工具集成

通过SSE协议连接高德地图MCP服务，Agent可调用的工具：

- `maps_text_search` — 地点搜索
- `maps_around_search` — 周边搜索
- `maps_weather` — 天气查询
- `maps_direction_*` — 路线规划
- `maps_schema_*` — 地图链接生成
- `search_destination_info` — 内置12城市旅行知识库

### 3. SSE实时推送

前端通过 `fetch` + `ReadableStream` 连接后端SSE，实时接收：

- `phase` 事件：阶段进度
- `thought` 事件：Agent思考过程（显示在可折叠的实时日志面板中）
- `tool` 事件：工具调用详情
- `result` 事件：最终行程JSON
- `stats` 事件：Token使用统计
- `conversationId` 事件：对话ID

### 4. 行程画布

右侧画布实时展示行程时间线，支持：

- **时间线视图**: 按天分组，清晰展示每日行程项
- **点击展开**: 查看地点描述、坐标信息，一键跳转高德地图
- **拖拽排序**: 跨天拖拽调整行程项顺序
- **删除操作**: 每个行程项独立删除按钮
- **空状态引导**: 无行程时展示画布功能介绍

### 5. 对话系统

- **多轮对话**: 支持持续对话优化行程
- **对话历史**: 保存所有对话记录，按标签/标题搜索
- **行程关联**: 对话自动关联生成的行程，卡片展示"已有行程"标识
- **自然回复**: AI回复为结构化自然语言，不再展示原始思考过程
- **实时日志**: 思考过程默认折叠，需要时可点击展开查看

### 6. 智能意图识别

双通道架构确保准确性和响应速度：

#### 第一层：关键词快速匹配
- **组件**：`IntentStateMachine.detectIntent()`
- **机制**：52个修改关键词（"改成""删除""增加"等）
- **性能**：0延迟，立即响应
- **场景**：明确指令，如"把故宫换成天坛"、"删掉第二天"

#### 第二层：AI分类器兜底
- **组件**：`HttpTripPlanningAiClient.classifyIntent()`
- **机制**：轻量级LLM调用（max_tokens=10, temperature=0）
- **性能**：快速分类，处理自然表达
- **场景**：自然表达，如"多玩一天"、"想换个地方"

#### 意图类型
| 意图 | 说明 | 触发条件 |
|------|------|----------|
| `PLAN_START` | 开始规划 | 用户请求生成行程 |
| `PLAN_MODIFY` | 修改行程 | 用户要求修改现有行程 |
| `PLAN_QUERY` | 查询信息 | 用户询问行程相关信息 |
| `PLAN_CONFIRM` | 确认操作 | 用户确认当前操作 |
| `PLAN_COMPLETE` | 完成规划 | 用户表示行程已完成 |
| `PLAN_REPEAT_LAST` | 重复上一次 | 用户要求重复上一次操作 |
| `PLAN_SHOW_HISTORY` | 显示历史 | 用户查看操作历史 |

#### 状态机管理
- **状态类型**：`IDLE`、`GATHERING_INFO`、`PLANNING`、`MODIFYING`、`REVIEWING`
- **状态转换**：根据意图自动转换状态
- **上下文保持**：跨消息保持对话上下文

#### AI分类器优化
1. **修改意图识别**：
   - 明确指令："把故宫换成天坛"、"删掉第二天"
   - 自然表达："多玩一天"、"想换个地方"
   - 关键词："推荐"≠MODIFY，除非说"把XX加到行程中"

2. **生成意图识别**：
   - 正则快速判断：城市名 + 时间信息
   - AI兜底：处理模糊请求
   - 默认策略：无法确定时返回CHAT，避免意外修改

### 7. 技能与工具系统

- **技能管理**: 创建、编辑、删除自定义技能
- **工具管理**: 查看和配置可用的MCP工具
- **技能市场**: 浏览和使用社区技能

### 8. 用户系统

- 用户注册/登录（JWT认证）
- 个人信息管理
- 关注/粉丝系统
- 社区帖子/评论/点赞/收藏

## 数据库设计

包含16张核心表：

- **用户系统**: user, user_health, user_follow, user_friend, user_global_preference
- **行程系统**: itinerary, real_time_trip, trip_trajectory_map
- **对话系统**: conversation, conversation_message
- **社区系统**: community_post, community_comment, community_like
- **旅行报告**: trip_report, trip_summary, challenge_record

## API接口

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

## 快速开始

### 环境要求

- Java 21+
- Node.js 18+
- MySQL 8.0+
- Maven 3.8+

### 后端启动

```bash
cd intellitravel-pioneer
# 配置环境变量
export SILICONFLOW_API_KEY=your_api_key
export AMAP_KEY=your_amap_key

# 启动应用
./mvnw spring-boot:run
```

### 前端启动

```bash
cd planclient
npm install
npm run dev
```

### 访问地址

- 前端: http://localhost:5173
- 后端API: http://localhost:8080

## 项目状态

**当前版本**: 活跃开发中
**最后更新**: 2026年6月19日
**主要方向**: Agent架构优化、意图识别增强、前端体验提升、跨对话知识共享

## 许可证

本项目仅供学习交流使用

---

> 本项目展示了如何将ReAct Agent架构应用于实际业务场景，实现了AI自主规划、工具调用、智能意图识别、实时交互的完整闭环。
