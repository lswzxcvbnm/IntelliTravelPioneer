# IntelliTravel Pioneer

> 一个可规划、会查证、能持续修改的 AI 旅行 Agent。它把自然语言需求转化为可执行行程，并通过地图工具、实时推送与可视化画布完成从规划到管理的完整闭环。

IntelliTravel Pioneer 是一个前后端分离的全栈 AI 应用。用户只需描述目的地、时间、预算和偏好，Agent 就会自主拆解任务、查询真实地点、生成逐日安排，并继续响应“多玩一天”“把故宫换成天坛”等自然语言修改。

## 快速开始

### 1. 环境准备

| 依赖 | 建议版本 |
|------|----------|
| Java | 21+ |
| Node.js | 20+ |
| MySQL | 8.0+ |
| Maven | 无需单独安装，项目已包含 Maven Wrapper |

克隆项目并进入目录：

```bash
git clone git@github.com:lswzxcvbnm/IntelliTravelPioneer.git
cd IntelliTravelPioneer
```

### 2. 初始化数据库

先创建数据库：

```sql
CREATE DATABASE intellitravel
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

随后在 MySQL 客户端中导入表结构与初始化数据：

```sql
USE intellitravel;
SOURCE intellitravel-pioneer/intellitravel.sql;
```

### 3. 配置环境变量

启动后端至少需要数据库、DeepSeek 和高德地图配置。不要把真实密钥写入配置文件或提交到 Git。

<details>
<summary>Linux / macOS</summary>

```bash
export DB_URL='jdbc:mysql://localhost:3306/intellitravel?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8'
export DB_USERNAME='root'
export DB_PASSWORD='your_database_password'
export DEEPSEEK_API_KEY='your_deepseek_api_key'
export AMAP_KEY='your_amap_web_service_key'
export AMAP_MCP_KEY='your_amap_mcp_key'
```

</details>

<details>
<summary>Windows PowerShell</summary>

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/intellitravel?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = 'your_database_password'
$env:DEEPSEEK_API_KEY = 'your_deepseek_api_key'
$env:AMAP_KEY = 'your_amap_web_service_key'
$env:AMAP_MCP_KEY = 'your_amap_mcp_key'
```

</details>

图片生成和对象存储是可选能力，需要时再配置：

```text
SILICONFLOW_API_KEY
OSS_ACCESS_KEY_ID
OSS_ACCESS_KEY_SECRET
```

### 4. 启动后端

```bash
cd intellitravel-pioneer

# Linux / macOS
./mvnw spring-boot:run

# Windows PowerShell
# .\mvnw.cmd spring-boot:run
```

后端默认运行在 `http://localhost:8080`。

### 5. 启动前端

确认 `planclient/src/config/api.ts` 导出了本地后端地址：

```ts
export const API_BASE_URL = 'http://localhost:8080'
```

打开另一个终端：

```bash
cd planclient
npm install
npm run dev
```

访问 `http://localhost:5173`，注册账号后即可开始规划行程。

## 为什么选择 IntelliTravel Pioneer

### 从一句话到完整行程

```text
自然语言需求
   ↓
意图识别与需求补全
   ↓
Plan：制定规划策略
   ↓
Execute：循环调用地图工具并组织行程
   ↓
Verify：独立审核并自动修正
   ↓
实时画布展示 → 对话修改 → 保存与分享
```

它不是一次性输出文本的聊天机器人，而是围绕旅行规划建立了一条可持续迭代的 Agent 工作流。

### 核心亮点

| 能力 | 项目亮点 |
|------|----------|
| **自主规划 Agent** | 使用 Plan → Execute → Verify 三阶段架构；执行阶段最多进行 15 轮工具调用，审核失败时可自动修正 |
| **真实数据查证** | 通过高德地图 MCP 查询地点、周边、天气和路线，减少模型凭空生成地点或交通信息 |
| **实时可观测** | 基于 SSE 推送阶段进度、工具调用、结果和 Token 统计；前端可折叠查看 Agent 实时日志 |
| **自然语言改行程** | 支持“增加一天”“删除第二天”“把 A 换成 B”等表达，无需重新生成整份行程 |
| **双通道意图识别** | 明确指令由关键词与状态机快速处理，复杂表达交给轻量级 AI 分类器兜底 |
| **可视化行程画布** | 按天展示时间线，支持跨天拖拽、排序、删除、详情展开和地图跳转 |
| **长期用户偏好** | 饮食、预算、节奏等偏好可跨对话生效，让后续规划延续用户习惯 |
| **故障自愈** | 工具调用失败自动重试，模型输出截断或 JSON 异常时尝试修复，并在最终阶段再次审核 |
| **完整产品闭环** | 覆盖账号、对话、行程仓库、实时旅行、技能工具、社区互动与旅行总结 |

## 产品体验

### AI 行程工作台

- 支持结构化表单和自然语言两种输入方式。
- 规划过程持续流式更新，不必等待整份行程生成完毕。
- 对话与行程双向关联，可从历史会话恢复上下文。
- 原始思考与工具日志默认折叠，既保持界面简洁，也便于调试和演示。

### 行程画布

- 按天组织景点、活动与时间信息。
- 支持跨天拖拽调整顺序，手动编辑与 AI 修改可以并存。
- 展开卡片即可查看地点说明、坐标和地图入口。
- 修改结果直接回写完整行程，避免局部变更造成上下文丢失。

### 对话式修改

系统会结合当前行程识别用户意图和具体操作：

| 用户表达 | 识别结果 |
|----------|----------|
| “再增加一天” | 更新行程天数，并搜索合适地点补全新一天 |
| “删掉第二天下午的景点” | 定位对应条目并删除 |
| “把故宫换成天坛” | 查询并验证新地点后完成替换 |
| “推荐附近的餐厅” | 保持查询语义，不误改现有行程 |
| “重复上一次修改” | 从操作历史中恢复并重新执行 |

### 个性化与长期记忆

- 全局偏好在不同对话间共享。
- 对话上下文使用滑动窗口和摘要压缩，兼顾连贯性与 Token 成本。
- 操作历史支持查询、重复上一步和按序号重放。
- 行程、对话和用户偏好共同构成可持续演进的旅行档案。

## Agent 核心机制

### 新建行程：Plan → Execute → Verify

```text
PLAN
├── 分析目的地、时间、预算与兴趣
├── 判断缺失信息
└── 生成任务拆解和执行策略

EXECUTE
├── Thought：判断下一步行动
├── Tool Call：查询地点、天气、路线等真实数据
├── Observation：合并工具返回结果
├── Self Check：检查完整性与合理性
└── Final Output：输出结构化行程

VERIFY
├── 审核完整性、真实性、节奏与预算
├── 发现问题后进入修正循环
└── 返回可展示、可保存、可继续修改的结果
```

### 修改行程：快速识别 + Agent Loop

```text
用户消息
├── 关键词快速通道：处理明确的增、删、改、天数调整
└── AI 分类通道：理解更自然或含糊的表达
        ↓
识别 DELETE / ADD / UPDATE_DAYS / MODIFY
        ↓
调用地图工具查证 → 生成修改后的完整行程 → SSE 返回结果
```

### MCP 工具能力

Agent 可按需使用高德地图 MCP 与内置旅行知识工具：

- 地点关键词与周边搜索
- 天气查询
- 驾车、步行和公共交通路线规划
- 地图链接生成
- 目的地信息检索

工具层与模型推理解耦，便于继续接入酒店、票务、搜索或其他 MCP 服务。

## 系统架构

```text
┌──────────────────────────────────────────────────────┐
│ Vue 3 + TypeScript                                   │
│ 对话工作台 · 行程画布 · 实时日志 · 偏好与行程管理     │
└───────────────────────┬──────────────────────────────┘
                        │ REST + SSE
┌───────────────────────▼──────────────────────────────┐
│ Spring Boot 4 / Java 21                              │
│ 认证 · 对话上下文 · 意图状态机 · Agent 编排 · 业务服务 │
└───────────────┬───────────────────────┬──────────────┘
                │                       │
       ┌────────▼────────┐     ┌────────▼──────────────┐
       │ MySQL 8         │     │ 外部能力              │
       │ 用户/对话/行程  │     │ DeepSeek · 高德 MCP   │
       │ 偏好/社区数据   │     │ SiliconFlow · OSS     │
       └─────────────────┘     └───────────────────────┘
```

### 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、Axios |
| 后端 | Java 21、Spring Boot 4、Spring Data JPA、Maven |
| AI 与工具 | DeepSeek、ReAct Agent、高德地图 MCP、SSE |
| 数据与存储 | MySQL 8、阿里云 OSS（可选） |
| 安全 | JWT、BCrypt、环境变量密钥管理 |

## 项目结构

```text
agent-project/
├── intellitravel-pioneer/        # Spring Boot 后端
│   ├── src/main/java/
│   │   └── .../
│   │       ├── controller/       # Web 入口与 SSE 端点
│   │       ├── service/          # Agent、意图、上下文及业务服务
│   │       ├── repository/       # 数据访问层
│   │       ├── po/ dto/ vo/      # 数据模型
│   │       └── configure/ util/  # 配置与基础设施
│   ├── src/main/resources/       # 应用配置
│   ├── intellitravel.sql         # 数据库初始化脚本
│   └── pom.xml
├── planclient/                   # Vue 前端
│   ├── src/
│   │   ├── views/                # 工作台、行程、用户与管理页面
│   │   ├── components/           # 通用组件
│   │   ├── composables/          # 对话、拖拽和行程逻辑
│   │   ├── api/                  # 请求封装
│   │   └── stores/               # Pinia 状态管理
│   └── package.json
├── Deployment.md                 # 完整部署指南
├── deploy.ps1                    # 自动化部署脚本
└── README.md
```

## 数据模型与业务范围

系统包含用户、行程、对话、社区和旅行总结等核心数据域：

- **用户域**：账号、健康信息、关注关系、好友和全局偏好。
- **行程域**：规划行程、实时旅行和轨迹地图。
- **对话域**：会话、消息、上下文摘要和行程关联。
- **社区域**：帖子、评论、点赞与收藏。
- **总结域**：旅行报告、行程摘要和挑战记录。

## 开发与验证

运行后端测试：

```bash
cd intellitravel-pioneer
./mvnw test
```

检查前端类型并构建生产资源：

```bash
cd planclient
npm run build
```

## 部署

项目支持传统服务器部署和 Docker 容器化部署，包含 Nginx、HTTPS、systemd、CI/CD、更新及回滚说明。详见 [Deployment.md](./Deployment.md)。

## 项目状态

项目处于持续迭代阶段，当前重点包括：

- 增强 Agent 规划与自我修正能力。
- 扩展更多真实世界旅行工具。
- 优化长对话记忆与 Token 使用效率。
- 完善行程协作、分享和社区体验。

## 安全提示

- 所有 API Key、数据库密码和 JWT 密钥都应通过环境变量或密钥管理服务注入。
- 不要在 README、配置默认值、测试请求或构建产物中提交真实凭据。
- 如果密钥曾进入 Git 历史，仅删除当前文件内容并不够；还需轮换密钥并清理历史提交。

## 许可证

本项目仅供学习与交流使用。

---

IntelliTravel Pioneer 展示了如何把 Agent 推理、真实工具调用、流式交互与可编辑业务状态组合成一个完整的 AI 原生应用。
