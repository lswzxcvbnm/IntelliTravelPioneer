# IntelliTravel Pioneer - AI旅行规划Agent系统

> 基于ReAct Agent架构的智能旅行规划系统，实现AI自主思考、工具调用、实时交互的完整闭环

## 项目概述

IntelliTravel Pioneer 是一个**AI Agent应用**，采用Plan-Execute-Verify三阶段架构，通过MCP协议集成高德地图工具，实现AI自主规划旅行行程。系统通过SSE实时推送Agent思考过程，让用户透明地看到AI的决策逻辑。

### 核心特性

- **ReAct Agent架构**: 三阶段自主规划（Plan → Execute → Verify）
- **MCP工具集成**: 通过SSE连接高德地图，实时验证地理数据
- **SSE实时推送**: Agent思考过程、工具调用实时可视化
- **自愈能力**: 工具失败自动重试、JSON截断自动修复
- **智能行程修改**: AI自主识别用户意图（删除/替换/修改）
- **多轮对话**: 支持持续对话优化行程
- **技能系统**: 可扩展的技能插件机制

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
├── intellitravel-pioneer/    # 后端Spring Boot项目
│   ├── src/main/java/        # Java源码
│   │   ├── controller/       # REST控制器
│   │   │   ├── AmApiController.java      # 地图API
│   │   │   ├── ConversationController.java # 对话管理
│   │   │   ├── ItineraryController.java   # 行程规划
│   │   │   ├── SkillController.java       # 技能管理
│   │   │   ├── ToolController.java        # 工具管理
│   │   │   └── UserController.java        # 用户管理
│   │   ├── service/          # 业务逻辑层
│   │   │   ├── agent/        # Agent核心逻辑
│   │   │   ├── amap/         # 高德地图服务
│   │   │   ├── mcp/          # MCP协议服务
│   │   │   └── skill/        # 技能服务
│   │   ├── repository/       # 数据访问层
│   │   ├── entity/           # 实体类
│   │   ├── dto/              # 数据传输对象
│   │   ├── configure/        # 配置类
│   │   └── exception/        # 异常处理
│   ├── src/main/resources/   # 配置文件、模板
│   ├── pom.xml               # Maven配置
│   └── intellitravel.sql     # 数据库脚本
│
├── planclient/               # 前端Vue.js项目
│   ├── src/
│   │   ├── api/              # API接口封装
│   │   │   ├── chat.ts           # AI聊天接口
│   │   │   ├── conversation.ts   # 对话管理接口
│   │   │   ├── itinerary.ts      # 行程规划接口
│   │   │   ├── skill.ts          # 技能管理接口
│   │   │   ├── tools.ts          # 工具管理接口
│   │   │   ├── trips.ts          # 旅行管理接口
│   │   │   └── user.ts           # 用户接口
│   │   ├── components/       # 通用组件
│   │   │   └── BackButton.vue    # 返回按钮
│   │   ├── composables/      # 组合式函数
│   │   │   ├── useConversation.ts # 对话状态
│   │   │   ├── useDragDrop.ts     # 拖拽功能
│   │   │   └── useItinerary.ts    # 行程状态
│   │   ├── constants/        # 常量定义
│   │   │   └── tool.ts           # 工具常量
│   │   ├── router/           # 路由配置
│   │   ├── stores/           # Pinia状态管理
│   │   ├── types/            # TypeScript类型
│   │   ├── utils/            # 工具函数
│   │   └── views/            # 页面组件
│   │       ├── battle/       # Agent管理页面
│   │       ├── conversation/ # 对话模块
│   │       ├── user/         # 用户模块
│   │       └── workspace/    # 工作区（核心）
│   ├── package.json          # Node.js配置
│   └── vite.config.ts        # Vite配置
│
└── README.md                 # 项目说明
```

## 前端路由

| 路径 | 页面 | 说明 |
|------|------|------|
| `/` | - | 重定向至 `/dashboard` |
| `/login` | Login | 用户登录 |
| `/register` | Register | 用户注册 |
| `/dashboard` | Dashboard | 首页仪表盘 |
| `/workspace` | Workspace | **AI行程规划**（核心页面） |
| `/battle` | BattleCockpit | 行程仓库 |
| `/battle/skills` | SkillManage | 技能工作台 |
| `/tools` | ToolsManagement | 工具管理 |
| `/conversation` | ConversationList | 对话历史列表 |
| `/conversation/:id` | ConversationDetail | 对话详情 |

## 核心功能

### 1. AI Agent行程规划

**三阶段架构**:
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
    └── 工具失败自动重试（最多2次）

Phase 3: VERIFY（审核阶段）
    ├── 独立审核员检查：完整性、真实性、合理性、预算匹配
    ├── 不通过则自动修正（最多2轮）
    └── 输出审核结论
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
- `phase` 事件：三阶段进度
- `thought` 事件：Agent的思考过程
- `tool` 事件：工具调用详情
- `result` 事件：最终行程JSON
- `stats` 事件：Token使用统计

### 4. 行程管理

- **新建行程**: AI自主规划，支持表单/对话两种输入模式
- **修改行程**: 智能意图识别（删除/替换/修改描述）
- **行程存储**: JSON格式，支持拖拽排序、编辑
- **行程分享**: 生成精美海报（Thymeleaf + Playwright）

### 5. 对话系统

- **多轮对话**: 支持持续对话优化行程
- **对话历史**: 保存所有对话记录，可随时查看
- **行程关联**: 对话自动关联生成的行程
- **实时日志**: 显示Agent思考和工具调用过程

### 6. 技能与工具系统

- **技能管理**: 创建、编辑、删除自定义技能
- **工具管理**: 查看和配置可用的MCP工具
- **技能市场**: 浏览和使用社区技能

### 7. 用户系统

- 用户注册/登录（JWT认证）
- 个人信息管理
- 关注/粉丝系统
- 社区帖子/评论/点赞/收藏

## 数据库设计

包含15张核心表：
- **用户系统**: user, user_health, user_follow, user_friend
- **行程系统**: itinerary, real_time_trip, trip_trajectory_map
- **对话系统**: conversation, conversation_message
- **社区系统**: community_post, community_comment, community_like
- **旅行报告**: trip_report, trip_summary, challenge_record

## API接口

### 用户模块 (`/api/user`)
- `POST /api/user` — 用户注册
- `POST /api/user/login` — 用户登录
- `GET /api/user/{username}` — 获取用户信息

### 行程规划模块 (`/api/itinerary`)
- `POST /api/itinerary/generate/stream` — SSE流式生成行程
- `POST /api/itinerary/modify/stream` — SSE流式修改行程
- `POST /api/itinerary/save` — 保存行程
- `PUT /api/itinerary/{id}` — 更新行程
- `GET /api/itinerary/detail/{id}` — 获取行程详情
- `GET /api/itinerary/list` — 获取行程列表

### 对话模块 (`/api/conversation`)
- `POST /api/conversation` — 创建对话
- `GET /api/conversation/list` — 获取对话列表
- `GET /api/conversation/{id}/messages` — 获取对话消息
- `POST /api/conversation/{id}/link` — 关联行程到对话

### 技能模块 (`/api/skill`)
- `GET /api/skill/list` — 获取技能列表
- `POST /api/skill` — 创建技能
- `PUT /api/skill/{id}` — 更新技能
- `DELETE /api/skill/{id}` — 删除技能

### 工具模块 (`/api/tools`)
- `GET /api/tools/list` — 获取工具列表
- `POST /api/tools/{id}/toggle` — 启用/禁用工具

### 地图模块 (`/api/amap`)
- `POST /api/amap/geocode` — 地理编码
- `GET /api/amap/weather` — 天气查询

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
**最后更新**: 2026年6月14日  
**主要方向**: Agent架构优化、用户体验提升

## 许可证

本项目仅供学习交流使用

---

> 本项目展示了如何将ReAct Agent架构应用于实际业务场景，实现了AI自主规划、工具调用、实时交互的完整闭环。
