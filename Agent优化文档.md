# Agent 优化文档

> 记录行程规划 Agent 系统的完整优化过程，涵盖问题发现、具体实现与优化后效果。

---

## 目录

1. [Agent Loop 死循环防护](#1-agent-loop-死循环防护)
2. [LLM 非标准工具调用兼容（XML 格式）](#2-llm-非标准工具调用兼容xml-格式)
3. [行程修改 vs 普通对话分流](#3-行程修改-vs-普通对话分流)
4. [用户消息格式优化](#4-用户消息格式优化)
5. [AI 对话持久化与恢复](#5-ai-对话持久化与恢复)
6. [普通对话功能（纯聊天）](#6-普通对话功能纯聊天)
7. [Skill 模块重构（参考资料 + 脚本注入）](#7-skill-模块重构参考资料--脚本注入)

---

## 1. Agent Loop 死循环防护

### 问题发现

LLM（Qwen）在修改行程时频繁陷入死循环——连续调用同一工具数十次（如 `maps_search_detail` 连调 19 次），最终触发 API 429 限流，用户得到无意义的报错信息。

```
Agent turn 8: Invoking tool: maps_search_detail
Agent turn 9: Invoking tool: maps_search_detail
...
Agent turn 26: Invoking tool: maps_search_detail
→ 429 TOO_MANY_REQUESTS
```

此外，此前移除 maxTurns=10 硬限制后，Agent 缺少兜底保护。

### 优化具体实现

| 文件 | 改动 |
|---|---|
| [HttpTripPlanningAiClient.java](file:///d:/Code/Project/Agent/agent-project/intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/service/ai/HttpTripPlanningAiClient.java) | 新增 `detectLoop()` 方法 + 三档阈值 + 30 轮兜底 |

```java
// 三档检测逻辑
if (sameTool + sameArgs) → 连续 3 次 → 立即中断，报"AI 陷入重复调用死循环"
else if (sameTool + diffArgs) → 连续 8 次 → 立即中断，报"反复调用同一工具"
else if (totalTurns > 30) → 兜底中断

private boolean detectLoop(String currentToolName, String currentToolArgs,
                           String prevToolName, String prevToolArgs,
                           int sameToolCount, int sameToolDifferentArgsCount,
                           int turnNumber) { ... }
```

### 优化后效果

- LLM 跑偏时在触发 429 限流**之前**就被中断，用户得到清晰的中文错误提示
- Agent Loop 不会无限运行，服务稳定性显著提升

---

## 2. LLM 非标准工具调用兼容（XML 格式）

### 问题发现

Qwen 有时不按 OpenAI 标准的 `tool_calls` JSON 数组返回工具调用，而是把工具调用写成 XML/DSML 格式嵌入 `content` 字段：

```
<invoke name="maps_text_search">
<parameter name="keywords" string="true">南京新街口汉庭酒店</parameter>
...
```

Agent 检测不到标准 tool_calls，把这段文本当最终输出传给 JSON 解析器 → 遇到 `#` 开头 → 解析崩溃 → 用户看到"AI 生成的排版有点小偏差"。

### 优化具体实现

| 文件 | 改动 |
|---|---|
| [HttpTripPlanningAiClient.java](file:///d:/Code/Project/Agent/agent-project/intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/service/ai/HttpTripPlanningAiClient.java) | 新增 3 个方法：`extractXmlToolCalls()` / `extractXmlAttr()` / `executeXmlToolCall()` |
| 同上 | `runAgentLoop()` 中新增 XML 检测分支 |
| 同上 | `parseItinerary()` 中新增非 `{` 开头跳过逻辑 |

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
```

`extractXmlToolCalls(content)` 从文本中解析 `<invoke name="...">` 及 `<parameter>` 子元素，`executeXmlToolCall` 执行解析出的工具调用并写回对话历史。

### 优化后效果

- Qwen 的 XML 格式工具调用被正确识别和执行
- 行程生成不再因非标准格式而崩溃

---

## 3. 行程修改 vs 普通对话分流

### 问题发现

用户输入"南京还有什么特色小吃推荐吗"或"苏州博物馆有什么稀有展品吗"，Agent 却执行了完整的行程修改流程（调用工具、修改行程 JSON），用户只想聊天咨询。

**迭代过程：**

| 阶段 | 方案 | 问题 |
|---|---|---|
| v1 | 正则意图识别 `IntentStateMachine` | "推荐好吃的"被误判为修改意图 |
| v2 | AI 意图分类 `quickClassifyChatOrModify`（LLM 调用） | 不稳定，有网络延迟，模型理解偏差 |
| v3 | **确定性关键词匹配** `isModifyLike(text)` | ✅ 最终方案 |

### 优化具体实现

| 文件 | 改动 |
|---|---|
| [HttpTripPlanningAiClient.java](file:///d:/Code/Project/Agent/agent-project/intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/service/ai/HttpTripPlanningAiClient.java) | `isModifyLike(text)` 方法：匹配 `改成/换成/删除/去掉/增加/添加/调整/修改/替换/加一个/删掉/改一下/放到/挪到/移到/加入/新增/改进/优化/改一改/完善` |
| 同上 | `answerChatQuestion(message, planJson)` 方法：独立 LLM 调用生成 200 字内旅行咨询回答，注入当前行程上下文 |
| [ItineraryServiceImpl.java](file:///d:/Code/Project/Agent/agent-project/intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/service/serviceImpl/ItineraryServiceImpl.java) | `modifyItineraryWithStats()` 入口第一行前置拦截 |

```java
// 修改流程入口 —— 第一行拦截
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

### 优化后效果

| 用户输入 | 关键词命中 | 行为 |
|---|---|---|
| "把第一天换成室内" | ✅ 换成 | 完整修改流程 + 对话记忆 |
| "在第一次基础上改进" | ✅ 改进 | 完整修改流程 + 对话记忆 |
| "推荐好吃的苏州菜" | ❌ 无 | 纯聊天回复，行程不变 |
| "苏州有什么好玩的" | ❌ 无 | 纯聊天回复（含行程上下文） |

---

## 4. 用户消息格式优化

### 问题发现

旧代码在构建 LLM 提示时自动追加结构化字段：

```
请帮我修改行程方案：
推荐苏州菜
天数：2天
预算：medium
```

"天数：2天 预算：medium"是系统自动加的，用户没有输入这些。而且修改流程中完整行程 JSON 已包含这些元数据，完全是冗余信息。

### 优化具体实现

| 文件 | 改动 |
|---|---|
| [HttpTripPlanningAiClient.java](file:///d:/Code/Project/Agent/agent-project/intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/service/ai/HttpTripPlanningAiClient.java) | `buildNiceUserMessage()` 在 modify 模式下直接返回原始 userPrompt |

```java
if (isModifyMode) {
    return userPrompt; // 不再追加"天数：X天\n预算：X"
}
```

### 优化后效果

- 用户输入什么，Agent 就收到什么，不再被强加前缀和冗余字段
- 自然语言输入：`推荐好吃一点的苏州特色菜` 即可

---

## 5. AI 对话持久化与恢复

### 问题发现

用户反馈：AI 对话面板退出重进后，之前和 AI 的对话内容就丢失/变成其他内容。根因是：

1. `chatConversationId` 是 Vue 组件内存变量，刷新归 `null`
2. 聊天回复只通过 SSE 推送给前端一次，**没有保存到数据库**
3. 前端从不从数据库加载历史对话

### 优化具体实现

**后端** ([ItineraryServiceImpl.java](file:///d:/Code/Project/Agent/agent-project/intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/service/serviceImpl/ItineraryServiceImpl.java))：

```java
// 聊天回复保存到 conversation 数据库
ConversationMessage chatMsg = new ConversationMessage();
chatMsg.setConversationId(conversationId);
chatMsg.setRole("assistant");
chatMsg.setContent(chatAnswer);
chatMsg.setMessageType("CHAT");
conversationMessageRepository.save(chatMsg);
```

**前端** ([BattleCustomizePlan.vue](file:///d:/Code/Project/Agent/agent-project/planclient/src/views/battle/BattleCustomizePlan.vue))：

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

### 优化后效果

- 退出重进 → 从 localStorage 恢复 conversationId → 调 API 加载历史 → 对话内容不变
- 聊天回复持久化到数据库，刷新后仍可见

---

## 6. 普通对话功能（纯聊天）

### 问题发现

行程编辑页面只有"AI完善"功能（修改行程），用户无法进行普通对话咨询（如了解目的地信息、旅行建议等）。

### 优化具体实现

**新增文件（6 个）：**

| 文件 | 说明 |
|---|---|
| [AiChatRequest.java](file:///d:/Code/Project/Agent/agent-project/intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/vo/ai/AiChatRequest.java) | `message` + `conversationId` DTO |
| [AiChatController.java](file:///d:/Code/Project/Agent/agent-project/intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/controller/AiChatController.java) | `POST /api/ai/chat/stream` SSE 端点 |
| [AiChatService.java](file:///d:/Code/Project/Agent/agent-project/intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/service/AiChatService.java) | 接口 |
| [AiChatServiceImpl.java](file:///d:/Code/Project/Agent/agent-project/intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/service/serviceImpl/AiChatServiceImpl.java) | 核心实现：复用 ConversationContextManager 加载历史上下文 + 流式调用 LLM + token-by-token 推送 |
| [chat.ts](file:///d:/Code/Project/Agent/agent-project/planclient/src/api/chat.ts) | 前端 SSE 解析 + `streamChat()` 函数 |

**前端改动：** `BattleCustomizePlan.vue` 顶部栏新增"💬 对话"按钮（紫色），右侧面板 v-if 切换 AgentPanel / ChatPanel，消息气泡样式，打字动画。

### 优化后效果

- 用户可在不修改行程的情况下，与 AI 进行旅行咨询对话
- 流式响应，逐字展示
- 对话历史自动保存，刷新不丢

---

## 7. Skill 模块重构（参考资料 + 脚本注入）

### 问题发现

Skill 模块最初只有 name/description/prompt，缺少 Anthropic 规范中的**捆绑资源**支持（参考资料 references + 可执行脚本 scripts）。

### 优化具体实现

**后端（6 文件）：**

| 文件 | 变更 |
|---|---|
| [StringMapJsonConverter.java](file:///d:/Code/Project/Agent/agent-project/intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/po/converter/StringMapJsonConverter.java) | 新增 — `Map<String,String>` ↔ JSON TEXT 的 JPA AttributeConverter |
| [SkillPO.java](file:///d:/Code/Project/Agent/agent-project/intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/po/SkillPO.java) | `+references` `+scripts` 两个 TEXT 列 |
| [SkillVO.java](file:///d:/Code/Project/Agent/agent-project/intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/vo/skill/SkillVO.java) | +references / +scripts 字段 |
| [SkillSaveRequest.java](file:///d:/Code/Project/Agent/agent-project/intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/vo/skill/SkillSaveRequest.java) | +references / +scripts 字段 |
| [SkillServiceImpl.java](file:///d:/Code/Project/Agent/agent-project/intellitravel-pioneer/src/main/java/com/travel/intellitravel_pioneer/service/serviceImpl/SkillServiceImpl.java) | CRUD 保存 references/scripts；`buildSkillContext()` 按三层注入 |

**Agent 注入格式（Anthropic 分层规范）：**

```
## 用户启用的专属技能

### 摄影旅拍助手
描述：专注于拍照打卡点的行程优化
要求：每天安排1-2个适合拍照的打卡点...

**参考资料：**
#### 北京拍照攻略.md
[文档内容...]

**可执行脚本：**
脚本 `golden_hour.py`：
\```python
import json
from datetime import datetime, timedelta

def get_golden_hours(sunrise: str, sunset: str) -> dict:
    ...
\```
```

**前端（2 文件）：**

| 文件 | 变更 |
|---|---|
| [skill.ts](file:///d:/Code/Project/Agent/agent-project/planclient/src/api/skill.ts) | SkillVO/SkillSaveRequest 增加 `references: Record<string,string>` 和 `scripts: Record<string,string>` |
| [SkillManage.vue](file:///d:/Code/Project/Agent/agent-project/planclient/src/views/battle/SkillManage.vue) | Dialog 新增可折叠的"📚 参考资料"和"⚙️ 可执行脚本"编辑区（添加/删除 key-value 对）；卡片显示资源数量标签 |

### 优化后效果

- Skill 支持参考资料和脚本的创建、编辑、查看
- Agent 规划行程时自动注入用户启用的技能提示词 + 参考资料 + 脚本
- 按 Anthropic 规范采用三层注入：元数据 → 正文 prompt → 捆绑资源

---

## 优化总结

| 优化项 | 问题 | 方案 | 效果 |
|---|---|---|---|
| Agent Loop 死循环防护 | LLM 连调同一工具数十次触发 429 | 三档检测 + 30 轮兜底 | 提前中断，清晰报错 |
| XML 工具调用兼容 | Qwen 非标准格式导致解析崩溃 | 正则解析 + 执行器 | 兼容非标准格式 |
| 对话 vs 修改分流 | 关键词匹配 | 聊天不进 Agent Loop | "推荐好吃的"不再改行程 |
| 消息格式优化 | 系统强加冗余前缀/字段 | 原始输入直传 | 用户输入即所得 |
| AI 对话持久化 | 退出重进内容丢失 | localStorage + DB 双持久化 | 刷新不丢对话 |
| 普通对话功能 | 缺少纯聊天入口 | 新增 SSE 聊天端点 + 前端面板 | 可聊天咨询 |
| Skill 捆绑资源 | 缺 references/scripts | Map<String,String> JSON 存储 + 三层注入 | 支持参考资料和脚本 |

