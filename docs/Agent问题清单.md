# IntelliTravel Pioneer — Agent 问题清单

> **项目名称**: IntelliTravel Pioneer — AI 旅行规划 Agent  
> **用途**: 记录迭代二测试与迭代三开发中发现的所有问题，作为优化工作的依据和评估基线  
> **最后更新**: 2026-06-14

---

## 一、Agent 架构问题

### P1：小模型指令跟随偏差 → Thought-only 误判崩溃

| 属性 | 内容 |
|------|------|
| **严重程度** | 🔴 高 |
| **发现方式** | 测试 Bad Case |
| **涉及文件** | `HttpTripPlanningAiClient.java` — `runAgentLoop` 方法 |

**问题描述**：

小模型（Qwen3-8B）在工具调用失败后仅输出 `Thought: ...` 纯文本，既没有 `tool_calls` 也没有 `final_output` 标记。`runAgentLoop` 的 else 分支将其误判为"最终输出"，调用 `parseItinerary()` 对纯文本执行 JSON 解析而崩溃。

---

### P2：max_tokens=8192 硬截断 + 括号修复逻辑 Bug

| 属性 | 内容 |
|------|------|
| **严重程度** | 🔴 高 |
| **发现方式** | 测试 Bad Case |
| **涉及文件** | `HttpTripPlanningAiClient.java` — `tryBracketRepair` / `callLlmChat` 方法 |

**问题描述**：

多天行程（3-5 天）的 JSON 体积较大，超出 `max_tokens=8192` 限制后 `finish_reason=length`。原有的 `tryBracketRepair` 括号修复逻辑存在 bug——先补 `]` 再补 `}`，而非按栈逆序闭合。例如 `{"items":[{"name":"..."` 开括号顺序为 `{ [ {`，正确闭合应为逆序 `} ] }`，旧代码先补 `]` 导致数组提前关闭，JSON 结构彻底错乱。

**错误逻辑**：
```
int braceOpen = 0, bracketOpen = 0;
for (int i = 0; i < bracketOpen; i++) sb.append(']');  // 先补 ]
for (int i = 0; i < braceOpen; i++) sb.append('}');     // 再补 }
```

---

### P3：行程天数不一致 — 请求 2 天却生成 8 天

| 属性 | 内容 |
|------|------|
| **严重程度** | 🟡 中 |
| **发现方式** | 测试 Bad Case |
| **涉及文件** | `HttpTripPlanningAiClient.java` — Prompt 模板 + 后置逻辑 |

**问题描述**：

用户请求 2 天行程，LLM 返回 8 天，且 Day 1、Day 2 循环重复出现。

**根因**：
1. `EXECUTE_SYSTEM_PROMPT_PREFIX` 的示例 JSON 写死 `"totalDays":3`，且没有明确指令要求天数与用户请求一致
2. `parseItinerary` 成功后没有任何后置校验——不管 LLM 返回 2 天还是 8 天直接接受
3. Qwen3-8B 小模型对精确约束的遵循能力有限

---

### P4：JSON 解析失败直接抛异常，无兜底路径

| 属性 | 内容 |
|------|------|
| **严重程度** | 🟡 中 |
| **发现方式** | 代码审查 |
| **涉及文件** | `HttpTripPlanningAiClient.java` — `runAgentLoop` else 分支 |

**问题描述**：

`runAgentLoop` 中 `parseItinerary()` 失败直接抛异常，导致整个 Agent 流程中断。没有重试或流式回退机制，用户看到无意义的"AI 生成的排版有点小偏差"错误。

---

### P5：Agent 循环无安全上限，可能无限消耗 Token

| 属性 | 内容 |
|------|------|
| **严重程度** | 🟡 中 |
| **发现方式** | 代码审查 |
| **涉及文件** | `HttpTripPlanningAiClient.java` — `runAgentLoop` 方法 |

**问题描述**：

此前移除 `maxTurns=10` 硬限制后，Agent 缺少兜底保护。一旦 LLM 陷入持续工具调用循环，没有任何机制中断，直接耗尽 Token 配额。

---

### P5a：Agent Loop 429 死循环（附属问题）

| 属性 | 内容 |
|------|------|
| **严重程度** | 🔴 高 |
| **发现方式** | 生产环境日志 |
| **涉及文件** | `HttpTripPlanningAiClient.java` — `runAgentLoop` 方法 |

**问题描述**：

LLM（Qwen）在修改行程时频繁陷入死循环——连续调用同一工具数十次（如 `maps_search_detail` 连调 19 次），最终触发 DeepSeek API 429 限流：

```
Agent turn 8:  Invoking tool: maps_search_detail
Agent turn 9:  Invoking tool: maps_search_detail
...
Agent turn 26: Invoking tool: maps_search_detail
→ 429 TOO_MANY_REQUESTS
```

用户得到无意义的报错信息，服务稳定性严重受损。

---

### P5b：Qwen XML 格式工具调用不兼容

| 属性 | 内容 |
|------|------|
| **严重程度** | 🔴 高 |
| **发现方式** | 测试 Bad Case |
| **涉及文件** | `HttpTripPlanningAiClient.java` — 工具调用解析逻辑 |

**问题描述**：

Qwen 有时不按 OpenAI 标准 `tool_calls` JSON 数组返回工具调用，而是把工具调用写成 XML/DSML 格式嵌入 `content` 字段：

```
<invoke name="maps_text_search">
<parameter name="keywords" string="true">南京新街口汉庭酒店</parameter>
...
```

Agent 检测不到标准 tool_calls → 把 XML 文本当最终输出 → JSON 解析器遇到非 `{` 开头 → 崩溃。

---

### P5c：聊天咨询被误判为行程修改

| 属性 | 内容 |
|------|------|
| **严重程度** | 🟡 中 |
| **发现方式** | 用户反馈 |
| **涉及文件** | `ItineraryServiceImpl.java`、`HttpTripPlanningAiClient.java` |

**问题描述**：

用户输入"南京还有什么特色小吃推荐吗"或"苏州博物馆有什么稀有展品吗"，Agent 却执行了完整的行程修改流程（调用工具、修改行程 JSON），用户只想聊天咨询。

**迭代过程**：

| 阶段 | 方案 | 为何失败 |
|------|------|---------|
| v1 | 正则意图识别 `IntentStateMachine` | "推荐好吃的"被误判为修改意图 |
| v2 | AI 意图分类 `quickClassifyChatOrModify`（LLM 调用） | 不稳定，有网络延迟，模型理解偏差 |

---

### P5d：用户消息被自动追加冗余字段

| 属性 | 内容 |
|------|------|
| **严重程度** | 🟢 低 |
| **发现方式** | 代码审查 |
| **涉及文件** | `HttpTripPlanningAiClient.java` — `buildNiceUserMessage()` |

**问题描述**：

修改行程时，系统在用户输入后自动追加 `天数：X天\n预算：X` 等结构化字段。这些是系统自动添加的，用户没有输入，且修改流程中完整行程 JSON 已包含这些元数据，完全是冗余信息。

```
旧格式：
请帮我修改行程方案：
推荐苏州菜
天数：2天       ← 系统强加
预算：medium    ← 系统强加
```

---

### P5e：缺少纯聊天功能入口

| 属性 | 内容 |
|------|------|
| **严重程度** | 🟡 中 |
| **发现方式** | 功能缺失 |
| **涉及文件** | 新增 `AiChatController.java`、`AiChatServiceImpl.java` 等 5 个文件 |

**问题描述**：

行程编辑页面只有"AI完善"功能（修改行程），用户无法进行普通对话咨询（如了解目的地信息、旅行建议等）。旅行咨询与行程修改耦合在一起，体验割裂。

---

## 二、上下文工程问题

### P6：长对话全量传入 LLM，Token 线性增长

| 属性 | 内容 |
|------|------|
| **严重程度** | 🟡 中 |
| **发现方式** | 代码审查 |
| **涉及文件** | `ConversationContextManager.java` |

**问题描述**：

多轮对话中所有历史消息全量传入 LLM，Token 消耗随对话轮次线性增长，易超出上下文窗口上限（DeepSeek 上下文约 128K token），长对话场景下不可持续。

---

### P7：跨轮次已确认信息丢失，Agent 重复询问

| 属性 | 内容 |
|------|------|
| **严重程度** | 🟡 中 |
| **发现方式** | 用户反馈 |
| **涉及文件** | 无明显实体追踪机制 |

**问题描述**：

跨轮次对话中用户已确认的信息（目的地、天数、预算等）丢失，Agent 在后续轮次中重复询问。例如：
- 用户第 1 轮说了"去北京 3 天"
- Agent 第 5 轮又问"您想去哪里？需要几天？"

---

### P8：用户偏好无法跨对话复用

| 属性 | 内容 |
|------|------|
| **严重程度** | 🟡 中 |
| **发现方式** | 功能缺失 |
| **涉及文件** | `MemoryManager.java`、`UserLongTermMemoryPO` |

**问题描述**：

每次新对话从零开始，用户的历史偏好（常去目的地、预算习惯、兴趣类型）无法被 Agent 感知。用户每次都需要重新表达自己的偏好。

---

### P9：用户输入与历史信息矛盾时无检测

| 属性 | 内容 |
|------|------|
| **严重程度** | 🟢 低 |
| **发现方式** | 代码审查 |
| **涉及文件** | `EntityTracker.java` |

**问题描述**：

用户新输入与历史信息矛盾时无法主动检测和提示。例如：
- 先说要 3 天，后来说 2 天
- 先要去北京，后来说去上海
- 经济型预算却要求五星级酒店

Agent 无法识别这些冲突，可能导致混乱的行程输出。

---

### P9a：AI 对话退出重进后丢失

| 属性 | 内容 |
|------|------|
| **严重程度** | 🔴 高 |
| **发现方式** | 用户反馈 |
| **涉及文件** | `ItineraryServiceImpl.java`、`BattleCustomizePlan.vue` |

**问题描述**：

用户反馈 AI 对话面板退出重进后，之前和 AI 的对话内容就丢失或变成其他内容。

**根因**：
1. `chatConversationId` 是 Vue 组件内存变量，刷新归 `null`
2. 聊天回复只通过 SSE 推送给前端一次，**没有保存到数据库**
3. 前端从不从数据库加载历史对话

---

## 三、RAG 知识增强问题

### P10：MCP 服务不可用时 Agent 完全不可用

| 属性 | 内容 |
|------|------|
| **严重程度** | 🔴 高 |
| **发现方式** | 生产环境排查 |
| **涉及文件** | `HttpTripPlanningAiClient.java` — Phase 0 MCP 初始化 |

**问题描述**：

高德地图 MCP 服务不可用时（网络故障 / API Key 过期 / 连接超时），Agent 完全无法生成行程。没有任何离线降级方案，MCP 依赖成为单点故障。

---

### P11：目的地信息完全依赖实时 API

| 属性 | 内容 |
|------|------|
| **严重程度** | 🟡 中 |
| **发现方式** | 代码审查 |
| **涉及文件** | `HttpTripPlanningAiClient.java` — 工具调用逻辑 |

**问题描述**：

行程中的目的地基础信息（景点、美食、最佳季节）完全依赖高德 MCP API 实时查询，无本地知识储备。网络不稳定时，Agent 对目的地的了解就完全中断。

---

### P12：LLM 摘要失败时无降级方案

| 属性 | 内容 |
|------|------|
| **严重程度** | 🟢 低 |
| **发现方式** | 代码审查 |
| **涉及文件** | `ConversationContextManager.java` — `callLightweightLlmForSummary` |

**问题描述**：

对话历史摘要依赖 LLM（`TripSummaryAiClient`），如果 LLM 摘要调用失败，没有降级策略，历史信息直接丢失。

---

## 四、工具链问题

### P13：工具调用失败直接崩溃，无重试

| 属性 | 内容 |
|------|------|
| **严重程度** | 🔴 高 |
| **发现方式** | 测试 |
| **涉及文件** | `HttpTripPlanningAiClient.java` — `runAgentLoop` 工具调用分支 |

**问题描述**：

工具调用（高德地图 API、自定义工具）失败时直接抛异常或返回错误给 Agent，Agent 收到错误后可能做出不可预料的响应。没有任何自动重试机制，网络抖动即可导致行程生成失败。

---

### P14：工具返回结果过大占用过多 Token

| 属性 | 内容 |
|------|------|
| **严重程度** | 🟡 中 |
| **发现方式** | 代码审查 |
| **涉及文件** | `HttpTripPlanningAiClient.java` — `runAgentLoop` |

**问题描述**：

部分工具（如 `maps_text_search` 搜索热门景点）返回结果动辄数千字符，全部放入下一轮 LLM 调用的上下文，严重挤占推理空间，影响后续工具调用和行程生成的质量。

---

### P15：用户无法控制可用的工具

| 属性 | 内容 |
|------|------|
| **严重程度** | 🟢 低 |
| **发现方式** | 功能缺失 |
| **涉及文件** | `ToolManagementService.java` |

**问题描述**：

所有对话中 Agent 可用的工具是固定的，用户无法根据自己的需求启用/禁用特定工具。例如用户不想使用导航类工具时，Agent 仍会尝试调用。

---

### P16：前端工具映射散落重复

| 属性 | 内容 |
|------|------|
| **严重程度** | 🟢 低 |
| **发现方式** | 代码审查 |
| **涉及文件** | `ToolCallCard.vue`、`ItineraryCanvas.vue`、`Workspace.vue` 等 |

**问题描述**：

工具名称到中文显示名、Emoji 图标、日志消息的映射关系在 `ToolCallCard.vue`、`ItineraryCanvas.vue`、`Workspace.vue` 等多个组件中分散重复定义，新增工具时需要修改多处，维护负担重且容易出现不一致。

---

### P16a：Skill 缺少捆绑资源支持

| 属性 | 内容 |
|------|------|
| **严重程度** | 🟡 中 |
| **发现方式** | 规范对齐 |
| **涉及文件** | `SkillPO.java`、`SkillServiceImpl.java`、`SkillManage.vue` |

**问题描述**：

Skill 模块最初只有 `name`/`description`/`prompt`，缺少 Anthropic Skills 规范中的**捆绑资源**支持（参考资料 `references` + 可执行脚本 `scripts`）。无法为 Agent 提供结构化的领域知识文档和可执行工具脚本。

---

## 五、问题统计

### 按严重程度

| 等级 | 数量 | 编号 |
|------|------|------|
| 🔴 高 | 7 | P1, P2, P5a, P5b, P9a, P10, P13 |
| 🟡 中 | 12 | P3, P4, P5, P5c, P5e, P6, P7, P8, P11, P14, P16a |
| 🟢 低 | 5 | P5d, P9, P12, P15, P16 |

### 按发现方式

| 方式 | 数量 | 编号 |
|------|------|------|
| 测试 Bad Case | 6 | P1, P2, P3, P5b |
| 代码审查 | 9 | P4, P5, P5d, P6, P9, P11, P12, P14, P16 |
| 用户反馈 | 3 | P5c, P7, P9a |
| 生产环境排查 | 1 | P5a, P10 |
| 功能缺失 | 3 | P5e, P8, P15 |
| 规范对齐 | 1 | P16a |

### 按优化方向

| 方向 | 问题数 | 编号 |
|------|--------|------|
| Agent 架构优化 | 10 | P1, P2, P3, P4, P5, P5a, P5b, P5c, P5d, P5e |
| 上下文工程 | 5 | P6, P7, P8, P9, P9a |
| RAG 知识增强 | 3 | P10, P11, P12 |
| 工具链重构与扩展 | 4 | P13, P14, P15, P16, P16a |

---

> **问题总数**: 24 项  
> **对应优化文档**: [Agent优化文档.md](./Agent优化文档.md)  
> **最后更新**: 2026-06-14
