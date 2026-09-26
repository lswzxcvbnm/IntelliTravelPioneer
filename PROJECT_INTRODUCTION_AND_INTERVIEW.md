# IntelliTravel Pioneer 项目介绍与面试准备

> 本文基于仓库当前代码整理，重点覆盖项目定位、核心功能、架构设计、技术实现、面试问题与参考回答，以及一份约两分钟的英文项目介绍。

## 1. 一句话介绍

IntelliTravel Pioneer 是一个基于大语言模型与 MCP 工具调用的智能旅行规划 Agent：它能够理解用户的自然语言需求，自主查询天气、POI 和路线等真实地图信息，生成按天组织的结构化行程，并支持通过多轮对话继续修改、保存和可视化编辑行程。

## 2. 项目解决的问题

传统旅行规划往往需要在攻略、地图、天气、路线和预算等多个平台之间来回切换。单纯让大模型一次性生成攻略，又容易出现地点不存在、坐标错误、路线不合理、天数不一致等问题。

本项目把旅行规划拆成“理解需求—制定策略—调用工具—生成方案—审核修正”的 Agent 工作流，用高德地图 MCP 提供外部事实依据，再通过结构化 JSON 将结果落到可编辑的行程画布中。它解决的核心不是“生成一段旅行文案”，而是让模型完成一个有状态、可验证、可修改、可持久化的业务任务。

## 3. 当前代码规模与技术栈

### 3.1 代码规模

按当前仓库静态扫描结果：

- 后端包含 12 个 Controller、31 个 PO、27 个 Repository。
- `intellitravel.sql` 明确定义了 26 张表；JPA 实体数量更多，说明运行时模型和 SQL 初始化脚本可能仍在同步演进。
- 后端测试目录中共有 267 处 `@Test`，覆盖用户、行程、实时旅行、高德地图、社区和胶囊等模块。
- 前端核心工作区由对话区、Agent 实时日志和行程画布组成。

### 3.2 技术栈

| 层次 | 主要技术 | 用途 |
|---|---|---|
| 前端 | Vue 3、TypeScript、Vite、Element Plus | 页面与组件化开发 |
| 前端状态 | Pinia、Composition API | 行程、认证与页面状态管理 |
| 通信 | Axios、Fetch、ReadableStream、SSE | 普通 REST 请求与 Agent 流式事件 |
| 后端 | Java 21、Spring Boot 4.0.3、Spring MVC | REST API、SSE 与业务编排 |
| 数据访问 | Spring Data JPA、MySQL | 用户、对话、行程、工具记录等持久化 |
| AI | OpenAI 兼容的 Chat Completions 接口 | 意图分类、需求提取、规划、执行与审核 |
| 工具协议 | MCP、SSE、JSON-RPC 2.0 | 动态发现并调用高德地图工具 |
| 安全 | JWT、BCrypt、Spring MVC Interceptor | 登录认证与密码哈希 |
| 其他 | 阿里云 OSS、Thymeleaf | 媒体存储与模板渲染 |

## 4. 总体架构设计

```text
┌──────────────────────────────────────────────────────────────┐
│                         Vue 3 前端                            │
│  自然语言/表单输入 │ 对话历史 │ Agent 日志 │ 可拖拽行程画布    │
└───────────────────────────┬──────────────────────────────────┘
                            │ REST + POST SSE Stream
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                  Spring Boot 接口与业务层                     │
│  Controller → Service → Repository                            │
│       │                                                       │
│       ├─ Conversation / Itinerary 持久化                       │
│       └─ HttpTripPlanningAiClient（Agent 核心编排）            │
│            ├─ 意图分类与结构化需求提取                         │
│            ├─ Plan → Execute → Verify                         │
│            ├─ Agent Loop / JSON 修复 / 重试 / 降级             │
│            └─ 状态、实体、操作历史、三级记忆、技能与工具偏好    │
└───────────────┬────────────────────┬─────────────────────────┘
                │                    │
                ▼                    ▼
       OpenAI 兼容 LLM API     高德地图 MCP Server
                              initialize / tools/list / tools/call
                │                    │
                └──────────┬─────────┘
                           ▼
                         MySQL
```

项目整体采用前后端分离和经典分层架构。`ItineraryController` 负责 HTTP/SSE 边界，`ItineraryServiceImpl` 负责认证校验、对话创建或复用、消息落库及结果关联，`HttpTripPlanningAiClient` 负责 AI 工作流编排。Repository 层通过 JPA 持久化用户、行程、对话、偏好、工具调用和其他业务数据。

这种设计的优点是业务持久化与 Agent 推理相对解耦：将来替换模型、地图服务或前端交互方式时，不需要重写全部业务层。

## 5. 核心功能与实现细节

### 5.1 自然语言生成行程

用户既可以填写目的地、天数、兴趣、预算等结构化表单，也可以直接输入“帮我规划一个成都三天的美食之旅”。自然语言请求首先经过轻量意图分类，区分 `CHAT` 与 `GENERATE`；对于生成请求，再由模型提取为 `ItineraryRequest`，包括：

- `destination`
- `days`
- `budgetLevel`
- `interests`
- `additionalNotes`
- 可选的 `skillIds`、`conversationId` 和原始 `userPrompt`

这样做把开放式自然语言转换为稳定的领域对象，后续规划、保存和前端渲染都围绕同一份数据契约进行。

### 5.2 Plan–Execute–Verify Agent 工作流

#### Phase 1：Plan

规划阶段不开放工具，只让模型分析约束并生成执行策略，例如需要查询哪些天气、景点、餐饮与路线信息。该阶段的价值是先形成全局计划，降低模型在工具选择上的随机性。如果规划调用失败，代码会降级为通用旅行规划策略，不直接终止整个请求。

#### Phase 2：Execute

执行阶段将系统提示词、用户画像、会话上下文、技能内容、当前状态和 Plan 结果组合后传给模型，并把 MCP 工具转换为标准 function schema。核心循环是：

```text
LLM 思考
   ├─ 返回 tool_calls → 执行工具 → 将 tool result 加回 messages → 下一轮
   └─ 返回 final_output → 解析 ItineraryPlan → 规范化 → 结束
```

这是一种 ReAct-like 实现：模型根据当前观察决定下一步行动，工具结果作为新的上下文反馈给模型，而不是由后端预先写死每一个调用顺序。

#### Phase 3：Verify

执行阶段生成草案后，系统使用独立的审核提示词检查完整性、真实性、路线合理性、预算和天数一致性。审核返回 `PASS` 时直接通过；返回问题列表时，会把问题重新反馈给模型，要求用 `final_output` 输出修正后的完整行程。审核最多执行两轮。

这里的“独立”是指独立的审核阶段与 Prompt，并不是完全独立的模型服务。因此它能降低错误率，但不能被表述为数学意义上的正确性保证。

### 5.3 多轮对话与增量修改

修改已有行程不会重新走完整的 Plan–Execute–Verify，而是走更短的 Refine 链路：

```text
用户修改指令
  → AI 结构化意图分类
  → CHAT / NEW_ITINERARY / MODIFY 分流
  → 提取修改类型、目标和细节
  → 加载当前行程、上下文、记忆和实体
  → Agent Loop 调用必要工具验证新增或替换地点
  → 返回更新后的完整行程
```

修改类型包括替换、添加、删除、调整和交换。代码还对“多玩一天”“改为五天”等天数变化做了专门解析，在进入 Agent Loop 前更新请求天数，减少模型遗漏新增日期的概率。

对于闲聊或行程问答，系统返回自然语言回答并保留原行程；如果用户在已有行程中要求创建全新目的地行程，系统会提示返回并新建规划，避免误覆盖。

### 5.4 MCP 地图工具集成

`AmapMcpClient` 自行实现了 MCP 客户端流程：

1. 建立 SSE 长连接，取得服务端 POST endpoint。
2. 发送 JSON-RPC `initialize`，协议版本为 `2024-11-05`。
3. 发送 `notifications/initialized`。
4. 调用 `tools/list` 动态发现工具及输入 Schema。
5. 将 MCP Schema 转换为模型可理解的 function tools。
6. Agent 通过 `tools/call` 调用具体工具，结果作为 `role=tool` 消息加入上下文。

当前工具定义覆盖天气、POI 文本/周边搜索、地点详情、地理编码、距离计算、驾车/步行/骑行/公共交通路线、导航或地图链接等。系统也提供 `search_destination_info` 内置知识库工具。

选择 MCP 而不是把所有地图 API 写死在 Agent 中，主要是为了标准化工具发现与调用。后续增加新工具时，模型可以根据描述和 JSON Schema 动态使用，Agent 主循环无需为每个工具增加新的分支。

### 5.5 离线降级与容错

地图 MCP 初始化失败时，生成流程不会立即失败，而是切换到内置旅行知识库模式。当前知识库覆盖北京、上海、杭州、成都、西安、广州、深圳、重庆、南京、厦门、三亚和丽江等城市，并对不可用的实时天气、路线和地图链接返回明确的降级提示。

需要注意：离线模式提升了可用性，但此时无法保证信息实时性。因此面试中应表述为“在线时使用地图工具验证，离线时提供可解释的降级结果”，不要说成“所有结果都一定来自实时地图”。

### 5.6 Agent 稳定性设计

项目对大模型输出的不确定性做了多层防御：

- 工具参数先尝试修复截断 JSON。
- 同一工具、相同参数的重复计数达到阈值时终止，防止确定性死循环。
- 同一工具即使参数不同，连续调用也有默认 25 次硬上限。
- 普通工具失败最多重试 2 次，地图 Schema 类工具最多重试 3 次。
- Execute 阶段整体异常最多重新初始化 2 次。
- 检测到 `finish_reason=length` 时，以更大的 `max_tokens` 重试。
- 模型把工具调用错误地输出为 XML `<invoke>` 时，使用回退解析器提取并执行。
- 最终结果优先提取 `final_output`，再剥离 Markdown 代码块、扫描完整 JSON 对象、修复括号并兼容 `data.itinerary` 包装结构。
- 普通解析失败时尝试切换为流式模型调用重新生成。

当前 `runAgentLoop` 使用 `while (true)`，没有统一的总轮数或总成本上限，主要依赖重复调用检测和外层 600 秒 SSE 超时止损。这是一个明确的后续改进点：应增加 `maxTurns`、Token 预算、累计工具耗时和客户端取消信号。

### 5.7 SSE 实时交互

后端通过 `SseEmitter` 返回最多 600 秒的流式响应，并使用 `AtomicBoolean` 避免超时、正常完成和异常分支重复关闭连接。主要事件包括：

| 事件 | 内容 |
|---|---|
| `phase` | PLAN、EXECUTE、VERIFY、REFINE 等阶段状态 |
| `thought` | 可展示的推理摘要或处理进度 |
| `tool` | 工具调用开始、完成与失败信息 |
| `chat` | 面向用户的自然语言回复 |
| `result` | 结构化 `ItineraryPlan` |
| `stats` | Token、模型调用次数和工具调用记录 |
| `conversationId` | 当前对话 ID |
| `error` | 超时或执行异常 |

前端没有使用浏览器原生 `EventSource`，而是使用 `fetch + ReadableStream`。原因是请求需要 POST JSON body 和自定义 `token` 请求头，而 `EventSource` 更适合简单 GET。前端手动维护 buffer，按空行切分事件，并支持多行 `data:` 合并，避免网络分片导致半条消息被错误解析。

### 5.8 行程画布与前端状态

生成结果被规范化为 `ItineraryPlan → DayPlan[] → RouteItem[]`。右侧画布提供：

- 按天展示的时间线。
- 景点、餐饮、交通、住宿、休闲和购物类型标记。
- 路线项展开、删除、跨天拖拽与排序。
- 经纬度展示及高德地图跳转。
- 新建行程、已有行程更新及对话—行程双向关联。

`useItinerary` 负责行程规范化、保存、详情加载和 AI 修改结果合并；`useDragDrop` 封装拖拽；`useConversation` 管理消息和思考日志。把页面状态拆到 composable 中，降低了核心 `Workspace.vue` 的业务耦合。

### 5.9 三级记忆与上下文管理

项目将记忆分为三类：

| 类型 | 当前实现 | 价值 | 局限 |
|---|---|---|---|
| 短期记忆 | 从数据库读取最近 10 条消息 | 保持当前对话连续性 | 仅按时间截取，不考虑重要性 |
| 工作记忆 | `ConcurrentHashMap` 保存当前意图和草案 | 访问快、适合当前任务 | 重启丢失，不支持多实例共享 |
| 长期记忆 | 用户健康、历史行程、全局偏好及长期记忆 Repository | 跨对话个性化 | 需要去重、置信度衰减和用户可解释管理 |

`ConversationContextManager` 设计了“滑动窗口 + 摘要压缩”：短对话使用完整上下文，长对话把较早消息总结后与近期原文一起注入。当前代码有一个需要修复的边界问题：摘要阈值是 5 条，而近期窗口是 10 条，6～9 条消息时会产生负数 `skip` 参数并在上层被捕获，导致摘要上下文降级。合理改法是把触发阈值统一为不小于窗口大小，或使用 `Math.max(0, size - MAX_RECENT_MESSAGES)`。

### 5.10 个性化、技能与工具治理

- 全局偏好分为回答风格、饮食、体能、特殊需求和自定义规则，生成和修改阶段都会注入提示词。
- 自定义技能通过 `skillIds` 选择，并由 `SkillService.buildSkillContext` 拼入 Agent 上下文。
- `ToolManagementService` 在启动时注册工具定义，允许用户单独启用或禁用工具。
- 每次工具调用可记录参数、结果摘要、成功状态和调用时间，便于调试与统计。
- 偏好保存时会检测常见 Prompt Injection 关键词，运行时再次净化后注入。该策略是基础防线，但关键词黑名单存在误杀和绕过风险，生产级方案还应使用结构化策略、权限隔离和模型输入分区。

### 5.11 数据与外围业务

核心数据围绕用户、行程、对话、消息、偏好、工具定义和工具调用历史组织。SQL 脚本还包含：

- 实时旅行、轨迹点、轨迹图、旅行报告和旅行总结。
- 社区帖子、评论、点赞、收藏和活动。
- 关注、好友和私信。
- 旅行胶囊及媒体。
- 挑战与战力相关数据。

这些模块让项目不只是一个规划 Demo，而是向“规划前—旅行中—旅行后—社区分享”的旅行平台演进。不过面试介绍应先讲清 Agent 主链路，再把这些作为业务扩展点简要带过。

## 6. 一次完整请求的调用链

```text
1. Workspace 收集自然语言或表单数据
2. POST /api/itinerary/generate/stream
3. LoginInterceptor 校验 token
4. ItineraryServiceImpl 创建/复用 Conversation，并保存用户消息
5. HttpTripPlanningAiClient 分类意图并结构化旅行需求
6. 初始化 MCP；失败则进入内置知识库模式
7. PLAN 生成执行策略
8. EXECUTE 中 LLM 与工具多轮交互，输出 final_output JSON
9. 解析、修复并规范化 ItineraryPlan
10. VERIFY 审核；发现问题则自动反馈修正
11. 更新工作/长期记忆，生成自然语言总结
12. 通过 SSE 返回 result、stats、chat 和 conversationId
13. 前端更新消息区和右侧画布
14. 用户保存后，行程与对话建立关联并写入 MySQL
```

## 7. 项目的核心亮点

1. **从文本生成升级为可执行 Agent**：模型能自主决定何时调用什么工具，并根据观察继续推理。
2. **事实工具与生成模型结合**：地图服务提供实时依据，LLM 负责理解、规划和组织。
3. **生成与修改采用不同路径**：新建强调完整性，修改强调低延迟与保留已有内容。
4. **把不稳定性当作系统问题处理**：包含重试、循环检测、输出修复、协议回退和离线降级。
5. **端到端可观测**：SSE 展示阶段与工具进度，同时记录 Token 与工具调用历史。
6. **有状态与个性化**：对话、实体、操作历史、三级记忆、全局偏好和技能共同构成上下文。
7. **产品闭环完整**：用户可以生成、对话修改、拖拽编辑、保存、恢复和继续优化行程。

## 8. 需要诚实说明的边界与改进方向

面试中主动说明边界通常比过度包装更有说服力：

- Agent Loop 缺少统一的 `maxTurns` 和成本预算；应增加轮数、Token、耗时、工具调用数四维熔断。
- `CompletableFuture.runAsync` 默认使用公共线程池，长时间 AI 请求可能挤占线程；应改为专用有界线程池，并实现背压、取消和隔离。
- 客户端断开后，当前异步任务不一定立即停止；应保存 Future 或使用可取消任务，将断连信号向 Agent 与工具调用传播。
- SSE 仅靠连接维持，缺少心跳、事件 ID 和断线续传；可加入 keep-alive、`Last-Event-ID` 和幂等请求 ID。
- Verify 仍由 LLM 完成，适合软质量判断；天数、坐标范围、预算非负、每日项目数量等应增加确定性校验器。
- 工作记忆是单机内存，横向扩容时会不一致；可迁移到 Redis，并设置 TTL。
- 上下文窗口的 5/10 条阈值存在边界缺陷，应统一阈值并增加单元测试。
- `token` 自定义 Header 可以工作，但标准 `Authorization: Bearer` 更利于网关和生态集成。
- CORS 当前较宽松，生产环境应配置明确 Origin；自定义 Prompt Injection 黑名单也应替换为更稳健的结构化隔离策略。
- 没有可复现的 Agent 质量基准。应建立固定旅行请求集，衡量地点真实性、约束满足率、工具成功率、平均轮数、P95 延迟、Token 成本和人工满意度。

## 9. 面试问题与参考回答

以下回答应结合你本人实际承担的工作进行调整，不要把未参与的模块说成个人独立完成。

### Q1：请用一分钟介绍这个项目。

**参考回答：**

这是一个基于大语言模型和 MCP 的旅行规划 Agent。用户可以用自然语言描述目的地、天数、预算和兴趣，系统会先做意图识别和需求结构化，再经过 Plan、Execute、Verify 三个阶段完成行程。Execute 阶段采用 ReAct-like 循环，模型可以自主调用高德地图的天气、POI 和路线工具，并把结果反馈给下一轮推理。最终结果是结构化的逐日行程，通过 SSE 实时推送到 Vue 前端，用户还能在画布上拖拽编辑，并通过多轮对话继续修改。项目的重点是 Agent 工作流、工具调用、上下文记忆以及对大模型不稳定输出的工程化治理。

### Q2：为什么不直接让 LLM 一次生成完整行程？

**参考回答：**

一次生成虽然简单，但模型容易编造地点、坐标和路线，而且很难知道自己遗漏了哪些约束。拆成 Plan、Execute、Verify 后，Plan 负责全局任务分解，Execute 用外部工具补充事实，Verify 再检查完整性与合理性。代价是调用次数、延迟和成本更高，所以修改已有行程时采用更短的 Refine 路径，而不是每次都重跑全部阶段。

### Q3：你的 Agent Loop 是怎样工作的？

**参考回答：**

后端把系统 Prompt、用户需求和工具 Schema 发送给模型。如果模型返回 `tool_calls`，后端逐个执行 MCP 或内置工具，把结果作为 `role=tool` 消息追加到上下文，再进入下一轮。如果模型认为信息已足够，就用 `final_output` 包裹完整 JSON。后端解析并规范化为 `ItineraryPlan` 后退出循环。这种方式让模型决定行动，但执行权限、重试、循环检测和结果校验仍由应用控制。

### Q4：如何防止 Agent 陷入死循环？

**参考回答：**

当前实现有两层检测。第一层检测同一工具与完全相同参数的重复调用，重复计数达到默认阈值 3 时终止；第二层对同一工具连续调用设置默认 25 次硬上限，同时维护最近参数集合，区分批量查询和参数循环。外层还有 Execute 重试和 600 秒 SSE 超时。现有不足是没有统一总轮数与 Token 预算，我会补充 `maxTurns`、最大工具调用数和成本熔断。

### Q5：为什么使用 MCP，而不是直接封装高德 REST API？

**参考回答：**

直接 REST 封装适合固定业务，而 MCP 把工具发现、参数 Schema 和调用方式标准化。Agent 可以通过 `tools/list` 动态获得能力，再通过 `tools/call` 调用，因此新增工具时主循环不需要增加大量业务分支。项目中仍有直接高德 API 的业务服务，但 Agent 工具链使用 MCP，更适合可扩展的模型工具生态。

### Q6：MCP 客户端在这个项目中如何实现？

**参考回答：**

客户端先连接 MCP 的 SSE 地址，读取服务端返回的 POST endpoint；随后用 JSON-RPC 2.0 发送 `initialize`，再发送 initialized 通知。工具发现使用 `tools/list`，工具执行使用 `tools/call`。每个请求用递增 ID 和 `CompletableFuture` 关联异步 SSE 响应，单次 RPC 默认等待 30 秒。工具描述和 inputSchema 会被转换成模型 function calling 所需的结构。

### Q7：地图服务不可用怎么办？

**参考回答：**

MCP 初始化失败时，系统进入离线知识库模式，仍可生成基础行程；对天气、精确路线和地图链接会返回明确的不可用说明，不伪装成实时结果。这样保证了可用性，但牺牲了实时准确性。产品上应该给用户展示“已降级”状态，并允许稍后重新验证。

### Q8：怎样处理模型输出的 JSON 不合法或被截断？

**参考回答：**

解析链路先提取 `final_output`，剥离 Markdown，再用字符串状态机寻找不在引号内的完整花括号范围。之后修正常见 JSON 问题，并尝试补齐未闭合的对象和数组；还兼容 `data.itinerary` 等包装结构。如果模型返回 `finish_reason=length`，会增大输出上限重试；普通解析失败还会切换为流式生成。长期来看，更理想的是采用模型原生结构化输出或 JSON Schema 校验。

### Q9：Verify 阶段能完全解决模型幻觉吗？

**参考回答：**

不能。Verify 使用独立审核 Prompt，可以发现明显的完整性和合理性问题，但审核模型本身仍可能犯错。更稳健的设计应该把规则拆成两类：天数、坐标范围、预算、字段完整性等用确定性代码校验；景点搭配和节奏合理性等主观问题再交给 LLM。对关键地点还可以保存工具返回的证据 ID，实现可追溯验证。

### Q10：为什么 SSE 使用 `fetch`，而不是 `EventSource` 或 WebSocket？

**参考回答：**

这个场景主要是服务端单向持续推送，而且请求需要 POST body 和认证 Header。原生 `EventSource` 不方便发送 POST 和自定义 Header，因此使用 `fetch + ReadableStream` 手动解析 SSE。WebSocket 可以双向通信，但连接管理和协议复杂度更高，当前用户输入仍是一次请求、服务端持续返回事件，SSE 更轻量。

### Q11：SSE 如何处理网络分片和多行数据？

**参考回答：**

前端每次读取二进制 chunk 后用 `TextDecoder` 增量解码，并把未完成部分保存在 buffer 中。按换行拆分后，识别 `event:` 与 `data:`，多行 data 会用换行拼接，遇到空行才认为一个事件结束。这样不会假设一个网络 chunk 就对应一个完整事件。

### Q12：多轮对话的上下文怎样控制 Token 成本？

**参考回答：**

短对话直接使用完整消息；较长对话保留近期原文，把早期内容压缩成摘要，摘要重点保留目的地、天数、预算、偏好和已确认决策。模型调用还会截断过长的工具结果。当前实现的摘要阈值与窗口大小不一致，6 到 9 条消息存在边界问题，我会先修复索引，再增加 Token 级预算，而不是只按消息条数截断。

### Q13：三级记忆分别解决什么问题？

**参考回答：**

短期记忆解决当前对话连贯性；工作记忆保存当前意图和草案，避免每轮都从文本重建状态；长期记忆从用户健康、历史行程和显式偏好中构建用户画像，用于跨对话个性化。三者生命周期不同，所以存储方式也不同。当前工作记忆在本机内存中，生产环境会迁移到 Redis，并设置过期和版本控制。

### Q14：为什么生成行程和修改行程使用不同链路？

**参考回答：**

新建行程需要从零覆盖所有天数和约束，适合完整的 Plan–Execute–Verify；修改通常只影响局部，如果全部重跑容易改变用户没有要求修改的内容，也增加延迟和成本。因此修改先分类，再把当前完整行程和结构化修改指令交给 Refine Agent，只调用必要工具并返回更新后的完整计划。前端还有合并逻辑，尽量保留未变化内容。

### Q15：如何实现个性化，又怎样防止 Prompt Injection？

**参考回答：**

个性化来源包括显式全局偏好、健康信息、历史行程和自定义技能。偏好入库前检查长度和常见注入模式，注入模型前再做一次净化。这个黑名单方案只能作为基础防线，生产级方案应把系统指令、用户数据和工具权限严格分层，偏好使用结构化枚举而不是任意文本，并对自定义规则设置能力白名单。

### Q16：这个系统并发量上来后，首先会出现什么问题？

**参考回答：**

首先是长连接和外部 AI 调用占用资源。当前 Controller 使用 `CompletableFuture.runAsync`，默认公共线程池不适合大量数分钟任务；工作记忆又是单机 Map，多实例间不共享。我会使用独立有界执行器、任务队列和限流，把状态迁移到 Redis，并为模型与工具调用加入超时、隔离、熔断和取消。同时对 SSE 连接数、队列深度、P95 延迟和 Token 成本做监控。

### Q17：如何测试一个非确定性的 Agent？

**参考回答：**

测试分层进行。普通 Service、Repository 和 Controller 使用确定性单元/功能测试；模型客户端和 MCP 用 Mock 固定返回，验证工具循环、重试、JSON 修复和状态变化；再维护一组离线评测数据，检查约束满足率、地点真实性、天数一致性和平均工具轮数；最后少量在线端到端测试用于发现模型版本变化。不能只断言整段文本完全相等，而应验证结构、约束和关键事实。

### Q18：项目目前最大的技术债是什么？

**参考回答：**

我认为有三项。第一，Agent 主类职责过多，包含 Prompt、MCP、循环、解析、意图和回复生成，应该拆成 Planner、Executor、Verifier、ToolGateway 和 OutputParser。第二，缺少统一的运行预算和可取消任务。第三，评价体系还不完整，目前有调用统计，但缺少可复现的质量基线。优先解决这三项会直接提升可维护性、成本可控性和迭代可信度。

### Q19：为什么没有直接使用 LangChain 或 LangGraph？

**参考回答：**

自行实现的好处是能完整理解 Agent Loop、MCP 协议、消息格式和错误处理，也方便针对业务做轻量定制；缺点是核心类容易膨胀，状态持久化、可观测和恢复机制需要自己建设。如果工作流继续复杂化，我会评估用 LangGraph 一类状态图框架表达节点和转移，但工具网关、领域模型和确定性校验仍保留为独立业务组件，避免被框架绑定。

### Q20：你会用哪些指标评价项目是否真的有效？

**参考回答：**

质量指标包括地点真实性、硬约束满足率、天数一致率、人工修改次数和用户采纳率；效率指标包括首事件延迟、总生成时间、平均 Agent 轮数、工具成功率、Token 与单次请求成本；稳定性指标包括超时率、JSON 修复率、降级率和断线率。当前项目已经记录 Token、模型调用和工具调用，但还需要统一埋点、固定评测集和版本对比看板。

## 10. 面试表达中的高风险口径

以下说法容易被代码追问击穿，建议使用右侧的准确表述：

| 不建议直接说 | 更准确的表达 |
|---|---|
| “Agent 最多循环 15 轮” | 当前没有统一总轮数上限，有重复工具检测、阶段重试和 600 秒超时；下一步应补充 `maxTurns` |
| “项目有 50 张表” | 当前 SQL 初始化脚本有 26 张表，代码侧有 31 个 PO，模型仍在演进 |
| “所有地点都经过实时地图验证” | MCP 在线时优先用高德工具验证；不可用时会明确降级到内置知识库 |
| “Verify 保证结果完全正确” | Verify 是独立审核阶段，可降低错误率，但仍需确定性规则和证据校验 |
| “分类器 max_tokens 是 10” | 当前代码设置为 20，temperature 为 0 |
| “上下文压缩已经完全解决长对话” | 已实现滑动窗口与摘要，但 5/10 条阈值存在边界缺陷，且还需要 Token 级预算 |
| “工作记忆支持分布式” | 当前是本机 `ConcurrentHashMap`，生产化需要 Redis 或持久化状态存储 |

## 11. Two-minute English Introduction

Hello, I’d like to introduce IntelliTravel Pioneer, an AI travel-planning agent built with Java 21, Spring Boot, Vue 3, TypeScript, MySQL, and the Model Context Protocol.

The problem we wanted to solve is that traditional trip planning is fragmented. Users have to search for attractions, check the weather, compare routes, estimate budgets, and then manually organize everything into a schedule. A single LLM response is not reliable enough either, because it may invent places or produce an unreasonable itinerary.

Our solution is a tool-using agent with a Plan, Execute, and Verify workflow. First, the system classifies the user’s intent and converts natural-language requirements into structured fields such as destination, number of days, budget, and interests. In the planning phase, the model creates an execution strategy. In the execution phase, a ReAct-like loop allows the model to call Amap tools through MCP for weather, POI search, geocoding, and route planning. Tool results are added back into the conversation until the model produces a structured itinerary. Finally, a separate verification phase checks completeness and reasonableness and can ask the model to correct the plan.

The system also supports multi-turn itinerary editing. It distinguishes casual questions from real modification requests, preserves the existing plan, and only calls tools when new information needs to be verified. On the frontend, we use POST-based SSE with Fetch and ReadableStream to display phases and tool progress in real time. The result is shown on a draggable day-by-day itinerary canvas.

From an engineering perspective, the most interesting parts are reliability and state management. We implemented tool-loop detection, retries, offline fallback, truncated-JSON repair, conversation summarization, working memory, long-term user preferences, and per-user tool controls. If I continued improving the project, I would add a strict turn and cost budget, deterministic itinerary validators, Redis-based distributed state, and a reproducible evaluation benchmark.

## 12. 关键代码索引

- Agent 主流程与 MCP 客户端：`intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/service/ai/HttpTripPlanningAiClient.java`
- 行程业务编排：`intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/service/serviceImpl/ItineraryServiceImpl.java`
- SSE 接口：`intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/controller/ItineraryController.java`
- 意图状态机：`intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/service/IntentStateMachine.java`
- 三级记忆：`intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/service/MemoryManager.java`
- 上下文压缩：`intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/service/ConversationContextManager.java`
- 工具治理：`intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/service/ToolManagementService.java`
- 全局偏好安全：`intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/service/serviceImpl/UserGlobalPreferenceServiceImpl.java`
- 前端 SSE：`planclient/src/api/itinerary.ts`
- 前端行程逻辑：`planclient/src/composables/useItinerary.ts`
- 行程画布：`planclient/src/views/workspace/ItineraryCanvas.vue`

