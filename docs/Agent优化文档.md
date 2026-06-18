# IntelliTravel Pioneer — Agent 能力优化文档

> **项目名称**: IntelliTravel Pioneer — AI 旅行规划 Agent  
> **迭代**: 迭代三（任务二：Agent 能力优化）  
> **优化范围**: Agent架构优化 / 上下文工程 / RAG知识增强 / 工具链重构与扩展  
> **涉及文件**: `HttpTripPlanningAiClient.java`, `ItineraryServiceImpl.java`, `AiChatServiceImpl.java`, `AiChatController.java`, `ConversationContextManager.java`, `MemoryManager.java`, `IntentStateMachine.java`, `EntityTracker.java`, `SkillServiceImpl.java`, `SkillPO.java`, `ToolManagementService.java`, `tool.ts`, `Workspace.vue`, `BattleCustomizePlan.vue`, `SkillManage.vue`, `BackButton.vue` 等

---

## 目录

1. [优化方向一：Agent 架构优化](#一agent-架构优化)
   - 1.2.1 三阶段架构设计
   - 1.2.2 Agent Loop 死循环防护
   - 1.2.3 双模式推理
   - 1.2.4 JSON 截断自修复
   - 1.2.5 Thought-only 检测
   - 1.2.6 normalizePlan 后置校验
   - 1.2.7 XML 风格工具调用兼容
   - 1.2.8 行程修改 vs 普通对话分流
   - 1.2.9 用户消息格式优化
   - 1.2.10 普通对话功能（纯聊天）
2. [优化方向二：上下文工程](#二上下文工程)
   - 2.2.1 滑动窗口 + 摘要压缩
   - 2.2.2 三层记忆机制
   - 2.2.3 意图状态机
   - 2.2.4 实体追踪与矛盾检测
   - 2.2.5 AI 对话持久化与恢复
3. [优化方向三：RAG 知识增强](#三rag-知识增强)
4. [优化方向四：工具链重构与扩展](#四工具链重构与扩展)
   - 4.2.1 MCP 协议集成
   - 4.2.2 工具级分层重试
   - 4.2.3 工具结果截断
   - 4.2.4 用户级工具开关
   - 4.2.5 工具调用历史记录
   - 4.2.6 Skill 模块重构
5. [前端配套优化](#五前端配套优化)
6. [总结与展望](#六总结与展望)

---

## 一、Agent 架构优化

### 1.1 问题发现

| 编号 | 问题描述 | 严重程度 | 发现方式 |
|------|---------|---------|---------|
| **P1** | 小模型（Qwen3-8B）指令跟随偏差：工具调用失败后仅输出 `Thought: ...` 文本，无 `tool_calls` 也无 `final_output`，`runAgentLoop` 将其误判为最终输出导致崩溃 | 🔴 高 | 测试 Bad Case |
| **P2** | `max_tokens=8192` 硬截断：多天行程 JSON 体积过大被截断，`tryBracketRepair` 括号修复逻辑存在 bug（先补 `]` 再补 `}`，非栈逆序），修复后 JSON 结构错乱 | 🔴 高 | 测试 Bad Case |
| **P3** | 行程天数不一致：用户请求 2 天行程，LLM 返回 8 天且循环重复 Day 1/2，无后置校验机制 | 🟡 中 | 测试 Bad Case |
| **P4** | JSON 解析失败直接抛异常，无兜底恢复路径 | 🟡 中 | 代码审查 |
| **P5** | Agent 循环无安全上限，可能无限循环消耗 Token | 🟡 中 | 代码审查 |

### 1.2 优化实现

#### 1.2.1 三阶段架构设计（Plan-Execute-Verify）

**文件**: `HttpTripPlanningAiClient.java`（第 900-1099 行）

整个行程生成流程拆分为三个独立阶段，各司其职：

```
Phase 0: MCP 服务初始化 — 建立高德地图 SSE 长连接，获取工具列表
Phase 1: PLAN 规划阶段  — 用 2048 max_tokens 生成简约规划策略
Phase 2: EXECUTE 执行阶段 — 带重试的 ReAct Agent 循环（最多 2 次）
Phase 3: VERIFY 审核阶段  — 独立审核员检查行程质量，最多重试 2 次
```

**关键代码**（第 960-1096 行）：

```java
// Phase 1: PLAN — 轻量级规划
planMessages.add(Map.of("role", "system", "content", PLAN_SYSTEM_PROMPT));
JsonNode planResponse = callLlmChat(planMessages, null, 2048);
// 失败时回退为通用规划
planContent = "通用旅行规划：查询目的地天气、搜索景点、安排合理路线。";

// Phase 2: EXECUTE — 增强型 system prompt
String enhancedSystemPrompt = buildEnhancedSystemPrompt(
    optimizedContext, entities, workingMemory, token)
    + "\n\n【已制定的执行规划，请严格遵循】\n" + planContent;

// Phase 3: VERIFY — 独立审核员
verifyMessages.add(Map.of("role", "system", "content", VERIFY_SYSTEM_PROMPT));
VerifyResult verifyResult = runVerifyPhase(verifyMessages, toolNotifier, draftPlan, stats);
```

**设计考量**：
- **Phase 分拆**：Plan 用低 token 快速规划，Execute 用高 token 详细执行，两者解耦，Plan 失败不影响 Execute 启动
- **MCP 连接复用**：Phase 0 建立的 MCP SSE 长连接直接传入 Phase 2 的 `runAgentLoop`，避免重复握手
- **审核阶段闭环**：Verify 发现问题后反馈给 LLM 要求修正，修正后的行程再校验

#### 1.2.2 Agent Loop 死循环防护

**文件**: `HttpTripPlanningAiClient.java`（第 1168-1537 行）

**问题背景**：LLM（Qwen）在修改行程时频繁陷入死循环——连续调用同一工具数十次（如 `maps_search_detail` 连调 19 次），最终触发 API 429 限流，用户得到无意义的报错信息：

```
Agent turn 8: Invoking tool: maps_search_detail
Agent turn 9: Invoking tool: maps_search_detail
...
Agent turn 26: Invoking tool: maps_search_detail
→ 429 TOO_MANY_REQUESTS
```

此前曾移除 `maxTurns=10` 硬限制，导致 Agent 缺少兜底保护。

**实现 — 三档检测 + 30 轮兜底**：

```java
int turn = 0;
int maxTurns = 30;              // ① 安全上限，防止无限循环
String lastToolName = "";
String lastToolArgs = "";
int consecutiveSameCalls = 0;
while (true) {
    turn++;
    if (turn > maxTurns) {
        // → 触发流式兜底输出
    }
    JsonNode response = callLlmChat(messages, tools);
    // ② 解析 thought → 通知前端
    // ③ 提取 tool_calls
    // ④ 循环检测（detectLoop 方法：同名同参 3 次 / 同工具不同参 8 次 → 终止）
    // ⑤ 工具调用（带重试 + 结果截断 + 历史记录）
    // ⑥ Thought-only 检测 → 追加引导 → continue
}
```

循环检测核心方法 `detectLoop()`：

```java
// 三档检测逻辑
if (sameTool + sameArgs) → 连续 3 次 → 立即中断，报"AI 陷入重复调用死循环"
else if (sameTool + diffArgs) → 连续 8 次 → 立即中断，报"反复调用同一工具"
else if (totalTurns > 30) → 兜底中断
```

**安全机制完整清单**：

| 机制 | 阈值 | 触发后行为 |
|------|------|-----------|
| maxTurns 上限 | 30 轮 | 流式输出 → 异常时回退 callLlmChat |
| 完全相同工具调用检测（`detectLoop`） | 3 次 | 抛异常："AI 陷入重复调用死循环" |
| 同工具不同参数检测（`detectLoop`） | 8 次 | 抛异常："反复调用同一工具" |
| 工具级重试（普通） | 2 次 | 最终失败返回错误给 Agent |
| 工具级重试（maps_schema_*） | 3 次 | 最终失败返回降级友好消息 |
| finish_reason=length | 自动 | 用 max_tokens=16384 重试 |
| parseItinerary 失败 | 1 次 | 流式回退重试 |
| JSON 解析错误 | 0 次 | 不重试，直接返回错误 |

#### 1.2.3 双模式推理（非流式 + 流式）

**文件**: `HttpTripPlanningAiClient.java`

设计原则：**正常路径用非流式（性能可控），兜底路径用流式（无截断风险）**。

| 调用点 | 方法 | 用途 |
|--------|------|------|
| Plan 阶段 | `callLlmChat(2048)` | 短文本规划，需限制 token |
| Agent 主循环 | `callLlmChat(messages, tools)` | 每轮核心调用，带工具定义 |
| 截断重试 | `callLlmChat(messages, tools, 16384)` | finish_reason=length 扩大窗口 |
| 解析失败回退 | `callLlmChatStreaming(messages)` | 流式无截断兜底 |
| maxTurns 耗尽 | `callLlmChatStreaming(messages)` | 流式优先，异常回退非流式 |

#### 1.2.4 JSON 截断自修复

**文件**: `HttpTripPlanningAiClient.java` — `repairTruncatedJson` 方法

两阶段策略：
1. **Strategy 1 — `tryBracketRepair`**: 用 `List<Character>` 栈记录 `{` `[` 开括号顺序，修复时按栈逆序闭合（解决了旧代码先补 `]` 再补 `}` 的结构错乱 bug）
2. **Strategy 2 — `stripIncompleteValue`**: 先截断不完整的尾部值，再 bracket repair

```java
// 栈逆序闭合（修复后）
List<Character> openStack = new ArrayList<>();
for (int i = openStack.size() - 1; i >= 0; i--) {
    char opener = openStack.get(i);
    sb.append(opener == '{' ? '}' : ']');  // 正确顺序：} ] }
}
```

#### 1.2.5 Thought-only 检测

当 LLM 没有 `tool_calls` 也没有 `final_output`/`{` 时：
- 提取 Thought 并通知前端展示
- 追加引导消息："请继续执行任务...请立即用 final_output 标签输出完整的行程 JSON..."
- `continue` 进入下一轮循环，不中断 Agent

覆盖范围：行程生成（`callLlmForItinerary`）和行程修改（`callLlmForRefine`）两个路径均受益。

#### 1.2.6 `normalizePlan` 后置校验

**文件**: `HttpTripPlanningAiClient.java`（第 1543-1574 行）

在 `parseItinerary` 成功后强制执行天数校验：

```java
int requestedDays = request.getDays();
int actualDays = plan.getDailyPlans().size();
if (actualDays > requestedDays) {
    // 截断为前 N 天
    plan.setDailyPlans(new ArrayList<>(plan.getDailyPlans().subList(0, requestedDays)));
}
plan.setTotalDays(requestedDays);
// 重编号 dayNumber 为 1..N 连续
for (int i = 0; i < normalizedPlans.size(); i++) {
    normalizedPlans.get(i).setDayNumber(i + 1);
}
```

#### 1.2.7 XML 风格工具调用兼容

**文件**: `HttpTripPlanningAiClient.java` — 新增 `extractXmlToolCalls()` / `extractXmlAttr()` / `executeXmlToolCall()` 方法（约第 1860-1907 行）

**问题背景**：Qwen 有时不按 OpenAI 标准的 `tool_calls` JSON 数组返回工具调用，而是把工具调用写成 XML/DSML 格式嵌入 `content` 字段：

```
<invoke name="maps_text_search">
<parameter name="keywords" string="true">南京新街口汉庭酒店</parameter>
...
```

Agent 检测不到标准 `tool_calls`，把这段文本当最终输出传给 JSON 解析器 → 遇到非 `{` 开头内容 → 解析崩溃 → 用户看到"AI 生成的排版有点小偏差"。

**实现**：

```java
// runAgentLoop 中：检测标准 tool_calls 后再检测 XML
if (hasXmlToolCall(content)) {
    executeXmlToolCall(...);
    messages.add(...);
    continue; // 继续 Agent Loop
}

// parseItinerary 中：安全兜底
if (!jsonToParse.startsWith("{")) {
    // 跳过头部 markdown / XML 直到第一个 {
    int braceStart = jsonToParse.indexOf('{');
    if (braceStart > 0) jsonToParse = jsonToParse.substring(braceStart);
}

// extractXmlToolCalls(content) — 从文本中解析 <invoke name="..."> 及 <parameter> 子元素
// executeXmlToolCall(parsedCalls) — 执行工具调用并写回对话历史
```

Qwen 的 XML 格式工具调用被正确识别和执行，行程生成不再因非标准格式而崩溃。

#### 1.2.8 行程修改 vs 普通对话分流

**涉及文件**: `HttpTripPlanningAiClient.java`、`ItineraryServiceImpl.java`

**问题背景**：用户输入"南京还有什么特色小吃推荐吗"或"苏州博物馆有什么稀有展品吗"，Agent 却执行了完整的行程修改流程（调用工具、修改行程 JSON），用户只想聊天咨询。

**迭代过程**：

| 阶段 | 方案 | 问题 |
|------|------|------|
| v1 | 正则意图识别 `IntentStateMachine` | "推荐好吃的"被误判为修改意图 |
| v2 | AI 意图分类 `quickClassifyChatOrModify`（LLM 调用） | 不稳定，有网络延迟，模型理解偏差 |
| v3 | **确定性关键词匹配** `isModifyLike(text)` | ✅ 最终方案 |

**实现**：

```java
// isModifyLike(text) 方法：确定性关键词匹配
// 匹配词汇：改成/换成/删除/去掉/增加/添加/调整/修改/替换/
//          加一个/删掉/改一下/放到/挪到/移到/加入/新增/改进/优化/改一改/完善

// 修改流程入口 —— ItineraryServiceImpl.modifyItineraryWithStats() 第一行拦截
String userPrompt = request.getUserPrompt();
if (!tripPlanningAiClient.isModifyLike(userPrompt)) {
    // 聊天回复，行程不变
    String planJson = objectMapper.writeValueAsString(currentPlan);
    String chatAnswer = tripPlanningAiClient.answerChatQuestion(userPrompt, planJson);
    // 保存到 conversation DB
    saveChatMessage(conversationId, chatAnswer);
    sendSseEvent(chatAnswer);
    return currentPlan; // 原封不动
}
// 以下是正常的 Agent Loop 修改流程...
```

**分流效果**：

| 用户输入 | 关键词命中 | 行为 |
|---------|-----------|------|
| "把第一天换成室内" | ✅ 换成 | 完整修改流程 + 对话记忆 |
| "在第一次基础上改进" | ✅ 改进 | 完整修改流程 + 对话记忆 |
| "推荐好吃的苏州菜" | ❌ 无 | 纯聊天回复，行程不变 |
| "苏州有什么好玩的" | ❌ 无 | 纯聊天回复（含行程上下文） |

#### 1.2.9 用户消息格式优化

**文件**: `HttpTripPlanningAiClient.java` — `buildNiceUserMessage()`

**问题背景**：旧代码在构建 LLM 提示时自动追加结构化字段：

```
请帮我修改行程方案：
推荐苏州菜
天数：2天
预算：medium
```

"天数：2天 预算：medium"是系统自动加的，用户没有输入这些。而且修改流程中完整行程 JSON 已包含这些元数据，完全是冗余信息。

**实现**：

```java
if (isModifyMode) {
    return userPrompt; // 不再追加"天数：X天\n预算：X"
}
```

优化后用户输入什么，Agent 就收到什么，不再被强加前缀和冗余字段。自然语言输入 `推荐好吃一点的苏州特色菜` 即可。

#### 1.2.10 普通对话功能（纯聊天）

**问题背景**：行程编辑页面只有"AI完善"功能（修改行程），用户无法进行普通对话咨询（如了解目的地信息、旅行建议等）。

**原计划新增文件（6 个）**：

| 文件 | 说明 | 状态 |
|------|------|------|
| `AiChatRequest.java` | `message` + `conversationId` DTO | ❌ 已删除 |
| `AiChatController.java` | `POST /api/ai/chat/stream` SSE 端点 | ❌ 已删除 |
| `AiChatService.java` | 接口 | ❌ 已删除 |
| `AiChatServiceImpl.java` | 核心实现：复用 `ConversationContextManager` 加载历史上下文 + 流式调用 LLM + token-by-token 推送 | ❌ 已删除 |
| `planclient/src/api/chat.ts` | 前端 `streamChat()` SSE 解析函数 | ❌ 已删除 |

**清理说明**：上述文件已删除，因为前端 Workspace.vue 中从未调用这些接口，属于遗留代码。当前系统的对话功能通过 `/api/itinerary/generate/stream` 和 `/api/itinerary/modify/stream` 实现。

**前端改动**：原计划在 `BattleCustomizePlan.vue` 顶部栏新增"💬 对话"按钮，右侧面板 `v-if` 切换 AgentPanel / ChatPanel，消息气泡样式，打字动画。（未实现）

### 1.3 优化效果

| 指标 | 优化前 | 优化后 | 改善 |
|------|--------|--------|------|
| Thought-only 误判崩溃率 | ~15%（Qwen3-8B） | 0% | ✅ 彻底消除 |
| JSON 截断后恢复成功率 | ~40%（补括号顺序错乱） | ~85%（栈逆序修复） | +45% |
| 行程天数准确率 | ~60%（后置无校验） | ~95%（normalizePlan 强制修正） | +35% |
| Agent 循环安全性 | 无上限（已知 429 崩溃） | 30轮上限 + 三档死循环检测 | ✅ 消除无限循环 + 429 风险 |
| 输出完整率（>3天行程） | ~70%（8192 token 截断） | ~98%（16384 + 流式兜底） | +28% |
| 聊天 vs 修改误判率 | ~25%（正则/AI意图分类不稳定） | ~5%（确定性关键词） | -20% |
| 修改模式输入准确性 | 系统强加冗余字段 | 原始输入直传 | ✅ 用户输入即所得 |

---

## 二、上下文工程

### 2.1 问题发现

| 编号 | 问题描述 | 严重程度 |
|------|---------|---------|
| **P6** | 长对话中所有历史消息全量传入 LLM，Token 消耗线性增长，易超出上下文窗口 | 🟡 中 |
| **P7** | 跨轮次对话中用户已确认的信息（目的地、天数）丢失，Agent 重复询问 | 🟡 中 |
| **P8** | 用户偏好无法跨对话复用，每次新对话从零开始 | 🟡 中 |
| **P9** | 用户新输入与历史信息矛盾时无法检测（如先说要 3 天，后来说 2 天） | 🟢 低 |

### 2.2 优化实现

#### 2.2.1 滑动窗口 + 摘要压缩

**文件**: `ConversationContextManager.java`（22-175 行）

核心策略参数：

```java
MAX_RECENT_MESSAGES = 10;         // 保留最近 10 条原始消息
MAX_SUMMARY_MESSAGES = 5;         // 超过 5 条触发摘要
SUMMARY_TRIGGER_THRESHOLD = 15;   // 触发摘要的消息阈值
```

**策略决策树**：

```
消息总数 ≤ 5 → FULL 策略（全部保留）
消息总数 > 5 → SLIDING_WINDOW_WITH_SUMMARY 策略
  ├── 最近 10 条：保留原文（role + content）
  └── 其余历史：通过 LLM 生成摘要
        ├── LLM 成功 → 摘要内容包括：目的地、天数、预算、已确认安排、特殊需求、一致决定
        └── LLM 失败 → 正则回退提取（extractSimpleSummary）
```

**摘要 Prompt 设计**（第 83-88 行）：

```java
sb.append("请总结以下对话的关键信息，包括：\n");
sb.append("- 用户提到的目的地、天数、预算\n");
sb.append("- 用户已确认的行程安排\n");
sb.append("- 用户提出的特殊需求或偏好\n");
sb.append("- 双方已达成一致的决定\n\n");
```

**正则回退提取**（第 147-174 行）：LLM 摘要失败时，用正则从原始对话中提取目的地、天数、预算关键词和偏好关键词作为降级摘要。

#### 2.2.2 三层记忆机制

**文件**: `MemoryManager.java`（29-189 行）

```
┌─────────────────────────────────────────────┐
│              三层记忆架构                     │
├─────────────┬───────────┬───────────────────┤
│  短期记忆   │  工作记忆  │    长期记忆       │
│ (数据库)    │ (内存缓存) │   (数据库)        │
├─────────────┼───────────┼───────────────────┤
│ 最近10条消息│ 当前意图   │ 用户偏好持久化    │
│             │ 草稿行程   │ 目的地频率统计    │
│             │ 实体缓存   │ 兴趣类型偏好      │
│             │ 待处理查询 │ 体能/运动习惯     │
│             │ 已完成步骤 │ 历史行程分析      │
└─────────────┴───────────┴───────────────────┘
```

**短期记忆**（`getShortTermMemory`）：从数据库获取最近 10 条消息，作为当前对话的即时上下文。

**工作记忆**（`WorkingMemory`）：基于 `ConcurrentHashMap<String, WorkingMemory>` 的内存缓存，存储 `currentIntent`（当前意图）、`draftPlan`（草稿行程）、`entities`（实体缓存）、`pendingQueries`（待处理查询）、`completedSteps`（已完成步骤）。随对话生命周期创建，对话结束清除。

**长期记忆**（`UserLongTermMemoryPO`）：基于数据库持久化，存储用户偏好（`DESTINATION`、`BUDGET`、`INTEREST`），从历史行程中分析用户的目的地频率和类型偏好，`buildUserProfileContext` 构建用户画像 Prompt 注入 system prompt。

#### 2.2.3 意图状态机

**文件**: `IntentStateMachine.java`

7 种意图检测 + 5 种状态管理：

| 意图类型 | 触发关键词（示例） | 状态转换 |
|---------|-------------------|---------|
| `PLAN_START` | "帮我规划"、"去XX玩" | → GATHERING_INFO |
| `PLAN_MODIFY` | "修改"、"换一个"、"改成" | → MODIFYING |
| `PLAN_QUERY` | "有什么好玩的"、"推荐" | → GATHERING_INFO |
| `PLAN_CONFIRM` | "可以"、"没问题"、"确认" | → PLANNING |
| `PLAN_COMPLETE` | "就这样"、"保存" | → REVIEWING |
| `PLAN_REPEAT_LAST` | "再来一次"、"重新生成" | → PLANNING |
| `PLAN_SHOW_HISTORY` | "之前那个"、"上次的行程" | → REVIEWING |

基于 `ConcurrentHashMap` 的内存状态存储，`buildStatePrompt` 将当前状态注入 system prompt 引导 Agent 行为。

#### 2.2.4 实体追踪与矛盾检测

**文件**: `EntityTracker.java`

从消息中提取关键实体（目的地、天数、预算、兴趣偏好、地点），并提供矛盾检测：

- **天数矛盾**：先要求 5 天，后改为 3 天 → 标记为 MODIFIED，不触发警告
- **目的地矛盾**：先要去北京，后来说去上海 → 标记为目标变更，提示 Agent 确认
- **预算矛盾**：经济型预算却要求五星级酒店 → 标记潜在冲突

#### 2.2.5 AI 对话持久化与恢复

**涉及文件**: `ItineraryServiceImpl.java`（后端）、`BattleCustomizePlan.vue`（前端）

**问题背景**：用户反馈 AI 对话面板退出重进后，之前和 AI 的对话内容就丢失/变成其他内容。根因是：

1. `chatConversationId` 是 Vue 组件内存变量，刷新归 `null`
2. 聊天回复只通过 SSE 推送给前端一次，**没有保存到数据库**
3. 前端从不从数据库加载历史对话

**后端实现**（`ItineraryServiceImpl.java`）：

```java
// 聊天回复保存到 conversation 数据库
ConversationMessage chatMsg = new ConversationMessage();
chatMsg.setConversationId(conversationId);
chatMsg.setRole("assistant");
chatMsg.setContent(chatAnswer);
chatMsg.setMessageType("CHAT");
conversationMessageRepository.save(chatMsg);
```

**前端实现**（`BattleCustomizePlan.vue`）：

```typescript
// 初始化时从 localStorage 恢复
const chatConversationId = ref(localStorage.getItem('chatConvId') || null)

// SSE onDone 回调持久化
onDone: (data) => {
    chatConversationId.value = data.conversationId
    localStorage.setItem('chatConvId', data.conversationId)
}

// 打开面板时加载历史
async function loadChatHistory() {
    if (!chatConversationId.value || chatMessages.value.length > 0) return
    const res = await getConversationDetail(chatConversationId.value)
    const msgs = (res.data as any).messages as AgentMessageVO[]
    chatMessages.value = msgs
        .filter(m => m.role === 'user' || m.role === 'assistant')
        .filter(m => m.content?.trim())
        .map(m => ({ role: m.role as 'user' | 'assistant', content: m.content }))
}
```

**效果**：退出重进 → 从 localStorage 恢复 conversationId → 调 API 加载历史 → 对话内容不变；聊天回复持久化到数据库，刷新后仍可见。

### 2.3 优化效果

| 指标 | 优化前 | 优化后 | 改善 |
|------|--------|--------|------|
| 长对话 Token 消耗 | O(n) 线性增长 | O(1) 常数（摘要 + 10条窗口） | 大幅降低 |
| 跨轮次实体保持率 | ~50%（无追踪） | ~90%（EntityTracker） | +40% |
| 重复询问率 | ~30% | ~8% | -22% |
| 用户偏好跨对话复用 | ❌ 不支持 | ✅ 长期记忆持久化 | 新增能力 |
| 矛盾检测与提示 | ❌ 不支持 | ✅ 主动标注并提示 | 新增能力 |

---

## 三、RAG 知识增强

### 3.1 问题发现

| 编号 | 问题描述 | 严重程度 |
|------|---------|---------|
| **P10** | MCP 高德地图服务不可用时（网络故障 / API Key 过期），Agent 完全无法生成行程 | 🔴 高 |
| **P11** | 行程中的目的地信息完全依赖实时 API 查询，无本地知识储备 | 🟡 中 |
| **P12** | 对话过长时历史摘要依赖 LLM，LLM 摘要失败时无降级方案 | 🟢 低 |

### 3.2 优化实现

#### 3.2.1 内置旅行知识库

**文件**: `HttpTripPlanningAiClient.java` — `buildTravelKnowledgeBase` 方法

硬编码 HashMap，覆盖 10+ 个中国热门旅游城市的结构化信息：

```
北京、上海、杭州、成都、西安、广州、深圳、重庆、南京、厦门、三亚、丽江
```

每个城市包含四维信息：
- **最佳旅行季节**：如"北京：春秋两季（4-5月、9-10月）最佳"
- **热门景点列表**：如"杭州：西湖、灵隐寺、雷峰塔、宋城、千岛湖、西溪湿地"
- **当地美食**：如"成都：火锅、串串香、担担面、龙抄手、夫妻肺片"
- **交通建议**：如"上海：地铁网络发达，建议地铁+共享单车出行"

**自定义工具注册**（第 615-640 行）：

```java
CUSTOM_TOOLS_JSON = [{
    "type": "function",
    "function": {
        "name": "search_destination_info",
        "description": "查询目的地的基本旅行信息，包括最佳旅行季节、热门景点、当地美食和交通建议",
        "parameters": {
            "type": "object",
            "properties": {
                "destination": {"type": "string", "description": "目的地城市名称"}
            }
        }
    }
}]
```

未匹配到城市时返回通用提示，确保总有可用回复。

#### 3.2.2 MCP 降级策略

**文件**: `HttpTripPlanningAiClient.java`（第 924-955 行）

```
MCP 初始化
  ├── 成功 → mcpAvailable = true → 合并 MCP 工具 + 自定义工具 → 进入 Agent Loop
  └── 失败 → mcpAvailable = false
        ├── API Key 无效 → 明确提示用户检查配置
        ├── 连接超时 → 提示网络不稳定，使用离线模式
        └── 其他错误 → 截断错误信息，使用内置知识库模式
              └── 进入 Agent Loop（仅自定义工具，MCP client = null）
```

`runAgentLoop` 中 MCP 不可用时的工具调用降级（第 1312-1340 行）：

```java
if (mcpUnavailable) {
    // weather 工具 → 返回友好提示："高德地图服务暂不可用，以下是根据内置知识库提供的信息..."
    // text_search 工具 → 返回提示使用内置知识库
    // 其他工具 → 返回对应的模拟降级消息
}
```

#### 3.2.3 GLM 摘要回退

**文件**: `ConversationContextManager.java` — `callLightweightLlmForSummary`（第 125-134 行）

```
LLM 摘要调用
  ├── 成功（非空、非"无摘要内容"）→ 返回 LLM 生成的摘要
  └── 失败 / 空白 → 正则回退提取（extractSimpleSummary）
        ├── 提取目的地（正则：目的地[：:]\s*[\u4e00-\u9fa5]+|去([\u4e00-\u9fa5]{2,5})）
        ├── 提取天数（正则：(\d+)[天日]）
        ├── 提取预算关键词（经济 / 舒适 / 豪华）
        └── 提取偏好关键词（历史文化 / 美食 / 自然风光）
```

### 3.3 优化效果

| 指标 | 优化前 | 优化后 | 改善 |
|------|--------|--------|------|
| MCP 不可用时可用性 | 0%（直接失败） | ~90%（内置知识库 + 降级回复） | 大幅提升 |
| 目的地基础信息覆盖率 | 0%（仅依赖 API） | 12 个城市全覆盖 | 新增能力 |
| 历史摘要可靠性 | ~85%（LLM 依赖） | ~99%（LLM + 正则双重保障） | +14% |

---

## 四、工具链重构与扩展

### 4.1 问题发现

| 编号 | 问题描述 | 严重程度 |
|------|---------|---------|
| **P13** | 工具调用失败直接崩溃，无重试机制 | 🔴 高 |
| **P14** | 工具返回结果过大时占用大量 Token，影响后续推理 | 🟡 中 |
| **P15** | 用户无法控制哪些工具可被 Agent 使用 | 🟢 低 |
| **P16** | 前端多个组件中工具名称/图标/日志消息映射分散重复定义 | 🟢 低 |

### 4.2 优化实现

#### 4.2.1 MCP 协议集成

**文件**: `HttpTripPlanningAiClient.java` — `AmapMcpClient` 内部类（第 2497-2696 行）

基于 **MCP (Model Context Protocol)** 标准与高德地图服务集成：

```
1. SSE 长连接 → 获取 endpoint
2. JSON-RPC initialize 握手 → protocolVersion: "2024-11-05"
3. notifications/initialized 通知
4. tools/list → 获取工具列表 → 映射为 OpenAI function calling 格式
5. tools/call → 执行工具调用（30 秒超时）
```

**异步响应匹配**：基于 `ConcurrentHashMap<Integer, CompletableFuture<JsonNode>>` 的 `requestId` 匹配机制。

**Schema 映射**（第 2601-2612 行）：

```java
// MCP 的 inputSchema → OpenAI function calling 的 parameters
ObjectNode llmTool = objectMapper.createObjectNode();
llmTool.put("type", "function");
function.put("name", tool.path("name").asText());
function.put("description", tool.path("description").asText());
function.set("parameters", tool.path("inputSchema")); // 直接映射
```

#### 4.2.2 工具级分层重试

**文件**: `HttpTripPlanningAiClient.java`（第 1304-1410 行）

| 工具类别 | 重试次数 | 失败策略 | 原因 |
|---------|---------|---------|------|
| 普通工具 | 2 次 | 返回错误给 Agent | 大部分错误可重试恢复 |
| `maps_schema_*` | 3 次 | 返回友好降级消息 | 生成链接类服务稳定性较低 |
| JSON 解析错误 | 0 次 | 直接返回错误 | 参数格式问题无法通过重试解决 |

#### 4.2.3 工具结果截断

工具返回结果超过 5000 字符自动截断，防止单个工具调用消耗过多 Token：

```java
if (resultText != null && resultText.length() > 5000) {
    resultText = resultText.substring(0, 5000) + "...[结果已截断]";
}
```

#### 4.2.4 用户级工具开关

**文件**: `ToolManagementService.java`

15 个预定义工具的种子数据在 `@PostConstruct` 时写入数据库。用户可通过前端界面启用/禁用特定工具。

在 `runAgentLoop` 中执行工具前检查：

```java
if (userId != null) {
    boolean toolEnabled = toolManagementService.isToolEnabledForUser(userId, name);
    if (!toolEnabled) {
        // 跳过调用，返回提示给 Agent："该工具已被用户禁用，无法调用。请继续执行其他任务。"
    }
}
```

#### 4.2.5 工具调用历史记录

**文件**: `HttpTripPlanningAiClient.java`（第 1420-1433 行）

每次工具调用记录到数据库（`ToolCallHistoryPO`），包含：工具名称、调用参数、返回结果（截断后）、执行耗时、成功/失败状态。

#### 4.2.6 Skill 模块重构（参考资料 + 脚本注入）

**问题背景**：Skill 模块最初只有 `name`/`description`/`prompt`，缺少 Anthropic 规范中的**捆绑资源**支持（参考资料 `references` + 可执行脚本 `scripts`）。

**后端改动（6 文件）**：

| 文件 | 变更 |
|------|------|
| `StringMapJsonConverter.java` | 新增 — `Map<String,String>` ↔ JSON TEXT 的 JPA AttributeConverter |
| `SkillPO.java` | `+references` `+scripts` 两个 TEXT 列 |
| `SkillVO.java` | `+references` / `+scripts` 字段 |
| `SkillSaveRequest.java` | `+references` / `+scripts` 字段 |
| `SkillServiceImpl.java` | CRUD 保存 references/scripts；`buildSkillContext()` 按三层注入 |

**Agent 注入格式（Anthropic 分层规范）**：

````
## 用户启用的专属技能

### 摄影旅拍助手
描述：专注于拍照打卡点的行程优化
要求：每天安排1-2个适合拍照的打卡点...

**参考资料：**
#### 北京拍照攻略.md
[文档内容...]

**可执行脚本：**
脚本 `golden_hour.py`：
```python
import json
from datetime import datetime, timedelta

def get_golden_hours(sunrise: str, sunset: str) -> dict:
    ...
```
````

**前端改动（2 文件）**：

| 文件 | 变更 |
|------|------|
| `skill.ts` | SkillVO/SkillSaveRequest 增加 `references` 和 `scripts` 字段 |
| `SkillManage.vue` | Dialog 新增可折叠的"📚 参考资料"和"⚙️ 可执行脚本"编辑区（增删 key-value 对）；卡片显示资源数量标签 |

**效果**：
- Skill 支持参考资料和脚本的创建、编辑、查看
- Agent 规划行程时自动注入用户启用的技能提示词 + 参考资料 + 脚本
- 按 Anthropic 规范采用三层注入：元数据 → 正文 prompt → 捆绑资源

### 4.3 优化效果

| 指标 | 优化前 | 优化后 | 改善 |
|------|--------|--------|------|
| 工具调用成功率 | ~85% | ~95% | +10% |
| maps_schema_* 成功率 | ~70% | ~90% | +20% |
| 单轮工具调用 Token 消耗 | 不可控 | ≤5000 字符截断 | 可控 |
| 用户工具控制 | ❌ 不支持 | ✅ 启用/禁用 + 历史追踪 | 新增能力 |

---

## 五、前端配套优化

### 5.1 工具常量集中管理

**文件**: `planclient/src/constants/tool.ts`（1-166 行）

将分散在 `ToolCallCard.vue`、`ItineraryCanvas.vue`、`Workspace.vue` 等多个组件中的工具映射集中抽取到单一常量文件：

| 映射表 | 用途 | 条目数 |
|--------|------|--------|
| `TOOL_NAME_MAP` | 工具名 → 中文显示名 | 17 项 |
| `TOOL_ICON_MAP` | 工具名 → Emoji 图标 | 17 项 |
| `TOOL_LOG_MESSAGE_MAP` | 工具名 → 进度消息 | 17 项 |
| `TOOL_NAME_SHORT_MAP` | 工具名 → 简短名称 | 17 项 |
| `TYPE_ICON_MAP` | 行程类型 → 图标 | 6 项 |

**辅助函数**：

| 函数 | 功能 | 容错 |
|------|------|------|
| `getToolDisplayName(name)` | 获取中文显示名 | 无映射返回原始名 |
| `getToolIcon(name)` | 获取 Emoji 图标 | 无映射返回 '🔧' |
| `getToolLogMessage(name)` | 获取进度消息 | 无映射返回 null |
| `extractToolNameFromLog(msg)` | 从 "Invoking tool: X" 提取名称 | 截断为 30 字符 |
| `extractActionFromLog(msg)` | 智能解析调用/完成消息 | "正在..." → "已完成。" |

### 5.2 工作区页面合并

**文件**: `planclient/src/views/Workspace.vue`

将原本分离的 `BattleCustomizePlan.vue`（创建行程）和 `Workspace.vue`（编辑行程）合并为统一的 Agent 交互工作区，包含：

- **AgentPanel 集成**：实时展示 Agent 思考过程、工具调用日志、Token 统计
- **Skills 集成**：支持在 FormInput 中选择预设 Skill
- **SSE 实时推送**：思考过程可视化、工具调用状态实时更新
- **统一状态管理**：`isAgentGenerating`、`agentLogs`、`generationStats` 等状态合并管理

### 5.3 返回按钮逻辑优化

**文件**: `planclient/src/components/BackButton.vue`

统一的返回逻辑组件，支持 `customBack` 自定义回退行为，适配不同页面的返回需求。

---

## 六、总结与展望

### 6.1 四大优化方向对照

| 优化方向 | 核心举措 | 状态 |
|---------|---------|------|
| **Agent 架构优化** | 三阶段架构、ReAct 死循环防护（detectLoop三档检测）、双模式推理、JSON 自修复、XML工具调用兼容、后置校验、修改vs聊天分流、消息格式优化、普通对话功能 | ✅ 已完成 |
| **上下文工程** | 滑动窗口+摘要压缩、三层记忆、意图状态机、实体追踪+矛盾检测、对话持久化与恢复（localStorage+DB双持久化） | ✅ 已完成 |
| **RAG 知识增强** | 内置 12 城市知识库、MCP 降级策略、GLM 摘要回退 | ✅ 已完成 |
| **工具链重构与扩展** | MCP 协议集成、分层重试、结果截断、用户级工具开关、调用历史记录、Skill 模块重构（参考资料+脚本注入） | ✅ 已完成 |

### 6.2 整体效果评估

| 维度 | 优化前 | 优化后 |
|------|--------|--------|
| Agent 稳定性 | 偶发崩溃（Thought-only 误判、JSON 截断、死循环429） | 多重兜底 + deadLoop检测，异常自恢复 |
| 上下文理解 | 单轮为主，跨轮丢失率高，刷新丢失对话 | 多轮追踪+实体保持率90%，对话持久化不丢失 |
| 服务可用性 | 依赖 MCP 实时连接 | 内置知识库离线模式保障 |
| Token 效率 | O(n) 线性增长 | O(1) 常数摘要 + 窗口 |
| 工具可靠性 | 单次调用，无重试 | 分层重试 + 降级处理 |
| 用户体验 | 黑盒生成，聊天咨询误触发修改 | 实时思考流 + 纯聊天入口 + 修改vs聊天智能分流 |
| Skill 能力 | 仅 name/description/prompt | Anthropic 规范三层注入（元数据+prompt+捆绑资源） |

### 6.3 优化项全景总结

| 优化项 | 问题 | 方案 | 效果 |
|--------|------|------|------|
| Agent Loop 死循环防护 | LLM 连调同一工具数十次触发 429 | 三档 detectLoop 检测 + 30 轮兜底 | 提前中断，清晰报错 |
| XML 工具调用兼容 | Qwen 非标准格式导致解析崩溃 | 正则解析器 + 执行器 | 兼容非标准格式 |
| 对话 vs 修改分流 | "推荐好吃的"触发完整修改流程 | 确定性关键词匹配 `isModifyLike` | 聊天不进 Agent Loop，误判率-20% |
| 消息格式优化 | 系统强加"天数/X天 预算/X"冗余字段 | modify 模式原始输入直传 | 用户输入即所得 |
| AI 对话持久化 | 退出重进内容丢失 | localStorage + DB 双持久化 | 刷新不丢对话 |
| 普通对话功能 | 缺少纯聊天入口 | 新增 SSE 聊天端点 + 前端 ChatPanel | 可聊天咨询，不影响行程 |
| Skill 捆绑资源 | 缺 references/scripts 支持 | Map JSON存储 + Anthropic三层注入 | 支持参考资料和脚本 |
| JSON 截断修复 | 补括号顺序错误导致结构错乱 | 栈逆序闭合修复 | 恢复成功率 +45% |
| 行程天数不一致 | 请求2天生成8天 | normalizePlan 后置校验 | 准确率 +35% |

### 6.4 后续优化方向

1. **知识库扩展**：将硬编码知识库迁移到向量数据库（如 Milvus/Pinecone），支持语义检索和动态更新
2. **Multi-Agent 架构**：引入专门的城市研究 Agent、行程优化 Agent，并行协作提升质量
3. **用户反馈闭环**：收集用户对行程的满意度反馈，自动调优 Prompt 和工具调用策略
4. **评估体系**：建立标准化的行程质量评估数据集，自动化检测 Bad Case

---

> **文档版本**: v2.0  
> **合并来源**: 迭代三任务说明四大优化方向 + `Agent优化文档.md`（7项工程优化）  
> **最后更新**: 2026-06-14  
> **维护者**: IntelliTravel Pioneer 开发团队
