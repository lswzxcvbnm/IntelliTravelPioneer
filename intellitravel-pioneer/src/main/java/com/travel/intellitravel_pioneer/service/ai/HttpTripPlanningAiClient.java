package com.travel.intellitravel_pioneer.service.ai;
import java.util.Optional;
import com.travel.intellitravel_pioneer.vo.conversation.AgentMessageVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.service.SkillService;
import com.travel.intellitravel_pioneer.service.ToolManagementService;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryRequest;
import com.travel.intellitravel_pioneer.vo.trip.GenerationResult;
import com.travel.intellitravel_pioneer.vo.trip.GenerationStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.travel.intellitravel_pioneer.service.IntentStateMachine;
import com.travel.intellitravel_pioneer.service.ActionTracker;
import com.travel.intellitravel_pioneer.service.IntentStateMachine.Intent;
import com.travel.intellitravel_pioneer.service.IntentStateMachine.State;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.travel.intellitravel_pioneer.service.ConversationContextManager;
import com.travel.intellitravel_pioneer.service.MemoryManager;
import com.travel.intellitravel_pioneer.service.EntityTracker;

@Component
public class HttpTripPlanningAiClient implements TripPlanningAiClient {

    private final ObjectMapper objectMapper;
    private final ToolManagementService toolManagementService;
    private final TokenUtil tokenUtil;
    private static final Logger log = LoggerFactory.getLogger(HttpTripPlanningAiClient.class);
    @Autowired
    private ConversationContextManager contextManager;

    @Autowired
    private MemoryManager memoryManager;

    @Autowired
    private EntityTracker entityTracker;

    @Autowired
    private IntentStateMachine intentStateMachine;

    @Autowired
    private ActionTracker actionTracker;

    @Value("${trip.ai.provider}")
    private String aiProvider;

    @Value("${trip.ai.base-url}")
    private String aiBaseUrl;

    @Value("${trip.ai.plan-path}")
    private String aiPlanPath;

    @Value("${trip.ai.status-analysis-path}")
    private String aiStatusAnalysisPath;

    @Value("${trip.ai.llm.base-url}")
    private String llmBaseUrl;

    @Value("${trip.ai.llm.chat-path}")
    private String llmChatPath;

    @Value("${trip.ai.llm.api-key}")
    private String llmApiKey;

    @Value("${trip.ai.llm.model}")
    private String llmModel;

    @Value("${trip.ai.llm.temperature}")
    private Double llmTemperature;

    @Value("${trip.ai.amap.mcp-url}")
    private String amapMcpUrl;

    @Value("${trip.ai.loop.max-same-tool-consecutive:25}")
    private int maxSameToolConsecutive;

    @Value("${trip.ai.loop.same-args-threshold:3}")
    private int sameArgsLoopThreshold;

    @Autowired
    private SkillService skillService;

    public HttpTripPlanningAiClient(ObjectMapper objectMapper, ToolManagementService toolManagementService, TokenUtil tokenUtil) {
        this.objectMapper = objectMapper;
        this.toolManagementService = toolManagementService;
        this.tokenUtil = tokenUtil;
    }

    @Override
    public ItineraryPlan generateItinerary(String token, ItineraryRequest request) {
        GenerationResult result = generateItineraryWithStats(token, request, null);
        return result != null ? result.getPlan() : null;
    }

    @Override
    public GenerationResult generateItineraryWithStats(String token, ItineraryRequest request,
            java.util.function.Consumer<String> toolNotifier) {
        if (isLlmProvider()) {
            return callLlmForItinerary(token, request, toolNotifier);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("request", request);

        String rawResponse = callCustomAiService(aiPlanPath, token, requestBody);
        ItineraryPlan plan = parseItinerary(rawResponse);
        return new GenerationResult(plan, null);
    }

    @Override
    public ItineraryPlan modifyItinerary(String token, ItineraryRequest request,
            ItineraryPlan currentPlan, String userPrompt, String conversationId) {
        GenerationResult result = modifyItineraryWithStats(token, request, currentPlan, userPrompt, null,conversationId);
        return result != null ? result.getPlan() : null;
    }

    @Override
    public GenerationResult modifyItineraryWithStats(String token, ItineraryRequest request,
            ItineraryPlan currentPlan, String userPrompt,
            java.util.function.Consumer<String> toolNotifier,String conversationId) {
        if (!isLlmProvider()) {
            throw new IntellitravelException("非 LLM 模式下暂不支持行程修改");
        }
        return callLlmForRefine(token, request, toJsonString(currentPlan), userPrompt, toolNotifier,conversationId);
    }

    private GenerationResult callLlmForRefine(String token, ItineraryRequest request, String currentPlanJson,
            String userPrompt, java.util.function.Consumer<String> toolNotifier,String conversationId) {
        System.out.println(">>> callLlmForRefine 被调用了！<<<");
        System.out.println(">>> conversationId: " + conversationId);
        System.out.println(">>> userPrompt: " + userPrompt);

        // AI意图分类：判断是修改行程还是闲聊
        if (userPrompt != null && !userPrompt.isBlank()) {
            String intentJson = classifyAndStructureModifyIntent(userPrompt, currentPlanJson);
            System.out.println(">>> Modify intent analysis result: " + intentJson);
            
            if (!isModifyIntent(intentJson)) {
                // 闲聊：调用聊天回答，不修改行程
                System.out.println(">>> Intent is CHAT, generating chat answer <<<");
                String chatAnswer = answerChatQuestion(userPrompt, currentPlanJson);
                if (toolNotifier != null) {
                    sendPhaseNotification(toolNotifier, "REFINE_END", chatAnswer);
                    toolNotifier.accept("THOUGHT:" + (chatAnswer.length() > 200 ? chatAnswer.substring(0, 200) + "..." : chatAnswer));
                }
                try {
                    ItineraryPlan originalPlan = objectMapper.readValue(currentPlanJson, ItineraryPlan.class);
                    return new GenerationResult(originalPlan, null, chatAnswer);
                } catch (Exception e) {
                    return new GenerationResult(null, null, chatAnswer);
                }
            }
            
            // 修改请求：构建结构化修改提示词
            System.out.println(">>> Intent is MODIFY, building structured prompt <<<");
            String structuredPrompt = buildStructuredModifyPrompt(intentJson, userPrompt);
            userPrompt = structuredPrompt; // 用结构化提示词替换原始输入
            System.out.println(">>> Structured modify prompt: " + structuredPrompt.substring(0, Math.min(100, structuredPrompt.length())) + "...");
        }

        EntityTracker.ExtractedEntities entities = null;
        MemoryManager.WorkingMemory workingMemory = null;
        String contradictionWarning = null;
        GenerationStats stats = new GenerationStats();

        String userId = null;
        try {
            if (token != null && !token.isBlank()) {
                var user = tokenUtil.getUser(token);
                if (user != null) {
                    userId = String.valueOf(user.getId());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to extract user ID: " + e.getMessage());
        }

        // 意图检测和状态更新
        if (conversationId != null && !conversationId.isBlank() && userPrompt != null) {
            Intent intent = intentStateMachine.detectIntent(userPrompt);
            intentStateMachine.updateIntent(conversationId, intent);

            //  显示操作历史
            if (intent == Intent.PLAN_SHOW_HISTORY) {
                String historySummary = actionTracker.getActionHistorySummary(conversationId);
                userPrompt = "用户要求查看操作历史记录。请回复以下历史记录，并询问用户想重复哪个操作：\n" + historySummary;
                // 直接返回，不调用LLM修改
                sendPhaseNotification(toolNotifier, "REFINE_END", historySummary);
                return new GenerationResult(null, stats); // 返回原行程不变
            }

            //  按序号重复
            else if (intent == Intent.PLAN_REPEAT_BY_NUMBER) {
                Pattern pattern = Pattern.compile("重复第(\\d+)(?:个|次|条)?");
                Matcher matcher = pattern.matcher(userPrompt);
                if (matcher.find()) {
                    int seqNumber = Integer.parseInt(matcher.group(1));
                    Optional<ActionTracker.UserAction> targetAction = actionTracker.getActionBySequence(conversationId, seqNumber);
                    if (targetAction.isPresent()) {
                        ActionTracker.UserAction action = targetAction.get();
                        System.out.println("[ActionTracker] Repeating action #" + seqNumber + ": " + action.getActionType());
                        userPrompt = String.format("用户要求重复第%d个操作。目标操作是：【%s】（操作类型：%s）。请执行相同的操作。",
                                seqNumber, action.getDescription(), action.getActionType());
                    } else {
                        userPrompt = "用户要求重复第" + seqNumber + "个操作，但该序号不存在。请告诉用户：当前共有" +
                                actionTracker.getActionHistory(conversationId).size() + "条操作历史记录。";
                    }
                }
            }


            // 重复上上一个
            else if (intent == Intent.PLAN_REPEAT_PREVIOUS) {
                Optional<ActionTracker.UserAction> previousAction = actionTracker.getActionByOffset(conversationId, 2);
                if (previousAction.isPresent()) {
                    ActionTracker.UserAction action = previousAction.get();
                    userPrompt = String.format("用户要求重复上上一次操作。目标操作是：【%s】。请执行相同的操作。", action.getDescription());
                } else {
                    userPrompt = "用户要求重复上上一次操作，但还没有足够的历史操作记录。";
                }
            }

            // 重复上一次
            else if (intent == Intent.PLAN_REPEAT_LAST) {
                Optional<ActionTracker.UserAction> lastAction = actionTracker.getLastAction(conversationId);
                if (lastAction.isPresent()) {
                    ActionTracker.UserAction action = lastAction.get();
                    userPrompt = String.format("用户要求重复上一次操作。上一次操作是：【%s】。请执行相同的操作。", action.getDescription());
                } else {
                    userPrompt = "用户要求重复上一次操作，但还没有执行过任何修改操作。";
                }
            }
            if (intent == Intent.PLAN_MODIFY) {
                intentStateMachine.updateState(conversationId, State.MODIFYING);
            } else if (intent == Intent.PLAN_QUERY) {
                intentStateMachine.updateState(conversationId, State.GATHERING_INFO);
            } else if (intent == Intent.PLAN_REPEAT_LAST) {
                intentStateMachine.updateState(conversationId, State.MODIFYING);

                Optional<ActionTracker.UserAction> lastAction = actionTracker.getLastAction(conversationId);
                if (lastAction.isPresent()) {
                    ActionTracker.UserAction action = lastAction.get();
                    System.out.println("[ActionTracker] Repeating last action: " + action.getActionType());
                    userPrompt = String.format("用户要求重复上一次操作。上一次操作是：【%s】。请执行相同的操作。", action.getDescription());
                } else {
                    userPrompt = "用户要求重复上一次操作，但还没有执行过任何修改操作。请告诉用户：还没有执行过修改操作。";
                }
            } else {
                intentStateMachine.updateState(conversationId, State.PLANNING);
            }
        }

        // 实体追踪
        if (conversationId != null && !conversationId.isBlank() && userPrompt != null) {
            entities = entityTracker.loadEntities(conversationId);
            if (entities == null) {
                entities = new EntityTracker.ExtractedEntities();
            }
            entities = entityTracker.extractEntities(userPrompt, entities);
            entityTracker.saveEntities(conversationId, entities);
        }

        // 工作记忆
        if (conversationId != null && !conversationId.isBlank()) {
            workingMemory = memoryManager.getWorkingMemory(conversationId);
        }

        // 矛盾检测
        if (conversationId != null && !conversationId.isBlank() && userPrompt != null && entities != null) {
            Optional<EntityTracker.Contradiction> contradiction =
                    entityTracker.detectContradiction(userPrompt, entities, workingMemory);
            if (contradiction.isPresent()) {
                contradictionWarning = buildContradictionWarning(contradiction.get());
                log.warn("【矛盾检测】修改阶段发现矛盾: {}", contradiction.get().getConflicts());
            }
        }


        stats = new GenerationStats();
        String amapUrl = amapMcpUrl;
        try (AmapMcpClient mcpClient = new AmapMcpClient(amapUrl)) {
            mcpClient.initialize();

            JsonNode mcpTools = mcpClient.listTools();
            JsonNode tools = mergeTools(mcpTools);

            sendPhaseNotification(toolNotifier, "REFINE_START",
                    "开始分析修改建议，调用工具验证新地点...");

            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", REFINE_SYSTEM_PROMPT));
            String skillContext = skillService.buildSkillContext(request.getSkillIds());
            String refineUserMessage = "当前已有行程计划：\n" + currentPlanJson + "\n\n用户修改建议：\n" + userPrompt;
            if (skillContext != null && !skillContext.isBlank()) {
                refineUserMessage = refineUserMessage + skillContext;
            }
            messages.add(Map.of("role", "user", "content", refineUserMessage));
            ConversationContextManager.OptimizedContext optimizedContext = null;
            if (conversationId != null && !conversationId.isBlank()) {
                try {
                    optimizedContext = contextManager.getOptimizedContext(conversationId, userPrompt,token);
                    log.info("【修改阶段-上下文】对话ID: {}, 原始消息数: {}, 优化后消息数: {}, 摘要: {}",
                            conversationId,
                            optimizedContext.getOriginalCount(),
                            optimizedContext.getOptimizedCount(),
                            optimizedContext.getSummary() != null ? optimizedContext.getSummary().substring(0, Math.min(100, optimizedContext.getSummary().length())) + "..." : "无");
                    if (optimizedContext.getSummary() != null && !optimizedContext.getSummary().isBlank()) {
                        messages.add(1, Map.of("role", "system", "content",
                                "【对话历史摘要】以下是之前对话的关键信息摘要，请基于这些信息理解上下文：\n\n" + optimizedContext.getSummary()));
                    }
                } catch (Exception e) {
                    log.warn("获取优化上下文失败: {}", e.getMessage());
                }
            }

            if (conversationId != null && !conversationId.isBlank() && userPrompt != null) {
                String tempActionType = detectActionType(userPrompt);
                if ("UPDATE_DAYS".equals(tempActionType)) {
                    int daysResult = extractDaysFromPrompt(userPrompt);
                    int oldDays = request.getDays();
                    int newDays;
                    if (daysResult > 0) {
                        // 明确数字，如 "改为5天" → 5
                        newDays = daysResult;
                    } else if (daysResult == -1) {
                        // 相对+1，如 "多玩一天" → old + 1
                        newDays = oldDays + 1;
                    } else if (daysResult == -2) {
                        // 相对-1，如 "少玩一天" → old - 1
                        newDays = Math.max(1, oldDays - 1);
                    } else {
                        newDays = 0;
                    }
                    if (newDays > 0 && newDays != oldDays) {
                        log.info("【天数修改】将天数从 {} 改为 {}（在行程生成前更新）", oldDays, newDays);
                        request.setDays(newDays);
                        if (toolNotifier != null) {
                            toolNotifier.accept("THOUGHT:检测到天数修改请求，已将行程天数从 " + oldDays + " 天调整为 " + newDays + " 天");
                        }
                    }
                }
            }
            ItineraryPlan refinedPlan;
            int executeRetries = 0;
            int maxExecuteRetries = 2;
            while (true) {
                try {
                    refinedPlan = runAgentLoop(token, messages, tools, mcpClient, request, toolNotifier, stats);
                    break;
                } catch (IntellitravelException ie) {
                    throw ie;
                } catch (Exception e) {
                    executeRetries++;
                    System.err.println("Refine phase failed (attempt " + executeRetries + "): " + e.getMessage());
                    if (executeRetries >= maxExecuteRetries) {
                        throw new IntellitravelException("行程修改失败（已重试" + executeRetries + "次）：" + e.getMessage());
                    }
                    if (toolNotifier != null) {
                        toolNotifier.accept("THOUGHT:修改阶段异常（第" + executeRetries + "次），正在重新初始化...");
                    }
                    try { Thread.sleep(1500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw ie; }
                }
            }

            sendPhaseNotification(toolNotifier, "REFINE_END",
                    "行程修改完成，共" + (refinedPlan.getDailyPlans() != null ? refinedPlan.getDailyPlans().size() : 0) + "天");
            if (conversationId != null && !conversationId.isBlank() && userPrompt != null) {
                String actionType = detectActionType(userPrompt);

                Map<String, Object> params = new HashMap<>();
                params.put("plan", refinedPlan);
                params.put("userPrompt", userPrompt);
                actionTracker.recordAction(conversationId, actionType, userPrompt, params);
                log.info("【操作记忆】修改阶段记录操作: {} - {}", actionType, userPrompt);
            }
            return new GenerationResult(refinedPlan, stats);

        } catch (IntellitravelException ie) {
            throw ie;
        } catch (Exception e) {
            e.printStackTrace();
            throw new IntellitravelException("啊哦，修改行程时遇到了小阻碍，请稍后重试哦～");
        }
    }

    private String callCustomAiService(String path, String token, Object requestBody) {
        if (aiBaseUrl == null || aiBaseUrl.isBlank()) {
            throw new IntellitravelException("未配置 AI 规划服务");
        }

        int maxRetries = 3;
        int attempt = 0;
        long backoff = 1000;
        Exception lastException = null;

        while (attempt < maxRetries) {
            try {
                return RestClient.create()
                        .post()
                        .uri(aiBaseUrl + path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(headers -> {
                            if (token != null && !token.isBlank()) {
                                headers.set("token", token);
                            }
                        })
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);
            } catch (Exception e) {
                lastException = e;
                attempt++;
                if (attempt >= maxRetries)
                    break;
                try {
                    Thread.sleep(backoff);
                    backoff *= 2;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IntellitravelException("AI 调用被迫中断: " + ie.getMessage());
                }
            }
        }
        throw new IntellitravelException("AI 服务当前比较繁忙，请稍等片刻再试哦～");
    }

    private boolean isLlmProvider() {
        return "llm".equalsIgnoreCase(aiProvider);
    }

    // ==================== Refine System Prompt ====================
    private static final String REFINE_SYSTEM_PROMPT = """
            你是一位行程修改 Agent。你将收到一份已有行程计划（JSON 格式）和用户的修改建议。
            你的任务是分析用户意图，对已有行程执行精确修改，并调用必要的工具验证新地点的真实性。

            【意图识别与操作】

            1.【更改天数】（如"多玩一天""再加一天""少一天""延长到5天"）
               注：此时 request 中的 totalDays 可能已被预先更新，以 request 中的值为准。

               ★ 增加天数（如 3天→4天）：
                 a) 保留原有的 3 天内容不变。
                 b) 调用 maps_text_search 为新增的 Day 4 搜索推荐景点（用目的地名称+类型关键词，
                    如"南京 热门景点"），选 2~4 个合适结果填入。
                 c) 如果搜索结果不足，从原有行程中抽取最后一天的 1~2 项移到新天，并为被抽走的天补充新景点。

               ★ 减少天数（如 3天→2天）：
                 a) 删除最后一天（dayNumber 最大的），将其内容按类型就近插入到前面各天。
                 b) 每项插入到同类型项目后面；如果前面没有同类型项目，追加到那天末尾。
                 c) 如果某天项目数超过 5 个，考虑精简（保留最知名的）。

            2.【纯删除意图】（如"删掉故宫""去掉第2天"）
               → 不需要调用任何工具，直接在已有 JSON 上删除指定条目。

            3.【新增或替换地点】（如"加一个故宫""把长城换成颐和园"）
               → 必须调用 maps_text_search 查询新地点的真实名称和精确经纬度，
                 验证通过后才写入 JSON。严禁凭空编造坐标。
               → 如果 maps_text_search 返回空结果，尝试修改搜索关键词（如去掉"市/区"后缀），
                 若仍无结果，在 Thought 中说明并在最终结果中使用用户提供的名称，location 标注为 null。

            4.【仅修改描述/预算】（如"门票改成60元""预算调整为2000"）
               → 不需要调用工具，直接修改对应字段。

            【输出规范】
            - 不改动的部分原样保留
            - Thought 里说明每一步操作
            - 更改天数时，新增的天必须用 maps_text_search 填充景点，不能留空
            - 新增/替换地点后必须做 SelfCheck

            【SelfCheck 自检（输出前必须完成）】
            Thought: SelfCheck:
            OK 所有新增天都有景点填充 -> [已验证]
            OK 新增地点坐标来自工具返回 -> [已验证/存在未验证项]
            OK totalDays 与 dailyPlans 数组长度一致 -> [一致]
            OK 每天项目数合理（2~6项） -> [合理/第X天偏多]
            OK 预算仍与需求匹配 -> [匹配]

            - 最终用 final_output 包裹完整修正 JSON，中间不要有其他文字

            JSON 格式：
            {"title":"...", "estimatedCost":1500.0, "totalDays":3,
             "dailyPlans":[{"dayNumber":1, "items":[{"type":"景点|餐饮|住宿|购物",
             "name":"具体地点名称","description":"详细描述",
             "location":{"lat":39.9163,"lng":116.3971,"name":"位置名称"}}]}]}
            注意：description 每条控制在 30 字以内，简洁说明特色即可。""";

    // ==================== Plan System Prompt ====================
    private static final String PLAN_SYSTEM_PROMPT = """
            你是一位专业的旅行规划策略师。用户会给出出行需求，你需要制定一份【规划策略】，而不是直接给出行程。

            请按以下结构输出规划：
            ## 需求分析
            - 目的地特点
            - 时间约束
            - 预算约束
            - 用户偏好

            ## 任务分解（你需要按顺序完成以下子任务）
            1. 天气查询 -> 决定行程类型
            2. 景点搜索 -> 确认可玩项目
            3. 餐饮搭配 -> 结合景点安排
            4. 路线规划 -> 优化交通
            5. 预算分配 -> 总预算控制

            ## 执行策略
            - 你打算分几轮来执行？每轮要查什么？
            - 哪些信息可以并行查询？
            - 如果某个景点找不到怎么办？备选方案是什么？

            注意：本阶段只做规划，不要输出具体行程！""";

    // ==================== Execute System Prompt ====================
    private static final String EXECUTE_SYSTEM_PROMPT_PREFIX = """
            你是一位具备自主规划能力的旅行 Agent，会主动思考、分析、验证并执行计划。

            【工作流程】
            遵循制定好的执行规划，按子任务顺序执行。每一轮按以下格式输出：
            Thought: [分析当前进度，说明本轮要完成什么子任务，为什么需要调用工具]

            【核心规则】
            1. 并行调用：同一轮可同时调用多个无依赖的工具，提高效率
               - 可并行：天气查询 + 景点搜索 + 目的地信息查询
               - 可并行：多个景点的详情查询
               - 不可并行：需要先搜索景点，才能查询周边餐饮
            2. 失败处理：工具调用失败时在 Thought 中说明，并尝试备选方案
            3. 严禁编造：所有地点名称和坐标必须来自工具返回结果
            4. 每天至少安排 2-3 个行程项
            5. 天数硬约束：totalDays 和 dailyPlans 的数量必须严格等于用户请求的天数

            【行程质量标准】
            生成的行程必须满足以下质量要求：
            - 完整性：每天至少 2-3 个行程项，覆盖景点、餐饮、住宿（多日游）
            - 真实性：所有地点名称必须来自工具返回或已知真实景点，经纬度必须真实
            - 合理性：同一天内景点距离不宜过远（建议同区域安排），路线不绕路
            - 个性化：根据用户偏好（亲子/情侣/朋友）调整活动类型和节奏
            - 可执行性：描述具体（如"颐和园长廊"而非"当地景点"），预算合理
            - 预算匹配：总预算与用户选择的档次（经济/舒适/豪华）匹配

            【可用工具】
            - 高德地图工具（maps_开头）：查询真实地理信息、天气、路线，验证地点经纬度
            - search_destination_info：查询目的地旅行季节、热门景点、当地美食、交通建议

            【停止规则】
            最多 10 轮工具调用。完成以下所有步骤后立即停止并输出最终结果：
            1. 已查询目的地天气
            2. 已搜索并验证足够的景点（每天至少 2-3 个）
            3. 已搜索必要的餐饮/住宿信息
            4. 已完成 SelfCheck 自检

            【SelfCheck 自检（输出前必须完成）】
            Thought: SelfCheck:
            OK totalDays 等于用户请求天数 -> [一致/不一致]
            OK 每天至少3个行程项 -> [已检查]
            OK 所有地点名称来自工具返回 -> [已验证]
            OK 无模糊描述 -> [全部具体]

            【示例】
            用户请求：北京3天亲子游，预算3000元

            Thought: 用户需要北京3天亲子游，我先查询天气和搜索景点信息。
            工具调用: maps_weather(北京) + maps_text_search(北京亲子景点)
            工具返回: 天气晴朗25℃，搜索到故宫、颐和园、北京动物园等

            Thought: 已获得景点信息，继续搜索餐饮和住宿。
            工具调用: maps_around_search(故宫附近餐厅)
            工具返回: 找到东来顺、全聚德等餐厅

            Thought: SelfCheck:
            OK totalDays=3 等于用户请求天数 -> [一致]
            OK 每天3个行程项 -> [已检查]
            OK 所有地点名称来自工具返回 -> [已验证]
            OK 无模糊描述 -> [全部具体]

            final_output
            {"title":"北京3天亲子游","estimatedCost":3000.0,"totalDays":3,"dailyPlans":[...]}
            /final_output

            【最终输出格式】
            用 final_output 标签包裹纯 JSON：
            final_output
            {"title":"...", "estimatedCost":1500.0, "totalDays":N, "dailyPlans":[{"dayNumber":1, "items":[{"type":"景点|餐饮|住宿|购物","name":"具体地点名称","description":"详细描述（30字以内）","location":{"lat":39.9163,"lng":116.3971,"name":"位置名称"}}]}]}
            /final_output
            其中 N 必须严格等于用户 request 中的 days 字段值。""";

    // ==================== Verify System Prompt ====================
    private static final String VERIFY_SYSTEM_PROMPT = """
            你是旅行行程质量审核员。请对以下行程计划进行严格审核，逐项检查：

            ## 审核清单
            1. 【完整性】每天是否至少有3个行程项？是否有空缺？
            2. 【真实性】所有地点名称和经纬度是否来自真实查询结果？
            3. 【合理性】景点之间的距离是否合理？同一天内路线是否绕路？
            4. 【预算匹配】总预算是否与用户选择的档次（经济/舒适/豪华）匹配？
            5. 【偏好覆盖】是否覆盖了用户的兴趣偏好？
            6. 【描述质量】每个行程项的描述是否具体可执行？（禁止"当地特色餐厅"等模糊描述）

            ## 输出要求
            - 如果审核通过，输出：{"status":"PASS","issues":[],"summary":"总体评价..."}
            - 如果审核不通过，输出修正后的完整行程 JSON（用 final_output 标签包裹），并在 JSON 之前说明修正内容：

            修正内容：[具体说明修正了哪些问题]
            final_output
            {"title":"...", "estimatedCost":1500.0, "totalDays":N, "dailyPlans":[...]}
            /final_output

            注意：修正时必须保持原行程的整体框架，只针对问题进行局部调整。""";

    // ==================== Custom Agent Tool Definitions ====================
    private static final String CUSTOM_TOOLS_JSON = """
            [
              {
                "type": "function",
                "function": {
                  "name": "search_destination_info",
                  "description": "搜索目的地的实用旅行信息，包括最佳旅行季节、必去景点排名、当地特色美食、交通建议等。此工具整合了高质量旅行知识库。",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "destination": {
                        "type": "string",
                        "description": "目的地名称"
                      },
                      "category": {
                        "type": "string",
                        "enum": ["best_season", "top_attractions", "local_food", "transport_tips", "all"],
                        "description": "信息类别"
                      }
                    },
                    "required": ["destination"]
                  }
                }
              }
            ]
            """;

    private JsonNode buildCustomTools() {
        try {
            return objectMapper.readTree(CUSTOM_TOOLS_JSON);
        } catch (Exception e) {
            return objectMapper.createArrayNode();
        }
    }

    private JsonNode mergeTools(JsonNode mcpTools) {
        JsonNode customTools = buildCustomTools();
        if (customTools.isEmpty()) return mcpTools;
        if (mcpTools == null || mcpTools.isEmpty()) return customTools;
        ArrayNode merged = objectMapper.createArrayNode();
        merged.addAll((ArrayNode) mcpTools);
        merged.addAll((ArrayNode) customTools);
        return merged;
    }

    private String executeCustomTool(String name, String argsJson) {
        try {
            JsonNode args = objectMapper.readTree(argsJson);
            String destination = args.path("destination").asText("");

            if ("search_destination_info".equals(name)) {
                String category = args.path("category").asText("all");
                return getDestinationInfo(destination, category);
            }

            return "未知工具：" + name;
        } catch (Exception e) {
            return "工具执行出错：" + e.getMessage();
        }
    }

    private String getDestinationInfo(String destination, String category) {
        // 内置中国主要城市旅行知识库
        StringBuilder info = new StringBuilder();
        info.append("【").append(destination).append("旅行信息】\n\n");

        Map<String, String[]> destInfo = buildTravelKnowledgeBase();

        String key = null;
        for (String k : destInfo.keySet()) {
            if (destination.contains(k) || k.contains(destination)) {
                key = k;
                break;
            }
        }

        if (key == null) {
            info.append("最佳旅行季节：春秋两季（4-5月、9-10月）气候宜人，适合出行。\n");
            info.append("热门景点：建议使用 maps_text_search 工具进一步搜索具体景点。\n");
            info.append("当地美食：建议使用 maps_around_search 工具搜索特色餐饮。\n");
            info.append("交通建议：建议使用 maps_direction 系列工具规划具体路线。\n");
            return info.toString();
        }

        String[] data = destInfo.get(key);
        if (data == null) return info.toString();

        String season = data[0];
        String spots = data[1];
        String food = data[2];
        String transport = data[3];

        if ("all".equals(category) || "best_season".equals(category)) {
            info.append("最佳旅行季节：").append(season).append("\n");
        }
        if ("all".equals(category) || "top_attractions".equals(category)) {
            info.append("热门景点：").append(spots).append("\n");
        }
        if ("all".equals(category) || "local_food".equals(category)) {
            info.append("当地美食：").append(food).append("\n");
        }
        if ("all".equals(category) || "transport_tips".equals(category)) {
            info.append("交通建议：").append(transport).append("\n");
        }
        info.append("\n（建议进一步使用 maps_text_search 验证景点经纬度，使用 maps_weather 查询实际天气。）\n");
        return info.toString();
    }

    private Map<String, String[]> buildTravelKnowledgeBase() {
        Map<String, String[]> db = new HashMap<>();
        db.put("北京", new String[]{
                "春季(4-5月)和秋季(9-10月)最佳，气候宜人，避开暑期高温和国庆人流高峰。",
                "故宫博物院、八达岭长城、天坛公园、颐和园、南锣鼓巷、798艺术区、国家博物馆、雍和宫。",
                "北京烤鸭、炸酱面、豆汁焦圈、涮羊肉、卤煮火烧、驴打滚、糖火烧。",
                "地铁网络发达覆盖主要景点，建议办交通卡；高峰期限行，二环内建议地铁出行。"
        });
        db.put("上海", new String[]{
                "春秋两季(3-5月、9-11月)最佳，避开梅雨季(6-7月)和台风季(8月)。",
                "外滩、东方明珠、豫园、南京路步行街、迪士尼乐园、田子坊、新天地、上海博物馆。",
                "小笼包、生煎馒头、本帮红烧肉、蟹粉豆腐、葱油拌面、八宝鸭、擂沙圆。",
                "地铁覆盖全市，建议使用Metro大都会APP；浦西老城区推荐步行+共享单车。"
        });
        db.put("杭州", new String[]{
                "春季(3-5月)赏花和秋季(9-11月)最佳，避开暑期高温和节假日高峰。",
                "西湖、灵隐寺、宋城、千岛湖、西溪湿地、雷峰塔、河坊街、龙井村。",
                "西湖醋鱼、东坡肉、龙井虾仁、叫化鸡、片儿川、定胜糕、葱包烩。",
                "地铁+公交可到主要景点；西湖周边建议骑行，有完善的共享单车和公共自行车系统。"
        });
        db.put("成都", new String[]{
                "春秋两季(3-5月、9-11月)最佳，夏天闷热多雨，冬季湿冷。",
                "宽窄巷子、锦里古街、大熊猫繁育基地、都江堰、青城山、武侯祠、杜甫草堂、春熙路。",
                "火锅、串串香、担担面、钟水饺、龙抄手、麻辣兔头、钵钵鸡、三大炮。",
                "地铁覆盖主城区；去都江堰可在犀浦站乘城际列车；景点集中区建议地铁+步行。"
        });
        db.put("西安", new String[]{
                "春秋两季(3-5月、9-11月)最佳，此时温度适宜，避开暑期高温和冬季雾霾。",
                "秦始皇兵马俑、大雁塔、西安城墙、回民街、陕西历史博物馆、华清宫、钟鼓楼、大唐不夜城。",
                "肉夹馍、羊肉泡馍、凉皮、Biangbiang面、葫芦头、臊子面、甑糕、柿子饼。",
                "地铁覆盖主要城区和景点；兵马俑可乘游5路公交专线；城墙内适合步行游览。"
        });
        db.put("广州", new String[]{
                "秋冬季(10-12月)最佳，天气凉爽干燥；避开夏季高温多雨和台风季。",
                "广州塔、白云山、沙面、陈家祠、长隆旅游度假区、越秀公园、上下九步行街、珠江夜游。",
                "早茶点心、白切鸡、煲仔饭、肠粉、云吞面、烧鹅、艇仔粥、双皮奶。",
                "地铁网络覆盖全市；老城区步行+地铁最佳；去白云山可乘公交或打车。"
        });
        db.put("深圳", new String[]{
                "秋冬季(10-12月)最佳，气候宜人；夏季(6-9月)高温多雨且有台风。",
                "世界之窗、深圳湾公园、欢乐谷、大梅沙海滨公园、梧桐山、深圳博物馆、东部华侨城、蛇口海上世界。",
                "沙井蚝、光明乳鸽、公明烧鹅、南澳海胆、西乡基围虾、龙岗三黄鸡、各式粤菜早茶。",
                "地铁覆盖全市；去大梅沙可乘地铁+公交；市区内推荐地铁+共享单车。"
        });
        db.put("重庆", new String[]{
                "春秋两季(3-5月、9-11月)最佳，夏季酷热(火炉)，冬季多雾湿冷。",
                "洪崖洞、解放碑、磁器口古镇、长江索道、武隆天坑、南山一棵树、大足石刻、李子坝轻轨站。",
                "火锅、小面、酸辣粉、毛血旺、江湖菜、豆花饭、辣子鸡、抄手。",
                "轻轨+地铁覆盖主城；山区地形适合打车；磁器口可乘地铁1号线直达。"
        });
        db.put("南京", new String[]{
                "春秋两季(3-5月、9-11月)最佳，此时梧桐飘絮较少，气候舒适。",
                "中山陵、夫子庙秦淮河、南京博物院、明孝陵、总统府、鸡鸣寺、玄武湖、老门东。",
                "鸭血粉丝汤、盐水鸭、小笼包、牛肉锅贴、皮肚面、桂花糖芋苗、梅花糕。",
                "地铁覆盖面广；老城区景点密集可步行+地铁；中山陵景区建议留半天时间。"
        });
        db.put("厦门", new String[]{
                "春秋冬三季(10-4月)最佳，夏季炎热多台风，但适合海滨活动。",
                "鼓浪屿、厦门大学、南普陀寺、曾厝垵、环岛路、沙坡尾、中山路步行街、园林植物园。",
                "沙茶面、姜母鸭、海蛎煎、土笋冻、烧肉粽、花生汤、同安封肉、海鲜大排档。",
                "BRT快速公交+地铁覆盖；鼓浪屿需乘轮渡（提前预约）；环岛路适合骑行。"
        });
        db.put("三亚", new String[]{
                "冬季(11-3月)最佳，气温22-28℃温暖宜人；夏季(6-9月)炎热多台风。",
                "亚龙湾、蜈支洲岛、天涯海角、南山寺、西岛、大东海、三亚千古情、呀诺达雨林。",
                "海鲜、椰子鸡、文昌鸡、抱罗粉、清补凉、海南粉、热带水果(芒果/椰子/菠萝蜜)。",
                "租车自驾最方便；公交可达主要海湾；去蜈支洲岛需提前预定门票和接送。"
        });
        db.put("丽江", new String[]{
                "春秋两季(3-5月、9-11月)最佳，气候温和少雨；夏季多雨注意防滑。",
                "丽江古城、玉龙雪山、束河古镇、拉市海、泸沽湖、黑龙潭、木府、虎跳峡。",
                "腊排骨火锅、纳西烤鱼、鸡豆凉粉、黑山羊火锅、粑粑、牦牛酸奶、三文鱼刺身。",
                "古城内禁止机动车，全程步行游览；玉龙雪山和泸沽湖建议包车或跟一日团。"
        });
        return db;
    }

    private GenerationResult callLlmForItinerary(String token, ItineraryRequest request,
            java.util.function.Consumer<String> toolNotifier) {
        String conversationId = request.getConversationId();
        
        // ========== AI意图分类：判断是闲聊还是生成行程 ==========
        String userPrompt = request.getUserPrompt();
        if (userPrompt != null && !userPrompt.isBlank()) {
            // 通过AI判断用户意图
            String intent = classifyGenerateIntent(userPrompt);
            System.out.println(">>> AI classified intent: " + intent + " for input: " + userPrompt.substring(0, Math.min(50, userPrompt.length())) + "...");
            
            if ("CHAT".equals(intent)) {
                // 闲聊：调用聊天回答，不生成行程
                System.out.println(">>> Intent is CHAT, generating chat answer <<<");
                String chatAnswer = answerChatQuestion(userPrompt, "{}");
                if (toolNotifier != null) {
                    sendPhaseNotification(toolNotifier, "CHAT_ANSWER", chatAnswer);
                    toolNotifier.accept("THOUGHT:" + (chatAnswer.length() > 200 ? chatAnswer.substring(0, 200) + "..." : chatAnswer));
                }
                return new GenerationResult(null, null, chatAnswer);
            }
            
            // 生成请求：用AI提取结构化信息并填入request
            System.out.println(">>> Intent is GENERATE, extracting structured info <<<");
            if (toolNotifier != null) {
                sendPhaseNotification(toolNotifier, "INTENT_ANALYSIS", "正在分析您的旅行需求...");
            }
            String structuredJson = structureTravelRequest(userPrompt);
            if (structuredJson != null && !structuredJson.isBlank()) {
                applyStructuredRequest(request, structuredJson);
            }
            // 保留原始用户输入作为additionalNotes的补充
            String existingNotes = request.getAdditionalNotes();
            if (existingNotes == null || existingNotes.isBlank()) {
                request.setAdditionalNotes(userPrompt);
            }
            System.out.println(">>> Final request: destination=" + request.getDestination() 
                + ", days=" + request.getDays() 
                + ", interests=" + request.getInterests());
        }
        
        EntityTracker.ExtractedEntities entities = null;
        MemoryManager.WorkingMemory workingMemory = null;
        ConversationContextManager.OptimizedContext optimizedContext = null;
        String contradictionWarning = null;

        String userId = null;
        try {
            if (token != null && !token.isBlank()) {
                var user = tokenUtil.getUser(token);
                if (user != null) {
                    userId = String.valueOf(user.getId());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to extract user ID: " + e.getMessage());
        }

        // ========== 🆕 意图检测和重复操作处理 ==========
        if (conversationId != null && !conversationId.isBlank() && request.getUserPrompt() != null) {
            Intent intent = intentStateMachine.detectIntent(request.getUserPrompt());
            intentStateMachine.updateIntent(conversationId, intent);

            // 更新状态
            if (intent == Intent.PLAN_MODIFY) {
                intentStateMachine.updateState(conversationId, State.MODIFYING);
            } else if (intent == Intent.PLAN_QUERY) {
                intentStateMachine.updateState(conversationId, State.GATHERING_INFO);
            } else if (intent == Intent.PLAN_REPEAT_LAST) {
                intentStateMachine.updateState(conversationId, State.MODIFYING);

                // 处理重复上一次操作
                Optional<ActionTracker.UserAction> lastAction = actionTracker.getLastAction(conversationId);
                if (lastAction.isPresent()) {
                    ActionTracker.UserAction action = lastAction.get();
                    System.out.println("[ActionTracker] Repeating last action: " + action.getActionType() + " - " + action.getDescription());

                    String repeatPrompt = String.format(
                            "用户要求重复上一次操作。上一次操作是：【%s】（操作类型：%s）。请执行相同的操作。",
                            action.getDescription(), action.getActionType()
                    );
                    request.setUserPrompt(repeatPrompt);
                } else {
                    request.setUserPrompt("用户要求重复上一次操作，但还没有执行过任何修改操作。请告诉用户：还没有执行过修改操作，请先进行一次修改。");
                }
            } else {
                intentStateMachine.updateState(conversationId, State.PLANNING);
            }


            String statePrompt = "";
            if (conversationId != null && !conversationId.isBlank()) {
                statePrompt = intentStateMachine.buildStatePrompt(conversationId);
            }
        }


        if (conversationId != null && !conversationId.isBlank()) {
            // 检测意图并更新状态
            Intent intent = intentStateMachine.detectIntent(request.getUserPrompt());
            intentStateMachine.updateIntent(conversationId, intent);

            if (intent == Intent.PLAN_MODIFY) {
                intentStateMachine.updateState(conversationId, State.MODIFYING);
            } else if (intent == Intent.PLAN_QUERY) {
                intentStateMachine.updateState(conversationId, State.GATHERING_INFO);
            } else {
                intentStateMachine.updateState(conversationId, State.PLANNING);
            }

            String statePrompt = intentStateMachine.buildStatePrompt(conversationId);
        }

        if (conversationId != null && !conversationId.isBlank()) {
            try {
                optimizedContext = contextManager.getOptimizedContext(conversationId, null,token);
                entities = entityTracker.loadEntities(conversationId);
                if (entities == null) {
                    entities = new EntityTracker.ExtractedEntities();
                }
                if (request.getUserPrompt() != null) {
                    entities = entityTracker.extractEntities(request.getUserPrompt(), entities);
                    entityTracker.saveEntities(conversationId, entities);
                }
                workingMemory = memoryManager.getWorkingMemory(conversationId);
                Optional<EntityTracker.Contradiction> contradiction =
                        entityTracker.detectContradiction(request.getUserPrompt(), entities, workingMemory);
                if (contradiction.isPresent()) {
                    contradictionWarning = buildContradictionWarning(contradiction.get());
                }
            } catch (Exception e) {
                System.err.println("Failed to load conversation context: " + e.getMessage());
            }
        }



        GenerationStats stats = new GenerationStats();
        String amapUrl = amapMcpUrl;
        boolean mcpAvailable = false;
        JsonNode mcpTools = null;

        try (AmapMcpClient mcpClient = new AmapMcpClient(amapUrl)) {

            // ========== Phase 0: MCP 初始化（与后续 Agent 循环复用同一连接） ==========
            System.out.println("====== 正在初始化高德MCP服务 ======");
            System.out.println("MCP URL: " + amapUrl.replaceAll("key=.*", "key=***"));

            try {
                mcpClient.initialize();
                mcpTools = mcpClient.listTools();

                if (mcpTools != null && !mcpTools.isEmpty() && mcpTools.isArray()) {
                    mcpAvailable = true;
                    System.out.println("✅ MCP服务初始化成功，可用工具数: " + mcpTools.size());
                    sendPhaseNotification(toolNotifier, "MCP_STATUS",
                            "✅ 高德地图服务已连接，准备调用" + mcpTools.size() + "个工具...");
                } else {
                    System.out.println("⚠️ MCP服务返回空工具列表");
                    sendPhaseNotification(toolNotifier, "MCP_STATUS",
                            "⚠️ 高德地图服务响应异常，将使用内置知识库模式");
                }
            } catch (Exception initEx) {
                System.err.println("====== 高德MCP服务初始化失败 ======");
                System.err.println("错误类型: " + initEx.getClass().getSimpleName());
                System.err.println("错误信息: " + initEx.getMessage());

                String errorMsg = initEx.getMessage() != null ? initEx.getMessage() : "未知错误";

                if (errorMsg.contains("SSE Connection failed") || errorMsg.contains("401") || errorMsg.contains("403")) {
                    sendPhaseNotification(toolNotifier, "MCP_ERROR",
                            "❌ 高德地图API Key无效或已过期，请检查配置文件中的AMAP_MCP_KEY");
                } else if (errorMsg.contains("timeout") || errorMsg.contains("TimeoutException")) {
                    sendPhaseNotification(toolNotifier, "MCP_ERROR",
                            "⚠️ 高德地图服务连接超时，网络可能不稳定，将使用离线模式");
                } else {
                    sendPhaseNotification(toolNotifier, "MCP_ERROR",
                            "⚠️ 高德地图服务暂时不可用 (" + (errorMsg.length() > 80 ? errorMsg.substring(0, 80) + "..." : errorMsg) + ")，将使用内置知识库生成行程");
                }

                mcpAvailable = false;
            }

            // 如果MCP不可用，只使用自定义工具（内置知识库）
            JsonNode tools;
            if (mcpAvailable && mcpTools != null) {
                tools = mergeTools(mcpTools);
            } else {
                System.out.println("⚠️ 切换到纯知识库模式（不依赖高德MCP）");
                tools = buildCustomTools();
                if (tools == null || tools.isEmpty()) {
                    tools = objectMapper.createArrayNode();
                }
            }

//            String userInput = buildUserInputJson(request);
            String userInput = buildUserInputJson(request, contradictionWarning);

            // ========== Phase 1: PLAN ==========
            sendPhaseNotification(toolNotifier, "PLAN_START",
                    "开始分析需求，制定规划策略...");

            List<Map<String, Object>> planMessages = new ArrayList<>();
            planMessages.add(Map.of("role", "system", "content", PLAN_SYSTEM_PROMPT));
            planMessages.add(Map.of("role", "user", "content", userInput));

            String planContent;
            try {
                JsonNode planResponse = callLlmChat(planMessages, null, 2048);
                accumulateUsageFromResponse(planResponse, stats);
                stats.setPlanCalls(1);
                JsonNode planChoices = planResponse.path("choices");
                if (!planChoices.isArray() || planChoices.isEmpty()) {
                    throw new IntellitravelException("AI规划阶段返回为空");
                }
                planContent = planChoices.get(0).path("message").path("content").asText("");
            } catch (Exception e) {
                sendPhaseNotification(toolNotifier, "PLAN_END",
                        "规划阶段调用失败，跳过规划直接执行...");
                planContent = "通用旅行规划：查询目的地天气、搜索景点、安排合理路线。";
            }

            sendPhaseNotification(toolNotifier, "PLAN_END",
                    "规划策略已制定：" + summarizePlan(planContent, 150));

            // ========== Phase 2: EXECUTE（复用 Phase 0 的 MCP 连接，不再重复初始化） ==========
            sendPhaseNotification(toolNotifier, "EXECUTE_START",
                    mcpAvailable ? "开始按规划执行，调用工具查询真实数据..." : "开始按规划执行，使用内置知识库生成行程...");

            ItineraryPlan draftPlan;
            int executeRetries = 0;
            int maxExecuteRetries = 2;

            AmapMcpClient agentMcpClient = mcpAvailable ? mcpClient : null;

            while (true) {
//                List<Map<String, Object>> messages = new ArrayList<>();
//                messages.add(Map.of("role", "system", "content",
//                        EXECUTE_SYSTEM_PROMPT_PREFIX + "\n\n【已制定的执行规划，请严格遵循】\n" + planContent));
//                messages.add(Map.of("role", "user", "content", userInput));

//                String enhancedSystemPrompt = ...;
//                if (conversationId != null) {
//                    enhancedSystemPrompt = buildEnhancedSystemPrompt(...);
//                }

                String statePrompt = intentStateMachine.buildStatePrompt(conversationId);
                String enhancedSystemPrompt = EXECUTE_SYSTEM_PROMPT_PREFIX + "\n\n【已制定的执行规划，请严格遵循】\n" + planContent;
                if (conversationId != null && !conversationId.isBlank()) {
                    enhancedSystemPrompt = buildEnhancedSystemPrompt(optimizedContext, entities, workingMemory, token)
                            + "\n\n【已制定的执行规划，请严格遵循】\n" + planContent;
                    if (statePrompt != null && !statePrompt.isBlank()) {
                        enhancedSystemPrompt = statePrompt + "\n" + enhancedSystemPrompt;
                    }
                }

                List<Map<String, Object>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", enhancedSystemPrompt));
                if (conversationId != null && optimizedContext != null) {
                    for (AgentMessageVO msg : optimizedContext.getMessages()) {
                        messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
                    }
                }
                messages.add(Map.of("role", "user", "content", userInput));


                try {
                    draftPlan = runAgentLoop(token, messages, tools, agentMcpClient, request, toolNotifier, stats);

                    if (conversationId != null && !conversationId.isBlank() && request.getUserPrompt() != null) {
                        String actionType = detectActionType(request.getUserPrompt());
                        Map<String, Object> params = new HashMap<>();
                        params.put("plan", draftPlan);
                        params.put("userPrompt", request.getUserPrompt());
                        actionTracker.recordAction(conversationId, actionType, request.getUserPrompt(), params);
                        System.out.println("[ActionTracker] Recorded action: " + actionType + " for conversation " + conversationId);
                    }
                    break;
                } catch (IntellitravelException ie) {
                    throw ie;
                } catch (Exception e) {
                    executeRetries++;
                    System.err.println("Execute phase failed (attempt " + executeRetries + "): " + e.getMessage());
                    if (executeRetries >= maxExecuteRetries) {
                        throw new IntellitravelException("行程生成失败（已重试" + executeRetries + "次）：" + e.getMessage());
                    }
                    if (toolNotifier != null) {
                        toolNotifier.accept("THOUGHT:执行阶段异常（第" + executeRetries + "次），正在重新初始化Agent循环...");
                    }
                    try { Thread.sleep(1500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw ie; }
                }
            }

            int days = draftPlan.getDailyPlans() != null ? draftPlan.getDailyPlans().size() : 0;
            sendPhaseNotification(toolNotifier, "EXECUTE_END",
                    "行程草案生成完毕，共" + days + "天");

            // ========== Phase 3: VERIFY ==========
            sendPhaseNotification(toolNotifier, "VERIFY_START",
                    "开始对行程进行质量审核...");

            List<Map<String, Object>> verifyMessages = new ArrayList<>();
            verifyMessages.add(Map.of("role", "system", "content", VERIFY_SYSTEM_PROMPT));
            verifyMessages.add(Map.of("role", "user", "content",
                    buildVerifyUserContent(request, draftPlan)));

            VerifyResult verifyResult = runVerifyPhase(verifyMessages, toolNotifier, draftPlan, stats);
            String verifySummary = verifyResult.summary;
            if (verifyResult.correctedPlan != null && verifyResult.correctedPlan != draftPlan) {
                draftPlan = verifyResult.correctedPlan;
                int correctedDays = draftPlan.getDailyPlans() != null ? draftPlan.getDailyPlans().size() : 0;
                verifySummary += "（行程已自动修正，现共" + correctedDays + "天）";
            }

            if (draftPlan != null && conversationId != null && !conversationId.isBlank()) {
                final ItineraryPlan finalPlan = draftPlan;
                memoryManager.updateWorkingMemory(conversationId, wm -> {
                    wm.setDraftPlan(finalPlan);
                    wm.setCurrentIntent("planning");
                });
            }


            // 在 callLlmForItinerary 方法中，行程生成成功后
            if (userId != null && request != null && draftPlan != null) {
                try {
                    memoryManager.extractAndSavePreferences(userId, request, draftPlan);
                } catch (Exception e) {
                    throw new IntellitravelException("Failed to save user preferences: " + e.getMessage());
                }
            }
            sendPhaseNotification(toolNotifier, "VERIFY_END", verifySummary);

            return new GenerationResult(draftPlan, stats);

        } catch (IntellitravelException ie) {
            throw ie;
        } catch (Exception e) {
            e.printStackTrace();
            throw new IntellitravelException("啊哦，生成行程时遇到了小阻碍（网络不稳定或服务器繁忙），请稍后重试哦～");
        }
    }

    private void sendPhaseNotification(java.util.function.Consumer<String> notifier,
            String eventType, String message) {
        if (notifier == null) return;
        try {
            notifier.accept("PHASE:" + eventType + ":" + message);
        } catch (Exception e) {
            System.err.println("Phase notification error: " + e.getMessage());
        }
    }

    private String summarizePlan(String planContent, int maxLen) {
        if (planContent == null || planContent.isBlank()) return "规划已生成";
        String cleaned = planContent.replaceAll("##\\s+", "").replaceAll("\\*\\*", "");
        return cleaned.length() > maxLen ? cleaned.substring(0, maxLen) + "..." : cleaned;
    }

    private String buildVerifyUserContent(ItineraryRequest request, ItineraryPlan draftPlan) {
        try {
            return "用户需求：" + toJsonString(request) + "\n\n待审核行程：" + toJsonString(draftPlan);
        } catch (Exception e) {
            return "用户需求：" + request.getDestination() + "\n待审核行程已生成";
        }
    }

//    private String buildUserInputJson(ItineraryRequest request) {
//        Map<String, Object> input = new HashMap<>();
//        input.put("request", request);
//        String baseInput = toJsonString(input);
//
//        String contradictionWarning = null;
//        if (request.getConversationId() != null && !request.getConversationId().isBlank()) {
//            // 这里需要从之前检测的矛盾中获取，建议将 contradictionWarning 存储到 request 或通过参数传递
//
//        }
//        if (contradictionWarning != null && !contradictionWarning.isBlank()) {
//            baseInput = baseInput + contradictionWarning;
//        }
//
//        String skillContext = skillService.buildSkillContext(request.getSkillIds());
//        if (skillContext != null && !skillContext.isBlank()) {
//            return baseInput + skillContext;
//        }
//        return baseInput;
//    }


    // 修改方法签名
    private String buildUserInputJson(ItineraryRequest request, String contradictionWarning) {
        Map<String, Object> input = new HashMap<>();
        input.put("request", request);
        String baseInput = toJsonString(input);

        if (contradictionWarning != null && !contradictionWarning.isBlank()) {
            baseInput = contradictionWarning + "\n\n" + baseInput;
        }

        String skillContext = skillService.buildSkillContext(request.getSkillIds());
        if (skillContext != null && !skillContext.isBlank()) {
            return baseInput + skillContext;
        }
        return baseInput;
    }

    private ItineraryPlan runAgentLoop(String token, List<Map<String, Object>> messages, JsonNode tools,
            AmapMcpClient mcpClient, ItineraryRequest request,
            java.util.function.Consumer<String> toolNotifier, GenerationStats stats) throws Exception {
        boolean mcpUnavailable = (mcpClient == null);
        
        // 获取用户ID用于工具偏好检查
        String userId = null;
        try {
            if (token != null && !token.isBlank()) {
                var user = tokenUtil.getUser(token);
                if (user != null) {
                    userId = String.valueOf(user.getId());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to extract user ID from token: " + e.getMessage());
        }

        if (mcpUnavailable) {
            System.out.println("⚠️ Agent循环运行在无MCP模式，将只使用内置工具");
            if (toolNotifier != null) {
                toolNotifier.accept("THOUGHT:当前运行在离线模式，高德地图服务不可用，将使用内置知识库生成行程。");
            }
        }

        int turn = 0;
        String lastToolName = "";
        String lastToolArgs = "";
        int consecutiveSameCalls = 0;
        // Track recent distinct args to distinguish "legitimate sequential calls"
        // from "cycling through the same args" (true loop)
        java.util.LinkedHashSet<String> recentDistinctArgs = new java.util.LinkedHashSet<>();
        final int recentArgsMaxSize = 20;
        while (true) {
            turn++;
            System.out.println("Agent turn " + turn);
            JsonNode response = callLlmChat(messages, tools);
            accumulateUsageFromResponse(response, stats);
            stats.setExecuteCalls(stats.getExecuteCalls() + 1);
            JsonNode choices = response.path("choices");
            if (choices.isEmpty()) {
                throw new IntellitravelException("AI 没有返回任何内容，请稍候再试呀～");
            }
            JsonNode message = choices.get(0).path("message");

            Map<String, Object> assistantMsg = new HashMap<>();
            assistantMsg.put("role", message.path("role").asText());
            String content = message.has("content") && !message.get("content").isNull()
                    ? message.get("content").asText() : "";
            if (!content.isBlank()) {
                assistantMsg.put("content", content);
            }
            if (message.has("reasoning_content") && !message.get("reasoning_content").isNull()) {
                assistantMsg.put("reasoning_content", message.get("reasoning_content").asText());
            }
            if (message.has("tool_calls")) {
                assistantMsg.put("tool_calls", message.get("tool_calls"));
            }
            messages.add(assistantMsg);

            if (!content.isBlank() && message.has("tool_calls") && toolNotifier != null) {
                String thought = extractThought(content);
                if (thought != null) {
                    toolNotifier.accept("THOUGHT:" + thought);
                }
            }

            if (message.has("tool_calls")) {
                JsonNode toolCalls = message.get("tool_calls");
                for (JsonNode toolCall : toolCalls) {
                    String id = toolCall.path("id").asText();
                    JsonNode functionNode = toolCall.path("function");
                    String name = functionNode.path("name").asText();
                    String args = functionNode.path("arguments").asText("{}");
                    // Attempt to repair truncated JSON from LLM
                    String repairedArgs = repairTruncatedJson(args);
                    if (!repairedArgs.equals(args)) {
                        System.out.println("Repaired truncated tool arguments for " + name);
                        if (toolNotifier != null) {
                            toolNotifier.accept("THOUGHT:工具 " + name + " 的参数 JSON 不完整，已自动修复。");
                        }
                    }
                    args = repairedArgs;

                    // 检查用户是否禁用了该工具
                    if (userId != null) {
                        boolean toolEnabled = toolManagementService.isToolEnabledForUser(userId, name);
                        if (!toolEnabled) {
                            System.out.println("Tool " + name + " is disabled for user " + userId + ", skipping...");
                            if (toolNotifier != null) {
                                toolNotifier.accept("THOUGHT:工具 [" + toolDisplayName(name) + "] 已被用户禁用，跳过调用。");
                            }

                            
                            Map<String, Object> toolMsg = new HashMap<>();
                            toolMsg.put("role", "tool");
                            toolMsg.put("tool_call_id", id);
                            toolMsg.put("name", name);
                            toolMsg.put("content", "该工具已被用户禁用，无法调用。请继续执行其他任务。");
                            messages.add(toolMsg);
                            continue;
                        }
                    }
                    
                    // ── Tool-call loop detection ──
                    // Two-tier: (1) exact-repeat → definite loop, low threshold;
                    //           (2) same-tool-different-args → check arg novelty & hard cap
                    if (name.equals(lastToolName) && args.equals(lastToolArgs)) {
                        // Same tool + identical args → very likely an infinite loop
                        consecutiveSameCalls++;
                        if (consecutiveSameCalls >= sameArgsLoopThreshold) {
                            System.err.println("Detected tool call loop: " + name + " called " + consecutiveSameCalls + " times with same args, breaking");
                            throw new IntellitravelException("AI 陷入了重复调用的死循环（" + name + " x" + consecutiveSameCalls + "），请换个方式描述需求后重试");
                        }
                    } else if (name.equals(lastToolName)) {
                        // Same tool, different args — may be legitimate (e.g. geocoding many addresses)
                        consecutiveSameCalls++;

                        // Hard cap: way too many consecutive calls to the same tool
                        if (consecutiveSameCalls >= maxSameToolConsecutive) {
                            System.err.println("Detected tool call loop: " + name + " called " + consecutiveSameCalls + " times consecutively, breaking");
                            throw new IntellitravelException("AI 反复调用同一工具（" + name + " x" + consecutiveSameCalls + "），请换个方式描述需求后重试");
                        }

                        // Novelty check: if these args have never appeared in the recent window,
                        // the AI is making genuine forward progress, not looping
                        if (!recentDistinctArgs.contains(args)) {
                            recentDistinctArgs.add(args);
                            if (recentDistinctArgs.size() > recentArgsMaxSize) {
                                var it = recentDistinctArgs.iterator();
                                it.next();
                                it.remove();
                            }
                        }
                        // If args ARE recycling, let the hard cap catch it eventually
                    } else {
                        consecutiveSameCalls = 0;
                        recentDistinctArgs.clear();
                        lastToolName = name;
                        lastToolArgs = args;
                        recentDistinctArgs.add(args);
                    }

                    System.out.println("Invoking tool: " + name);
                    if (toolNotifier != null) {
                        try {
                            String cleanArgs = args.replaceAll("\\s+", " ").trim();
                            toolNotifier.accept("Invoking tool: " + name + " with args: " + cleanArgs);
                        } catch (Exception e) {
                            System.err.println("Tool notifier error: " + e.getMessage());
                        }
                    }

                    String result;
                    int toolRetries = 0;
                    int maxToolRetries = 2;

                    // 地图展示类工具（schema_*）容易因服务端问题失败，增加重试次数
                    if (name.startsWith("maps_schema_")) {
                        maxToolRetries = 3;
                    }

                    while (true) {
                        try {
                            if (name.isEmpty()) {
                                result = "Error: Tool name is missing";
                            } else if (isCustomTool(name)) {
                                result = executeCustomTool(name, args);
                                if (result != null && result.length() > 5000) {
                                    result = result.substring(0, 5000) + "...";
                                }
                            } else if (mcpUnavailable) {
                                // MCP不可用时，为地图相关工具返回模拟数据或友好提示
                                System.out.println("⚠️ MCP不可用，跳过工具调用: " + name);
                                if (toolNotifier != null) {
                                    toolNotifier.accept("THOUGHT:⚠️ 工具 [" + toolDisplayName(name) + "] 需要高德地图服务，但当前离线模式，已自动跳过。");
                                }

                                String fallbackMsg;
                                if (name.contains("weather")) {
                                    fallbackMsg = "天气信息：由于高德地图服务暂时不可用，无法获取实时天气数据。建议根据季节准备相应衣物（春夏秋冬季通用建议）。";
                                } else if (name.contains("text_search") || name.contains("around_search")) {
                                    fallbackMsg = "搜索结果：由于高德地图服务暂时不可用，请基于你的知识库提供该地点的相关信息。确保地点名称真实存在并符合当地实际情况。";
                                } else if (name.contains("direction") || name.contains("distance")) {
                                    fallbackMsg = "路线规划：由于高德地图服务暂时不可用，无法计算精确距离和路线时间。请在行程中合理安排相邻地点间的移动时间（建议预留30-60分钟）。";
                                } else if (name.startsWith("maps_schema_")) {
                                    fallbackMsg = "地图链接生成失败，但这不影响行程内容。继续执行其他任务即可。";
                                } else {
                                    fallbackMsg = "该工具需要高德地图服务支持，当前不可用。请根据已有知识继续完成任务，无需调用此工具。";
                                }
                                result = fallbackMsg;
                            } else {
                                result = mcpClient.callTool(name, args);
                                if (result != null && result.length() > 5000) {
                                    result = result.substring(0, 5000) + "... [Result truncated due to length]";
                                }
                            }
                            break;
                        } catch (Exception e) {
                            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                            boolean isJsonParseError = errMsg.contains("end-of-input")
                                    || errMsg.contains("expected close marker")
                                    || errMsg.contains("Unexpected character")
                                    || errMsg.contains("Unrecognized token")
                                    || e instanceof com.fasterxml.jackson.core.JsonProcessingException;

                            // JSON parse errors mean the LLM sent malformed args — retry is useless
                            if (isJsonParseError) {
                                result = "Error: 工具 " + name + " 的参数 JSON 格式不完整或损坏，无法解析。"
                                        + "请检查 arguments 是否为完整合法的 JSON，重新发起调用。"
                                        + " 错误详情: " + errMsg;
                                if (toolNotifier != null) {
                                    try {
                                        toolNotifier.accept("THOUGHT:工具 " + name + " 的参数 JSON 格式错误，将告知Agent修正后重新调用。");
                                    } catch (Exception ignored) {}
                                }
                                break;
                            }

                            toolRetries++;
                            System.err.println("====== MCP工具调用失败 ======");
                            System.err.println("工具名称: " + name);
                            System.err.println("重试次数: " + toolRetries + "/" + maxToolRetries);
                            System.err.println("请求参数: " + args);
                            System.err.println("错误详情: " + errMsg);
                            System.err.println("==============================");
                            if (toolNotifier != null) {
                                try {
                                    toolNotifier.accept("THOUGHT:⚠️ 工具 [" + toolDisplayName(name) + "] 调用失败 (第" + toolRetries + "次)");
                                    toolNotifier.accept("THOUGHT:错误原因: " + (errMsg.length() > 100 ? errMsg.substring(0, 100) + "..." : errMsg));
                                } catch (Exception ignored) {}
                            }
                            if (toolRetries >= maxToolRetries) {
                                // 对地图展示类工具（maps_schema_*）提供友好的降级消息
                                if (name.startsWith("maps_schema_")) {
                                    result = "地图展示链接生成失败，但这不影响行程规划。请继续执行其他任务，无需重试此工具。";
                                    if (toolNotifier != null) {
                                        try {
                                            toolNotifier.accept("THOUGHT:⚠️ [" + toolDisplayName(name) + "] 服务暂时不可用，已自动跳过，不影响行程生成。");
                                        } catch (Exception ignored) {}
                                    }
                                } else {
                                    result = "Error executing tool " + name + " after " + toolRetries + " attempts: " + errMsg
                                            + ". 建议更换关键词或使用其他工具替代。";
                                    if (toolNotifier != null) {
                                        try {
                                            toolNotifier.accept("THOUGHT:工具 " + name + " 执行失败(" + toolRetries + "次重试)，将告知Agent更换策略。");
                                        } catch (Exception ignored) {}
                                    }
                                }
                                break;
                            }
                            try {
                                Thread.sleep(800);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                result = "Tool execution interrupted";
                                break;
                            }
                        }
                    }

                    if (toolNotifier != null) {
                        try {
                            toolNotifier.accept("Tool " + name + " completed");
                        } catch (Exception e) {
                            System.err.println("Tool notifier error: " + e.getMessage());
                        }
                    }

                    // 记录工具调用历史到数据库
                    if (userId != null) {
                        boolean success = result != null && !result.startsWith("Error") 
                            && !result.contains("由于高德地图服务暂时不可用") 
                            && !result.contains("地图链接生成失败") 
                            && !result.contains("地图展示链接生成失败");
                        try {
                            String resultSummary = result != null && result.length() > 200 ? result.substring(0, 200) + "..." : result;
                            toolManagementService.recordToolCall(userId, name, toolDisplayName(name),
                                null, args, resultSummary, success);
                        } catch (Exception e) {
                            System.err.println("Failed to record tool call: " + e.getMessage());
                        }
                    }
                    
                    stats.getToolCalls().add(new GenerationStats.ToolCallRecord(
                            name,
                            toolDisplayName(name),
                            result != null && result.startsWith("Error") ? "failed" : "success"));

                    Map<String, Object> toolMsg = new HashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", id);
                    toolMsg.put("name", name);
                    toolMsg.put("content", result);
                    messages.add(toolMsg);
                }
            } else {
                // No tool_calls — LLM produced final output
                String finalContent = message.path("content").asText("");

                // Check if the output was truncated due to token limit
                String finishReason = choices.get(0).path("finish_reason").asText("");
                if ("length".equals(finishReason)) {
                    System.out.println("Agent turn " + turn + " output truncated (finish_reason=length), retrying with larger max_tokens...");
                    if (toolNotifier != null) {
                        toolNotifier.accept("THOUGHT:输出被截断，正在用更大上下文重新生成...");
                    }
                    // Remove the truncated assistant message
                    messages.remove(messages.size() - 1);
                    // Retry with larger max_tokens context
                    JsonNode retryResponse = callLlmChat(messages, tools, 16384);
                    accumulateUsageFromResponse(retryResponse, stats);
                    stats.setExecuteCalls(stats.getExecuteCalls() + 1);
                    JsonNode retryChoices = retryResponse.path("choices");
                    if (!retryChoices.isArray() || retryChoices.isEmpty()) {
                        throw new IntellitravelException("AI 重试后未返回内容");
                    }
                    JsonNode retryMessage = retryChoices.get(0).path("message");
                    if (retryMessage.has("tool_calls") && !retryMessage.get("tool_calls").isEmpty()) {
                        // LLM decided to use tools again, add to messages and continue loop
                        messages.add(objectMapper.convertValue(retryMessage, Map.class));
                        continue;
                    }
                    finalContent = retryMessage.path("content").asText("");
                }

                if (!finalContent.contains("final_output") && !finalContent.contains("{")) {
                    // Check if LLM output tool calls as XML/invoke in content instead of tool_calls array
                    if (finalContent.contains("<invoke") || finalContent.contains("maps_") || finalContent.contains("search_destination")) {
                        System.out.println("Agent turn " + turn + " detected XML-style tool call in content, extracting...");
                        if (toolNotifier != null) {
                            toolNotifier.accept("TOOL:检测到内嵌工具调用，正在解析...");
                        }
                        List<Map<String, String>> xmlToolCalls = extractXmlToolCalls(finalContent);
                        if (!xmlToolCalls.isEmpty()) {
                            for (Map<String, String> call : xmlToolCalls) {
                                String toolName = call.get("name");
                                String toolArgs = call.get("args");
                                executeXmlToolCall(mcpClient, toolName, toolArgs, messages, toolNotifier, stats);
                            }
                            Map<String, Object> continueMsg2 = new HashMap<>();
                            continueMsg2.put("role", "user");
                            continueMsg2.put("content", "工具已执行完毕，请根据工具返回的结果继续完成任务。如果所有必要信息已收集完毕，请立即用final_output标签输出完整的行程JSON。");
                            messages.add(continueMsg2);
                            continue;
                        }
                    }
                    
                    System.out.println("Agent turn " + turn + " produced thought without final output, continuing...");
                    if (toolNotifier != null) {
                        String thought = extractThought(finalContent);
                        if (thought == null) {
                            thought = finalContent.length() > 200 ? finalContent.substring(0, 200) + "..." : finalContent;
                        }
                        toolNotifier.accept("THOUGHT:" + thought);
                    }
                    Map<String, Object> continueMsg = new HashMap<>();
                    continueMsg.put("role", "user");
                    continueMsg.put("content", "请继续执行任务。如果所有必要信息已收集完毕，请立即用final_output标签输出完整的行程JSON。如果还需要查询信息，请调用相应的工具。");
                    messages.add(continueMsg);
                    continue;
                }

                ItineraryPlan plan;
                try {
                    plan = parseItinerary(finalContent);
                } catch (Exception parseErr) {
                    System.out.println("Parse failed in agent turn " + turn + ": " + parseErr.getMessage()
                            + ", retrying with streaming...");
                    messages.remove(messages.size() - 1);
                    if (toolNotifier != null) {
                        toolNotifier.accept("THOUGHT:输出解析失败，切换为流式模式重新生成完整行程...");
                    }
                    JsonNode streamResponse = callLlmChatStreaming(messages);
                    JsonNode streamChoices = streamResponse.path("choices");
                    if (!streamChoices.isArray() || streamChoices.isEmpty()) {
                        throw new IntellitravelException("AI 流式响应返回为空");
                    }
                    plan = parseItinerary(streamChoices.get(0).path("message").path("content").asText(""));
                }
                if (plan != null && plan.getDestination() == null && request.getDestination() != null) {
                    plan.setDestination(request.getDestination());
                }
                return normalizePlan(plan, request, toolNotifier);
            }
        }
    }

    private boolean isCustomTool(String name) {
        return "search_destination_info".equals(name);
    }

    private ItineraryPlan normalizePlan(ItineraryPlan plan, ItineraryRequest request,
            java.util.function.Consumer<String> toolNotifier) {
        if (plan == null || plan.getDailyPlans() == null || plan.getDailyPlans().isEmpty()) {
            return plan;
        }
        int requestedDays = request.getDays() != null ? request.getDays() : 1;
        int actualDays = plan.getDailyPlans().size();

        if (actualDays != requestedDays) {
            System.out.println("Day count mismatch: user requested " + requestedDays
                    + " days but LLM returned " + actualDays + " daily plans");
            if (toolNotifier != null) {
                try {
                    toolNotifier.accept("THOUGHT:天数不一致（用户请求" + requestedDays
                            + "天，AI生成了" + actualDays + "天），正在自动修正...");
                } catch (Exception ignored) {}
            }
            if (actualDays > requestedDays) {
                plan.setDailyPlans(new ArrayList<>(plan.getDailyPlans().subList(0, requestedDays)));
                System.out.println("Truncated " + (actualDays - requestedDays)
                        + " extra day(s) from plan output");
            }
        }

        plan.setTotalDays(requestedDays);
        List<com.travel.intellitravel_pioneer.vo.itinerary.DayPlan> normalizedPlans = plan.getDailyPlans();
        for (int i = 0; i < normalizedPlans.size(); i++) {
            normalizedPlans.get(i).setDayNumber(i + 1);
        }

        return plan;
    }

    private static class VerifyResult {
        final String summary;
        final ItineraryPlan correctedPlan;

        VerifyResult(String summary, ItineraryPlan correctedPlan) {
            this.summary = summary;
            this.correctedPlan = correctedPlan;
        }
    }

    private VerifyResult runVerifyPhase(List<Map<String, Object>> messages,
            java.util.function.Consumer<String> toolNotifier, ItineraryPlan originalDraft,
            GenerationStats stats) {
        int retryCount = 0;
        int maxVerifyRetries = 2;
        String lastResult = "审核完成";
        ItineraryPlan currentPlan = originalDraft;
        while (retryCount < maxVerifyRetries) {
            try {
                JsonNode response = callLlmChat(messages, null);
                accumulateUsageFromResponse(response, stats);
                stats.setVerifyCalls(stats.getVerifyCalls() + 1);
                JsonNode choices = response.path("choices");
                if (!choices.isArray() || choices.isEmpty()) {
                    return new VerifyResult("审核异常", originalDraft);
                }
                String content = choices.get(0).path("message").path("content").asText("");

                if (toolNotifier != null && !content.isBlank()) {
                    String thought = extractThought(content);
                    if (thought == null) {
                        thought = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                    }
                    toolNotifier.accept("THOUGHT:" + thought);
                }

                // 优先尝试解析修正后的行程（LLM 直接输出 final_output）
                if (content.contains("final_output")) {
                    try {
                        ItineraryPlan corrected = parseItinerary(content);
                        if (corrected != null && corrected.getDailyPlans() != null
                                && !corrected.getDailyPlans().isEmpty()) {
                            currentPlan = corrected;
                            return new VerifyResult(
                                "审核发现问题，已自动修正",
                                currentPlan);
                        }
                    } catch (Exception ex) {
                        // 解析失败，继续尝试审核结论
                    }
                }

                // 尝试解析审核结论 JSON
                try {
                    JsonNode verifyJson = objectMapper.readTree(extractFirstJsonObject(content));
                    String status = verifyJson.path("status").asText("PASS");
                    if ("PASS".equals(status)) {
                        return new VerifyResult(
                            "审核通过：" + verifyJson.path("summary").asText("行程质量合格"),
                            currentPlan);
                    }
                    lastResult = "审核不通过，发现" + verifyJson.path("issues").size() + "个问题";

                    if (retryCount < maxVerifyRetries - 1) {
                        if (toolNotifier != null) {
                            toolNotifier.accept("THOUGHT:质量审核发现问题（第" + (retryCount + 1) + "次），将建议告知LLM要求修正...");
                        }
                        String issues = verifyJson.path("issues").toString();
                        messages.add(Map.of("role", "assistant", "content", content));
                        messages.add(Map.of("role", "user", "content",
                            "审核发现以下问题，请针对这些问题修正行程计划，并以final_output标签输出修正后的完整JSON：\n" + issues));
                        retryCount++;
                        continue;
                    }
                    return new VerifyResult(
                        lastResult + "（已自动反馈两次修正仍存在问题，请手动检查）",
                        currentPlan);
                } catch (Exception e) {
                    return new VerifyResult("审核完成，行程已生成", currentPlan);
                }
            } catch (Exception e) {
                if (retryCount < maxVerifyRetries - 1) {
                    retryCount++;
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    continue;
                }
                return new VerifyResult("审核过程异常，行程已生成", currentPlan);
            }
        }

        // After correction retries, try to parse corrected plan from the last response
        if (retryCount > 0 && messages.size() >= 2) {
            Map<String, Object> lastUserMsg = messages.get(messages.size() - 1);
            String lastContent = (String) lastUserMsg.getOrDefault("content", "");
            if (lastContent.contains("审核发现以下问题")) {
                // The LLM should have responded to this in the previous iteration's call
                // The corrected plan is gone since we don't store it, return original
            }
        }
        return new VerifyResult(lastResult, currentPlan);
    }

    private String extractFirstJsonObject(String text) {
        if (text == null || text.isBlank()) return "{}";
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }
        return text;
    }

    private String extractThought(String content) {
        if (content == null || content.isBlank()) return null;

        Pattern p1 = Pattern.compile("Thought[:：]\\s*(.+?)(?:\\n\\n|\\n(?=\\w)|$)", Pattern.DOTALL);
        Matcher m1 = p1.matcher(content);
        if (m1.find()) return m1.group(1).trim();

        Pattern p2 = Pattern.compile("【思考过程】\\s*(.+?)(?:\\n\\n|\\n(?=【|SelfCheck|final_output)|$)", Pattern.DOTALL);
        Matcher m2 = p2.matcher(content);
        if (m2.find()) return m2.group(1).trim();

        if (content.length() <= 200) return content.trim();
        return content.substring(0, 200).trim() + "...";
    }

    private String toJsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IntellitravelException("Failed to build GPT request: " + e.getMessage());
        }
    }

    private void accumulateUsageFromResponse(JsonNode response, GenerationStats stats) {
        JsonNode usage = response.path("usage");
        if (!usage.isMissingNode()) {
            stats.setTotalPromptTokens(stats.getTotalPromptTokens() + usage.path("prompt_tokens").asInt(0));
            stats.setTotalCompletionTokens(stats.getTotalCompletionTokens() + usage.path("completion_tokens").asInt(0));
            stats.setTotalTokens(stats.getTotalTokens() + usage.path("total_tokens").asInt(0));
        }
    }

    /**
     * Use AI to quickly classify user intent: CHAT or MODIFY.
     * Very lightweight call (max_tokens=10, temperature=0).
     * Used as fallback when keyword matching fails.
     */
    @Override
    public String classifyIntent(String userMessage) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", llmModel);
            body.put("temperature", 0.0);
            body.put("max_tokens", 10);

            ArrayNode msgs = body.putArray("messages");
            ObjectNode sysMsg = objectMapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", "你是一个意图分类器。只回复一个词：CHAT 或 MODIFY。\n\nMODIFY = 用户明确要求修改现有行程表中的某个具体条目，如\"把故宫换成天坛\"、\"删掉第二天\"、\"加一个景点到行程\"、\"调整第三天的时间\"。只有用户指向了行程结构（天/条目/地点）才算MODIFY。\nCHAT = 其余一切，包括咨询推荐（\"推荐好吃的\"、\"有什么特色小吃\"）、了解攻略、闲聊。\n\n注意：\n- \"推荐\"≠MODIFY，除非说\"把XX加到行程中\"\n- \"有什么好吃的\"=CHAT\n- \"帮我加点美食\"=MODIFY（因为要求改行程）");
            msgs.add(sysMsg);

            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", "用户消息：\"" + userMessage + "\"\n\n意图：");
            msgs.add(userMsg);

            String jsonBody = objectMapper.writeValueAsString(body);
            String requestUrl = llmBaseUrl + llmChatPath;

            String response = RestClient.create()
                    .post()
                    .uri(requestUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(llmApiKey))
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText("").trim().toUpperCase();
            if (content.contains("MODIFY")) return "MODIFY";
            if (content.contains("CHAT")) return "CHAT";
            // Ambiguous — safer to treat as chat to avoid unwanted modifications
            System.out.println(">>> AI classification ambiguous: '" + content + "', defaulting to CHAT <<<");
            return "CHAT";
        } catch (Exception e) {
            System.err.println("Refine intent classification failed, defaulting to CHAT: " + e.getMessage());
            return "CHAT";
        }
    }

    /**
     * Use AI to classify user intent for itinerary generation: CHAT or GENERATE.
     * CHAT = general conversation, questions, recommendations (not requesting itinerary creation)
     * GENERATE = user wants to create/plan a trip
     * Very lightweight call (max_tokens=10, temperature=0).
     */
    public String classifyGenerateIntent(String userMessage) {
        // ========== 第一层：正则快速判断 ==========
        // 匹配明确的旅行规划请求：有目的地 + 有时间
        // 模式：目的地（城市名）+ 时间（X天/X日/几天/周末等）
        String quickResult = quickClassifyByRegex(userMessage);
        if (quickResult != null) {
            System.out.println(">>> classifyGenerateIntent regex result: " + quickResult + " for: " + userMessage);
            return quickResult;
        }

        // ========== 第二层：LLM分类（用于正则无法判断的模糊情况） ==========
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", llmModel);
            body.put("temperature", 0.0);
            body.put("max_tokens", 10);

            ArrayNode msgs = body.putArray("messages");
            ObjectNode sysMsg = objectMapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", "你是意图分类器。判断用户是否要规划旅行行程。只回复GENERATE或CHAT。");
            msgs.add(sysMsg);

            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", "判断以下用户消息的意图，只回复GENERATE或CHAT：\n\n" + userMessage);
            msgs.add(userMsg);

            String jsonBody = objectMapper.writeValueAsString(body);
            String requestUrl = llmBaseUrl + llmChatPath;

            System.out.println(">>> classifyGenerateIntent LLM fallback for: " + userMessage);
            
            String response = RestClient.create()
                    .post()
                    .uri(requestUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(llmApiKey))
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText("").trim().toUpperCase();
            System.out.println(">>> classifyGenerateIntent LLM content: '" + content + "'");
            
            if (content.contains("GENERATE")) return "GENERATE";
            if (content.contains("CHAT")) return "CHAT";
            System.out.println(">>> Generate intent classification ambiguous: '" + content + "', defaulting to CHAT <<<");
            return "CHAT";
        } catch (Exception e) {
            System.err.println("Generate intent classification failed, defaulting to CHAT: " + e.getMessage());
            return "CHAT";
        }
    }

    /**
     * 正则快速分类：检测明确的旅行规划请求。
     * 规则：包含目的地（中国城市名）+ 时间信息（X天/X日/几天）→ GENERATE
     * 其他情况返回null，交给LLM判断。
     */
    private String quickClassifyByRegex(String userMessage) {
        // 中国主要城市列表
        String cities = "北京|上海|南京|杭州|成都|西安|重庆|武汉|长沙|苏州|天津|青岛|大连|宁波|厦门|深圳|广州|佛山|三亚|昆明|大理|丽江|桂林|拉萨|哈尔滨|沈阳|济南|福州|合肥|郑州|太原|石家庄|贵阳|南昌|珠海|东莞|无锡|常州|温州|泉州|烟台|威海|洛阳|开封|敦煌|九寨沟|张家界|黄山|泰山|华山|峨眉山|乌镇|周庄|西塘|凤凰|丽江|香格里拉|北海|海口";
        
        boolean hasDestination = false;
        boolean hasTime = false;
        
        // 检查是否包含目的地
        for (String city : cities.split("\\|")) {
            if (userMessage.contains(city)) {
                hasDestination = true;
                break;
            }
        }
        
        // 检查是否有时间信息
        hasTime = userMessage.matches(".*\\d+\\s*[天日].*") 
                || userMessage.contains("几天")
                || userMessage.contains("周末")
                || userMessage.contains("一天")
                || userMessage.contains("两天")
                || userMessage.contains("三天")
                || userMessage.contains("一周");
        
        // 有目的地+时间 → GENERATE
        if (hasDestination && hasTime) {
            return "GENERATE";
        }
        
        // 明确的规划关键词 + 时间 → GENERATE
        boolean hasPlanKeyword = userMessage.contains("规划") || userMessage.contains("安排") 
                || userMessage.contains("制定") || userMessage.contains("行程");
        if (hasPlanKeyword && hasTime) {
            return "GENERATE";
        }
        
        // 无法快速判断，返回null交给LLM
        return null;
    }

    /**
     * Use AI to classify modify intent and extract structured modification instructions.
     * Returns a JSON string with intent and structured modify request, or null for CHAT.
     * 
     * Output format:
     * {
     *   "intent": "MODIFY" | "CHAT",
     *   "modifyType": "replace" | "add" | "remove" | "adjust" | "swap" | null,
     *   "target": "修改目标描述",
     *   "details": "具体修改内容"
     * }
     */
    public String classifyAndStructureModifyIntent(String userMessage, String currentPlanJson) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", llmModel);
            body.put("temperature", 0.1);
            body.put("max_tokens", 300);

            ArrayNode msgs = body.putArray("messages");
            ObjectNode sysMsg = objectMapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", "你是一个行程修改意图分析器。分析用户消息，判断是修改行程还是闲聊。\n\n"
                    + "输出严格JSON格式：\n"
                    + "{\n"
                    + "  \"intent\": \"MODIFY\" 或 \"CHAT\",\n"
                    + "  \"modifyType\": \"replace|add|remove|adjust|swap|null\",\n"
                    + "  \"target\": \"修改目标（如：第2天、故宫、酒店等）\",\n"
                    + "  \"details\": \"具体修改内容描述\"\n"
                    + "}\n\n"
                    + "判断规则：\n"
                    + "MODIFY（修改行程）必须同时满足：\n"
                    + "- 明确要求对行程进行改动\n"
                    + "- 指出具体的修改目标（景点、天数、酒店等）\n"
                    + "- 例如：\"把故宫换成天坛\"、\"加一个博物馆\"、\"删掉第二天\"\n\n"
                    + "CHAT（闲聊/咨询）包括：\n"
                    + "- 询问信息（\"北京有什么好吃的？\"、\"故宫门票多少钱？\"）\n"
                    + "- 征求建议（\"你觉得行程合理吗？\"、\"还有什么推荐？\"）\n"
                    + "- 闲聊（\"谢谢\"、\"好的\"）\n"
                    + "- 询问偏好/习惯/设置（\"我有哪些偏好\"、\"我的设置是什么\"）\n"
                    + "- 任何以\"我有哪些\"、\"什么是\"、\"怎么\"开头的问题\n"
                    + "- 不涉及具体行程修改的讨论\n\n"
                    + "modifyType 说明：\n"
                    + "- replace: 替换现有内容\n"
                    + "- add: 添加新内容\n"
                    + "- remove: 删除内容\n"
                    + "- adjust: 调整时间/顺序/数量\n"
                    + "- swap: 交换两个内容的位置\n"
                    + "- null: 如果是CHAT则为null\n\n"
                    + "示例：\n"
                    + "输入：\"把故宫换成颐和园\"\n"
                    + "输出：{\"intent\":\"MODIFY\",\"modifyType\":\"replace\",\"target\":\"故宫\",\"details\":\"换成颐和园\"}\n\n"
                    + "输入：\"第二天有什么安排？\"\n"
                    + "输出：{\"intent\":\"CHAT\",\"modifyType\":null,\"target\":null,\"details\":\"询问第二天行程安排\"}\n\n"
                    + "输入：\"我有哪些偏好？\"\n"
                    + "输出：{\"intent\":\"CHAT\",\"modifyType\":null,\"target\":null,\"details\":\"询问用户偏好设置\"}");
            msgs.add(sysMsg);

            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", "当前行程概要：" + (currentPlanJson != null ? currentPlanJson.substring(0, Math.min(200, currentPlanJson.length())) + "..." : "无") + "\n\n"
                    + "用户消息：\"" + userMessage + "\"\n\nJSON输出：");
            msgs.add(userMsg);

            String jsonBody = objectMapper.writeValueAsString(body);
            String requestUrl = llmBaseUrl + llmChatPath;

            String response = RestClient.create()
                    .post()
                    .uri(requestUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(llmApiKey))
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText("").trim();
            System.out.println(">>> Modify intent analysis: " + content);
            
            // 提取JSON（处理可能的markdown代码块包裹）
            if (content.contains("```json")) {
                content = content.substring(content.indexOf("```json") + 7, content.lastIndexOf("```")).trim();
            } else if (content.contains("```")) {
                content = content.substring(content.indexOf("```") + 3, content.lastIndexOf("```")).trim();
            }
            
            return content;
        } catch (Exception e) {
            System.err.println("Failed to classify modify intent: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse modify intent JSON and determine if it's a modify request.
     * Returns true if MODIFY, false if CHAT.
     */
    private boolean isModifyIntent(String intentJson) {
        try {
            if (intentJson == null || intentJson.isBlank()) return false;
            JsonNode json = objectMapper.readTree(intentJson);
            return json.has("intent") && "MODIFY".equals(json.get("intent").asText());
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Build structured modify prompt from parsed intent JSON.
     * Combines the structured info with original user message for clarity.
     */
    private String buildStructuredModifyPrompt(String intentJson, String originalMessage) {
        try {
            if (intentJson == null || intentJson.isBlank()) return originalMessage;
            JsonNode json = objectMapper.readTree(intentJson);
            
            StringBuilder prompt = new StringBuilder();
            prompt.append("【结构化修改指令】\n");
            
            if (json.has("modifyType") && !json.get("modifyType").isNull()) {
                String type = json.get("modifyType").asText();
                String typeDesc = switch (type) {
                    case "replace" -> "替换";
                    case "add" -> "添加";
                    case "remove" -> "删除";
                    case "adjust" -> "调整";
                    case "swap" -> "交换";
                    default -> type;
                };
                prompt.append("- 操作类型：").append(typeDesc).append("\n");
            }
            
            if (json.has("target") && !json.get("target").isNull() && !json.get("target").asText("").isBlank()) {
                prompt.append("- 修改目标：").append(json.get("target").asText()).append("\n");
            }
            
            if (json.has("details") && !json.get("details").isNull() && !json.get("details").asText("").isBlank()) {
                prompt.append("- 具体内容：").append(json.get("details").asText()).append("\n");
            }
            
            prompt.append("\n【原始用户输入】\n").append(originalMessage);
            
            return prompt.toString();
        } catch (Exception e) {
            return originalMessage;
        }
    }

    /**
     * Use AI to extract structured travel requirements from user's natural language input.
     * Returns a JSON string that can be parsed into ItineraryRequest fields.
     */
    public String structureTravelRequest(String userMessage) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", llmModel);
            body.put("temperature", 0.1);
            body.put("max_tokens", 300);

            ArrayNode msgs = body.putArray("messages");
            ObjectNode sysMsg = objectMapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", "你是一个旅行需求提取器。从用户的自然语言中提取旅行信息，输出JSON格式。\n\n"
                    + "输出格式（严格JSON，不要有其他文字）：\n"
                    + "{\n"
                    + "  \"destination\": \"目的地城市或地区\",\n"
                    + "  \"days\": 3,\n"
                    + "  \"budgetLevel\": \"low/medium/high\",\n"
                    + "  \"interests\": [\"兴趣1\", \"兴趣2\"],\n"
                    + "  \"additionalNotes\": \"其他需求或备注\"\n"
                    + "}\n\n"
                    + "规则：\n"
                    + "- destination：必须提取，如果用户没说则为空字符串\n"
                    + "- days：天数，默认3\n"
                    + "  * \"三天\" \"3天\" \"三日\" → 3\n"
                    + "  * \"一周\" \"7天\" → 7\n"
                    + "  * \"周末\" → 2\n"
                    + "  * \"五一\" \"国庆\"等假期 → 根据常见假期天数推断\n"
                    + "  * 未提及 → 3\n"
                    + "- budgetLevel：\n"
                    + "  * \"省钱\" \"穷游\" \"经济\" \"便宜\" → low\n"
                    + "  * \"豪华\" \"高端\" \"五星\" \"不差钱\" → high\n"
                    + "  * 其他或未提及 → medium\n"
                    + "- interests：兴趣标签数组\n"
                    + "  * 历史/文化/古迹/博物馆/寺庙 → \"历史文化\"\n"
                    + "  * 美食/小吃/餐厅/吃货 → \"美食\"\n"
                    + "  * 自然/山水/风景/户外/徒步 → \"自然风光\"\n"
                    + "  * 购物/商场/免税店 → \"购物\"\n"
                    + "  * 海滩/海岛/潜水 → \"海滨度假\"\n"
                    + "  * 亲子/孩子/乐园/动物园 → \"亲子游\"\n"
                    + "  * 摄影/拍照/打卡 → \"摄影\"\n"
                    + "  * 温泉/spa/休闲 → \"休闲放松\"\n"
                    + "  * 未提及特定兴趣 → []\n"
                    + "- additionalNotes：其他有用信息（出发地、同行人数、特殊需求等）\n\n"
                    + "示例：\n"
                    + "输入：\"我想带家人去北京玩三天，喜欢历史文化\"\n"
                    + "输出：{\"destination\":\"北京\",\"days\":3,\"budgetLevel\":\"medium\",\"interests\":[\"历史文化\",\"亲子游\"],\"additionalNotes\":\"带家人出行\"}");
            msgs.add(sysMsg);

            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", "用户输入：\"" + userMessage + "\"\n\nJSON输出：");
            msgs.add(userMsg);

            String jsonBody = objectMapper.writeValueAsString(body);
            String requestUrl = llmBaseUrl + llmChatPath;

            String response = RestClient.create()
                    .post()
                    .uri(requestUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(llmApiKey))
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText("").trim();
            System.out.println(">>> Structured travel JSON: " + content);
            
            // 提取JSON（处理可能的markdown代码块包裹）
            if (content.contains("```json")) {
                content = content.substring(content.indexOf("```json") + 7, content.lastIndexOf("```")).trim();
            } else if (content.contains("```")) {
                content = content.substring(content.indexOf("```") + 3, content.lastIndexOf("```")).trim();
            }
            
            return content;
        } catch (Exception e) {
            System.err.println("Failed to structure travel request: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse structured JSON from AI and apply to ItineraryRequest.
     */
    private void applyStructuredRequest(ItineraryRequest request, String structuredJson) {
        try {
            JsonNode json = objectMapper.readTree(structuredJson);
            
            // 目的地
            if (json.has("destination") && !json.get("destination").asText("").isBlank()) {
                request.setDestination(json.get("destination").asText());
            }
            
            // 天数
            if (json.has("days") && json.get("days").isInt()) {
                int days = json.get("days").asInt();
                if (days > 0 && days <= 30) {  // 合理范围检查
                    request.setDays(days);
                }
            }
            
            // 预算等级
            if (json.has("budgetLevel") && !json.get("budgetLevel").asText("").isBlank()) {
                String budget = json.get("budgetLevel").asText().toLowerCase();
                if (budget.equals("low") || budget.equals("medium") || budget.equals("high")) {
                    request.setBudgetLevel(budget);
                }
            }
            
            // 兴趣列表
            if (json.has("interests") && json.get("interests").isArray()) {
                List<String> interests = new ArrayList<>();
                for (JsonNode interest : json.get("interests")) {
                    String val = interest.asText("").trim();
                    if (!val.isEmpty()) {
                        interests.add(val);
                    }
                }
                if (!interests.isEmpty()) {
                    request.setInterests(interests);
                }
            }
            
            // 附加备注
            if (json.has("additionalNotes") && !json.get("additionalNotes").asText("").isBlank()) {
                request.setAdditionalNotes(json.get("additionalNotes").asText());
            }
            
            System.out.println(">>> Applied structured request: destination=" + request.getDestination() 
                + ", days=" + request.getDays() 
                + ", budget=" + request.getBudgetLevel() 
                + ", interests=" + request.getInterests());
                
        } catch (Exception e) {
            System.err.println("Failed to parse structured request JSON: " + e.getMessage());
        }
    }

    /**
     * Generate a brief chat answer when user asks a question in the refine panel.
     */
    public String answerChatQuestion(String userMessage, String currentPlanJson) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", llmModel);
            body.put("temperature", 0.7);
            body.put("max_tokens", 512);

            ArrayNode msgs = body.putArray("messages");
            ObjectNode sysMsg = objectMapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", "你是一个智能旅行助手。用户正在查看一份已经生成好的旅行行程，这是在已有行程基础上的延续对话，不是新对话。\n\n"
                    + "【当前行程上下文】\n" + currentPlanJson + "\n\n"
                    + "重要规则：\n"
                    + "1. 不要问候、不要询问用户偏好，直接回答用户的问题或建议\n"
                    + "2. 200字以内，简洁有力\n"
                    + "3. 回答时可以参考当前行程的内容\n"
                    + "4. 如果用户说\"在XX的基础上改进\"，请基于当前行程给出具体的改进建议，但不要直接替换行程内容\n"
                    + "5. 回答末尾可以提醒：如需将建议应用到行程中，请明确说明具体如何修改");
            msgs.add(sysMsg);

            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            msgs.add(userMsg);

            String jsonBody = objectMapper.writeValueAsString(body);
            String requestUrl = llmBaseUrl + llmChatPath;

            String response = RestClient.create()
                    .post()
                    .uri(requestUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(llmApiKey))
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText("").trim();
            return content.isEmpty() ? "你好！有什么旅行相关的问题我可以帮你解答？" : content;
        } catch (Exception e) {
            System.err.println("Chat question answer failed: " + e.getMessage());
            return "抱歉，暂时无法回答这个问题，请稍后重试。";
        }
    }

    private String toolDisplayName(String name) {
        return switch (name) {
            case "maps_weather" -> "天气查询";
            case "maps_text_search" -> "地点搜索";
            case "maps_around_search" -> "周边搜索";
            case "maps_search_detail" -> "地点详情查询";
            case "maps_direction_driving" -> "驾车路线规划";
            case "maps_direction_walking" -> "步行路线规划";
            case "maps_direction_bicycling" -> "骑行路线规划";
            case "maps_direction_transit_integrated" -> "公共交通路线规划";
            case "maps_distance" -> "测距";
            case "maps_geo" -> "坐标解析";
            case "maps_regeocode" -> "地址解析";
            case "maps_ip_location" -> "IP定位";
            case "maps_schema_personal_map" -> "地图展示";
            case "maps_schema_navi" -> "导航链接";
            case "maps_schema_take_taxi" -> "打车链接";
            case "search_destination_info" -> "目的地信息查询";
            default -> name;
        };
    }

    /**
     * Extract XML-style tool calls from LLM content (when model outputs
     * &lt;invoke name="..."&gt; instead of proper tool_calls JSON array).
     */
    private List<Map<String, String>> extractXmlToolCalls(String content) {
        List<Map<String, String>> result = new ArrayList<>();
        int pos = 0;
        while (true) {
            int invokeStart = content.indexOf("<invoke", pos);
            if (invokeStart < 0) break;
            int invokeEnd = content.indexOf("</invoke>", invokeStart);
            if (invokeEnd < 0) break;
            String block = content.substring(invokeStart, invokeEnd + "</invoke>".length());

            // Extract tool name from <invoke name="tool_name">
            String toolName = extractXmlAttr(block, "name");
            if (toolName == null || toolName.isBlank()) {
                pos = invokeEnd + 1;
                continue;
            }

            // Extract all <parameter name="..." string="true">value</parameter>
            Map<String, String> params = new LinkedHashMap<>();
            int paramPos = 0;
            while (true) {
                int pStart = block.indexOf("<parameter", paramPos);
                if (pStart < 0) break;
                int pEnd = block.indexOf("</parameter>", pStart);
                if (pEnd < 0) break;
                String pBlock = block.substring(pStart, pEnd + "</parameter>".length());
                String pName = extractXmlAttr(pBlock, "name");
                String pValue = block.substring(block.indexOf(">", pStart) + 1, pEnd).trim();
                if (pName != null && !pName.isBlank()) {
                    params.put(pName, pValue);
                }
                paramPos = pEnd + 1;
            }

            if (!params.isEmpty()) {
                Map<String, String> call = new LinkedHashMap<>();
                call.put("name", toolName);
                try {
                    call.put("args", objectMapper.writeValueAsString(params));
                } catch (Exception e) {
                    call.put("args", "{}");
                }
                result.add(call);
            }

            pos = invokeEnd + 1;
        }
        return result;
    }

    private String extractXmlAttr(String xml, String attrName) {
        int idx = xml.indexOf(attrName + "=\"");
        if (idx < 0) return null;
        int start = idx + attrName.length() + 2;
        int end = xml.indexOf("\"", start);
        if (end < 0) return null;
        return xml.substring(start, end);
    }

    /**
     * Execute a tool call extracted from XML content.
     */
    private void executeXmlToolCall(AmapMcpClient mcpClient, String toolName, String toolArgs,
            List<Map<String, Object>> messages,
            java.util.function.Consumer<String> toolNotifier,
            GenerationStats stats) {
        try {
            Map<String, Object> assistantMsg = new LinkedHashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", "调用工具: " + toolName);
            messages.add(assistantMsg);

            if (toolNotifier != null) {
                toolNotifier.accept("TOOL:调用" + toolDisplayName(toolName));
            }

            String result = mcpClient.callTool(toolName, toolArgs);

            stats.getToolCalls().add(new GenerationStats.ToolCallRecord(
                    toolName,
                    toolDisplayName(toolName),
                    result != null && result.startsWith("Error") ? "failed" : "success"));

            Map<String, Object> toolMsg = new LinkedHashMap<>();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", "xml-" + stats.getToolCalls().size());
            toolMsg.put("name", toolName);
            toolMsg.put("content", result);
            messages.add(toolMsg);
        } catch (Exception e) {
            System.err.println("XML tool call failed for " + toolName + ": " + e.getMessage());
            Map<String, Object> toolMsg = new LinkedHashMap<>();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", "xml-" + stats.getToolCalls().size());
            toolMsg.put("name", toolName);
            toolMsg.put("content", "Error: " + e.getMessage());
            messages.add(toolMsg);
        }
    }

    private String stripCodeFence(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            if (firstLineBreak > 0) {
                trimmed = trimmed.substring(firstLineBreak + 1, trimmed.length() - 3).trim();
            }
        }
        return trimmed;
    }

    private ItineraryPlan parseItinerary(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IntellitravelException("AI 返回了空内容，请稍后重试～");
        }

        // Try extracting from final_output tags first
        String finalOutput = extractFinalOutput(rawResponse);
        if (finalOutput != null) {
            rawResponse = finalOutput;
        }

        String cleaned = stripCodeFence(rawResponse);
        String jsonToParse = cleaned;

        int firstBrace = -1;
        int lastBrace = -1;
        int braceCount = 0;
        int inString = 0;
        boolean escaped = false;

        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\' && inString == 1) {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = 1 - inString;
                continue;
            }

            if (inString == 0) {
                if (c == '{') {
                    if (braceCount == 0) {
                        firstBrace = i;
                    }
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        lastBrace = i;
                    }
                }
            }
        }

        if (firstBrace >= 0 && lastBrace >= 0 && lastBrace > firstBrace) {
            jsonToParse = cleaned.substring(firstBrace, lastBrace + 1);
        }

        // Safety: strip leading markdown / non-JSON content (e.g. "#", XML, text)
        jsonToParse = jsonToParse.trim();
        if (!jsonToParse.startsWith("{")) {
            int braceIdx = jsonToParse.indexOf("{");
            if (braceIdx >= 0) {
                jsonToParse = jsonToParse.substring(braceIdx);
            }
        }

        jsonToParse = fixJsonIssues(jsonToParse);

        // Try to repair truncated JSON (LLM output hit token limit)
        String repaired = repairTruncatedJson(jsonToParse);
        if (!repaired.equals(jsonToParse)) {
            System.out.println("Repaired truncated itinerary JSON");
            jsonToParse = repaired;
        }

        try {
            JsonNode root = objectMapper.readTree(jsonToParse);
            ItineraryPlan plan = objectMapper.treeToValue(root, ItineraryPlan.class);
            return plan;
        } catch (Exception e) {
            try {
                JsonNode fallbackRoot = objectMapper.readTree(jsonToParse);
                JsonNode itineraryNode = null;
                if (fallbackRoot.has("data") && fallbackRoot.get("data") != null
                        && fallbackRoot.get("data").has("itinerary")) {
                    itineraryNode = fallbackRoot.get("data").get("itinerary");
                } else if (fallbackRoot.has("itinerary")) {
                    itineraryNode = fallbackRoot.get("itinerary");
                }
                if (itineraryNode != null && !itineraryNode.isNull()) {
                    ItineraryPlan plan = objectMapper.treeToValue(itineraryNode, ItineraryPlan.class);
                    return plan;
                }
                throw new IntellitravelException("AI 未能返回符合规范的行程，请再次尝试哦～");
            } catch (Exception ex) {
                System.err.println("====== 大模型返回内容解析失败 ======");
                System.err.println("原始报文长度: " + rawResponse.length());
                System.err.println("提取的JSON部分前500字符: \n" +
                        (jsonToParse.length() > 500 ? jsonToParse.substring(0, 500) + "..." : jsonToParse));
                System.err.println("错误信息: " + ex.getMessage());
                System.err.println("==========================================");
                throw new IntellitravelException("AI 生成的排版有点小偏差，请稍后刷新重试呀～");
            }
        }
    }

    private String extractFinalOutput(String content) {
        int start = content.indexOf("final_output");
        if (start < 0) return null;
        int jsonStart = content.indexOf("{", start);
        if (jsonStart < 0) return null;
        int end = content.indexOf("/final_output", jsonStart);
        if (end < 0) return content.substring(jsonStart);
        return content.substring(jsonStart, end).trim();
    }

    private String repairTruncatedJson(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) return json;
        json = json.trim();

        try {
            objectMapper.readTree(json);
            return json;
        } catch (Exception ignored) {
        }

        // Strategy 1: simple bracket repair
        String bracketRepaired = tryBracketRepair(json);
        if (bracketRepaired != null) return bracketRepaired;

        // Strategy 2: strip incomplete trailing value, then bracket repair
        String stripped = stripIncompleteValue(json);
        String strippedRepaired = tryBracketRepair(stripped);
        if (strippedRepaired != null) return strippedRepaired;

        return json;
    }

    private String tryBracketRepair(String json) {
        java.util.List<Character> openStack = new java.util.ArrayList<>();
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{' || c == '[') {
                openStack.add(c);
            } else if (c == '}') {
                removeLastOpen(openStack, '{');
            } else if (c == ']') {
                removeLastOpen(openStack, '[');
            }
        }

        if (openStack.isEmpty()) return null;

        StringBuilder sb = new StringBuilder(json);
        for (int i = openStack.size() - 1; i >= 0; i--) {
            char opener = openStack.get(i);
            sb.append(opener == '{' ? '}' : ']');
        }

        String repaired = sb.toString();
        try {
            objectMapper.readTree(repaired);
            return repaired;
        } catch (Exception ignored) {
        }
        return null;
    }

    private void removeLastOpen(java.util.List<Character> stack, char expectedOpen) {
        for (int j = stack.size() - 1; j >= 0; j--) {
            if (stack.get(j) == expectedOpen) {
                stack.remove(j);
                return;
            }
        }
    }

    private String stripIncompleteValue(String json) {
        int stripPoint = json.length();
        boolean inString = false;
        boolean escaped = false;

        for (int i = json.length() - 1; i >= 0; i--) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\' && i > 0) {
                    boolean prevEscaped = false;
                    int bsCount = 0;
                    for (int j = i; j >= 0 && json.charAt(j) == '\\'; j--) bsCount++;
                    escaped = (bsCount % 2 == 1);
                    if (escaped) continue;
                }
                if (c == '"') inString = false;
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == ',' || c == ':' || c == '{' || c == '[') {
                stripPoint = c == ',' || c == ':' ? i : i + 1;
                break;
            }
        }

        if (stripPoint >= json.length()) return json;

        String stripped = json.substring(0, stripPoint);

        if (stripped.endsWith(",")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        } else if (stripped.endsWith(":")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }

        return stripped.trim();
    }

    private String fixJsonIssues(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        int i = 0;

        while (i < json.length()) {
            char c = json.charAt(i);

            if (escaped) {
                result.append(c);
                escaped = false;
                i++;
                continue;
            }

            if (c == '\\' && inString) {
                result.append(c);
                escaped = true;
                i++;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                result.append(c);
                i++;
                continue;
            }

            if (!inString) {
                if (i + 1 < json.length() && json.charAt(i) == '/' && json.charAt(i + 1) == '/') {
                    while (i < json.length() && json.charAt(i) != '\n') {
                        i++;
                    }
                    continue;
                }
                if (i + 1 < json.length() && json.charAt(i) == '/' && json.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < json.length() && !(json.charAt(i) == '*' && json.charAt(i + 1) == '/')) {
                        i++;
                    }
                    i += 2;
                    continue;
                }
                if (c == ',' && i + 1 < json.length()) {
                    int j = i + 1;
                    while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
                        j++;
                    }
                    if (j < json.length() && (json.charAt(j) == '}' || json.charAt(j) == ']')) {
                        i++;
                        continue;
                    }
                }
            }

            result.append(c);
            i++;
        }

        return result.toString();
    }

    private JsonNode callLlmChat(List<Map<String, Object>> messages, JsonNode tools) {
        return callLlmChat(messages, tools, null);
    }

    private JsonNode callLlmChat(List<Map<String, Object>> messages, JsonNode tools, Integer overrideMaxTokens) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", llmModel);
        body.put("temperature", llmTemperature);
        body.set("messages", objectMapper.valueToTree(messages));

        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
        }

        if (overrideMaxTokens != null) {
            body.put("max_tokens", overrideMaxTokens);
        } else if (tools != null && !tools.isEmpty()) {
            body.put("max_tokens", 16384);
        }

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IntellitravelException("准备 AI 请求数据失败，请重试～");
        }

        String raw = executeGptRequest(jsonBody);

        try {
            String trimmedRaw = raw.trim();
            if (!trimmedRaw.startsWith("{") && !trimmedRaw.startsWith("[")) {
                System.err.println("====== LLM API 返回非 JSON 响应 ======");
                System.err.println("响应内容前500字符: " +
                        (trimmedRaw.length() > 500 ? trimmedRaw.substring(0, 500) + "..." : trimmedRaw));
                System.err.println("==========================================");
                throw new IntellitravelException("AI 服务返回了非预期的响应格式，请检查 API 配置或联系管理员～");
            }
            return objectMapper.readTree(raw);
        } catch (IntellitravelException ie) {
            throw ie;
        } catch (Exception e) {
            System.err.println("====== LLM 响应解析失败 ======");
            System.err.println("错误信息: " + e.getMessage());
            System.err.println("原始响应前500字符: " +
                    (raw != null && raw.length() > 500 ? raw.substring(0, 500) + "..." : raw));
            System.err.println("==========================================");
            throw new IntellitravelException("解读 AI 响应数据出现小问题，请再试一次哦～");
        }
    }

    private JsonNode callLlmChatStreaming(List<Map<String, Object>> messages) {
        if (llmApiKey == null || llmApiKey.isBlank()) {
            throw new IntellitravelException("未配置 AI 密钥，请联系系统管理员");
        }

        String requestUrl = llmBaseUrl + llmChatPath;

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", llmModel);
        body.put("temperature", llmTemperature);
        body.put("stream", true);
        body.set("messages", objectMapper.valueToTree(messages));

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IntellitravelException("准备 AI 流式请求数据失败，请重试～");
        }

        HttpClient streamingClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + llmApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = streamingClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                String errBody = response.body() != null ? response.body() : "";
                throw new IntellitravelException("AI 流式请求失败 (HTTP " + response.statusCode() + "): " +
                        (errBody.length() > 300 ? errBody.substring(0, 300) + "..." : errBody));
            }

            StringBuilder contentBuilder = new StringBuilder();
            StringBuilder reasoningBuilder = new StringBuilder();
            String[] lines = response.body().split("\\r?\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("data:")) {
                    String data = trimmed.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    try {
                        JsonNode chunk = objectMapper.readTree(data);
                        JsonNode choices = chunk.path("choices");
                        if (choices.isArray() && !choices.isEmpty()) {
                            JsonNode delta = choices.get(0).path("delta");
                            if (delta.has("content") && !delta.get("content").isNull()) {
                                contentBuilder.append(delta.get("content").asText());
                            }
                            if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                                reasoningBuilder.append(delta.get("reasoning_content").asText());
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            String fullContent = contentBuilder.toString();
            if (fullContent.isBlank() && reasoningBuilder.isEmpty()) {
                throw new IntellitravelException("AI 流式响应未产生任何内容");
            }

            ObjectNode responseNode = objectMapper.createObjectNode();
            ArrayNode choicesArr = responseNode.putArray("choices");
            ObjectNode choice = choicesArr.addObject();
            choice.put("index", 0);
            ObjectNode msg = choice.putObject("message");
            msg.put("role", "assistant");
            msg.put("content", fullContent);
            if (!reasoningBuilder.isEmpty()) {
                msg.put("reasoning_content", reasoningBuilder.toString());
            }
            choice.put("finish_reason", "stop");

            return responseNode;
        } catch (IntellitravelException ie) {
            throw ie;
        } catch (Exception e) {
            System.err.println("====== LLM 流式请求异常 ======");
            System.err.println("异常类型: " + e.getClass().getName());
            System.err.println("异常信息: " + e.getMessage());
            System.err.println("==========================================");
            throw new IntellitravelException("AI 流式生成过程中出现问题，请稍后重试～");
        }
    }

    private String executeGptRequest(Object body) {
        if (llmApiKey == null || llmApiKey.isBlank()) {
            throw new IntellitravelException("未配置 AI 密钥，请联系系统管理员");
        }

        String requestUrl = llmBaseUrl + llmChatPath;
        System.out.println("LLM Request URL: " + requestUrl);

        int maxRetries = 3;
        int attempt = 0;
        long backoff = 1000;
        Exception lastException = null;

        while (attempt < maxRetries) {
            try {
                String response = RestClient.create()
                        .post()
                        .uri(requestUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(headers -> headers.setBearerAuth(llmApiKey))
                        .body(body)
                        .retrieve()
                        .body(String.class);

                if (response != null && response.trim().toLowerCase().startsWith("<!doctype") ||
                        response != null && response.trim().toLowerCase().startsWith("<html")) {
                    System.err.println("====== LLM API 返回 HTML 错误页面 ======");
                    System.err.println("请求 URL: " + requestUrl);
                    System.err.println("响应内容前500字符: " +
                            (response.length() > 500 ? response.substring(0, 500) + "..." : response));
                    System.err.println("==========================================");
                    throw new IntellitravelException("AI 服务端点配置错误，返回了 HTML 页面而非 API 响应。请检查 LLM Base URL 配置～");
                }

                return response;
            } catch (IntellitravelException ie) {
                throw ie;
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                lastException = e;
                System.err.println("====== LLM API 客户端错误 ======");
                System.err.println("请求 URL: " + requestUrl);
                System.err.println("状态码: " + e.getStatusCode());
                System.err.println("响应体: " + e.getResponseBodyAsString());
                System.err.println("==========================================");
                if (e.getStatusCode().value() == 404) {
                    throw new IntellitravelException("AI 服务端点不存在 (404)，请检查 LLM Base URL 和 Chat Path 配置～");
                } else if (e.getStatusCode().value() == 401) {
                    throw new IntellitravelException("AI API 密钥无效 (401)，请检查 API Key 配置～");
                }
                throw new IntellitravelException("AI 服务请求失败 (" + e.getStatusCode() + ")，请稍后重试～");
            } catch (org.springframework.web.client.HttpServerErrorException e) {
                lastException = e;
                System.err.println("====== LLM API 服务器错误 ======");
                System.err.println("请求 URL: " + requestUrl);
                System.err.println("状态码: " + e.getStatusCode());
                System.err.println("响应体: " + e.getResponseBodyAsString());
                System.err.println("==========================================");
                attempt++;
                if (attempt >= maxRetries) {
                    throw new IntellitravelException("AI 服务器内部错误 (" + e.getStatusCode() + ")，请稍后重试～");
                }
            } catch (Exception e) {
                lastException = e;
                System.err.println("====== LLM API 请求异常 ======");
                System.err.println("请求 URL: " + requestUrl);
                System.err.println("异常类型: " + e.getClass().getName());
                System.err.println("异常信息: " + e.getMessage());
                System.err.println("==========================================");
                attempt++;
                if (attempt >= maxRetries) {
                    break;
                }
            }

            if (attempt < maxRetries) {
                try {
                    Thread.sleep(backoff);
                    backoff *= 2;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IntellitravelException("AI 调用被意外中断: " + ie.getMessage());
                }
            }
        }

        String errorMsg = lastException != null ? lastException.getMessage() : "未知错误";
        throw new IntellitravelException("AI 规划师服务器当前可能比较繁忙或处于维护中，请稍等片刻再试哦～ 错误: " + errorMsg);
    }

    private class AmapMcpClient implements AutoCloseable {
        private final String sseUrl;
        private final HttpClient client;
        private String postEndpoint;
        private CompletableFuture<Void> sseTask;
        private int requestId = 0;

        private final java.util.concurrent.ConcurrentMap<Integer, CompletableFuture<JsonNode>> pendingRequests = new java.util.concurrent.ConcurrentHashMap<>();

        public AmapMcpClient(String sseUrl) {
            this.sseUrl = sseUrl;
            this.client = HttpClient.newBuilder().executor(java.util.concurrent.Executors.newCachedThreadPool())
                    .build();
        }

        public void initialize() throws Exception {
            URI uri = URI.create(sseUrl);
            HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();

            CompletableFuture<String> endpointFuture = new CompletableFuture<>();

            sseTask = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .whenComplete((r, e) -> {
                        if (e != null) {
                            endpointFuture.completeExceptionally(e);
                        }
                    })
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            endpointFuture.completeExceptionally(new RuntimeException(
                                    "SSE Connection failed with status: " + response.statusCode()));
                            return;
                        }

                        java.util.concurrent.atomic.AtomicReference<String> currentEvent = new java.util.concurrent.atomic.AtomicReference<>(
                                "");
                        java.util.concurrent.atomic.AtomicReference<StringBuilder> currentData = new java.util.concurrent.atomic.AtomicReference<>(
                                new StringBuilder());

                        response.body().forEach(line -> {
                            String trimmedLine = line.trim();
                            if (trimmedLine.isEmpty()) {
                                String event = currentEvent.get();
                                String data = currentData.get().toString();

                                if ("endpoint".equals(event)) {
                                    if (!endpointFuture.isDone() && !data.isBlank()) {
                                        endpointFuture.complete(data.trim());
                                    }
                                } else if ("message".equals(event)) {
                                    try {
                                        JsonNode msg = objectMapper.readTree(data);
                                        if (msg.has("id")) {
                                            int id = msg.get("id").asInt();
                                            CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                                            if (future != null) {
                                                if (msg.has("error")) {
                                                    future.complete(msg);
                                                } else {
                                                    future.complete(msg);
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        System.err.println("Failed to parse SSE message: " + e.getMessage());
                                    }
                                }

                                currentEvent.set("");
                                currentData.set(new StringBuilder());
                            } else if (trimmedLine.startsWith("event:")) {
                                currentEvent.set(trimmedLine.substring(6).trim());
                            } else if (trimmedLine.startsWith("data:")) {
                                currentData.get().append(trimmedLine.substring(5).trim()).append("\n");
                            }
                        });
                    });

            String relativeEndpoint = endpointFuture.get(120, TimeUnit.SECONDS);
            this.postEndpoint = uri.resolve(relativeEndpoint).toString();

            JsonNode initResponse = callJsonRpc("initialize", Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of("sampling", Map.of()),
                    "clientInfo", Map.of("name", "java-client", "version", "1.0")));
            if (initResponse.has("error")) {
                throw new RuntimeException(
                        "MCP Initialization failed: " + initResponse.get("error").path("message").asText());
            }

            sendJsonRpcNotification("notifications/initialized", Map.of());
        }

        public JsonNode listTools() throws Exception {
            JsonNode response = callJsonRpc("tools/list", Map.of());
            if (response.has("error")) {
                System.err.println("Error listing tools: " + response.get("error").toPrettyString());
                return objectMapper.createArrayNode();
            }

            JsonNode tools = response.path("result").path("tools");
            if (tools.isMissingNode()) {
                return objectMapper.createArrayNode();
            }
            ArrayNode llmTools = objectMapper.createArrayNode();
            if (tools.isArray()) {
                for (JsonNode tool : tools) {
                    ObjectNode llmTool = objectMapper.createObjectNode();
                    llmTool.put("type", "function");
                    ObjectNode function = llmTool.putObject("function");
                    function.put("name", tool.path("name").asText());
                    function.put("description", tool.path("description").asText());
                    function.set("parameters", tool.path("inputSchema"));
                    llmTools.add(llmTool);
                }
            }
            return llmTools;
        }

        public String callTool(String name, String argumentsJson) throws Exception {
            Map<String, Object> args = objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {
            });
            JsonNode response = callJsonRpc("tools/call", Map.of(
                    "name", name,
                    "arguments", args));

            if (response.has("error")) {
                return "Error: " + response.get("error").path("message").asText();
            }

            JsonNode content = response.path("result").path("content");
            List<String> texts = new ArrayList<>();
            if (content.isArray()) {
                for (JsonNode item : content) {
                    if ("text".equals(item.path("type").asText())) {
                        texts.add(item.path("text").asText());
                    }
                }
            }
            return String.join("\n", texts);
        }

        private JsonNode callJsonRpc(String method, Object params) throws Exception {
            int id;
            synchronized (this) {
                id = requestId++;
            }
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            pendingRequests.put(id, future);

            try {
                Map<String, Object> request = new HashMap<>();
                request.put("jsonrpc", "2.0");
                request.put("id", id);
                request.put("method", method);
                request.put("params", params);

                HttpResponse<String> response = sendRpcHttpRequest(request);
                if (response.statusCode() >= 400) {
                    throw new RuntimeException("MCP RPC failed: " + response.body());
                }

                return future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                pendingRequests.remove(id);
                throw e;
            }
        }

        private void sendJsonRpcNotification(String method, Object params) throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("method", method);
            request.put("params", params);

            HttpResponse<String> response = sendRpcHttpRequest(request);
            if (response.statusCode() >= 400) {
                throw new RuntimeException("MCP Notification failed: " + response.body());
            }
        }

        private HttpResponse<String> sendRpcHttpRequest(Map<String, Object> request) throws Exception {
            String requestBody = objectMapper.writeValueAsString(request);

            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(postEndpoint))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            return client.send(postReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
            if (sseTask != null) {
                sseTask.cancel(true);
            }
        }
    }






    ////任务二
    private String buildContradictionWarning(EntityTracker.Contradiction contradiction) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n 检测到与之前对话的矛盾：\n");
        for (String conflict : contradiction.getConflicts()) {
            sb.append("- ").append(conflict).append("\n");
        }
        sb.append("请确认用户的最终意图。\n");
        return sb.toString();
    }

    private String buildEnhancedSystemPrompt(ConversationContextManager.OptimizedContext context,
                                             EntityTracker.ExtractedEntities entities, MemoryManager.WorkingMemory workingMemory,
                                             String token) {
        StringBuilder sb = new StringBuilder();
        sb.append(EXECUTE_SYSTEM_PROMPT_PREFIX);

        // 添加历史摘要
        if (context != null && context.getSummary() != null && !context.getSummary().isBlank()) {
            sb.append("\n\n").append("【对话历史摘要】\n").append(context.getSummary()).append("\n");
        }

        // 添加已识别的实体信息
        if (entities != null) {
            boolean hasInfo = false;
            StringBuilder entitySb = new StringBuilder("\n\n## 已识别的信息（多轮对话累计）\n");
            if (entities.getDestination() != null) {
                entitySb.append("- 目的地：").append(entities.getDestination()).append("\n");
                hasInfo = true;
            }
            if (entities.getDays() != null) {
                entitySb.append("- 天数：").append(entities.getDays()).append("天\n");
                hasInfo = true;
            }
            if (entities.getBudgetLevel() != null) {
                entitySb.append("- 预算：").append(entities.getBudgetLevel()).append("\n");
                hasInfo = true;
            }
            if (entities.getInterests() != null && !entities.getInterests().isEmpty()) {
                entitySb.append("- 兴趣偏好：").append(String.join("、", entities.getInterests())).append("\n");
                hasInfo = true;
            }
            if (hasInfo) {
                sb.append(entitySb.toString());
            }
        }

        // 添加工作记忆
        if (workingMemory != null && workingMemory.getDraftPlan() != null) {
            sb.append("\n\n## 当前任务状态\n");
            sb.append("- 已有行程草案：已生成").append(workingMemory.getDraftPlan().getTotalDays()).append("天行程\n");
            if (workingMemory.getCompletedSteps() != null && !workingMemory.getCompletedSteps().isEmpty()) {
                sb.append("- 已完成：").append(String.join(", ", workingMemory.getCompletedSteps())).append("\n");
            }
        }

        // 添加长期记忆（用户画像）
        try {
            Integer userId = Integer.parseInt(tokenUtil.getUser(token).getId().toString());
            String userProfile = memoryManager.buildUserProfileContext(userId);
            if (userProfile != null && !userProfile.isBlank()) {
                sb.append(userProfile);
            }
        } catch (Exception e) {
        }

        return sb.toString();
    }

    private String detectActionType(String userPrompt) {
        if (userPrompt == null) return "UNKNOWN";
        if (userPrompt.contains("删除") || userPrompt.contains("去掉") ||
                userPrompt.contains("移除") || userPrompt.contains("删掉")) {
            return "DELETE";
        }
        if (userPrompt.contains("增加") || userPrompt.contains("添加") || userPrompt.contains("新增")) {
            return "ADD";
        }
        // 天数变更：Pattern 匹配 + 自然表达
        Pattern daysPattern = Pattern.compile("(改为|改成|变为|增加|减少|调整到)\\s*(\\d+)\\s*天");
        Matcher daysMatcher = daysPattern.matcher(userPrompt);
        if (daysMatcher.find()) {
            if (!userPrompt.matches("第\\d+天.*")) {
                return "UPDATE_DAYS";
            }
        }
        // 自然语言天数变更表达
        if (userPrompt.contains("多玩") || userPrompt.contains("少玩") ||
                userPrompt.contains("加一天") || userPrompt.contains("减一天") ||
                userPrompt.contains("多一天") || userPrompt.contains("少一天") ||
                userPrompt.contains("再加一天") || userPrompt.contains("延长") ||
                userPrompt.contains("缩短") || userPrompt.contains("多几天") ||
                userPrompt.contains("少几天")) {
            return "UPDATE_DAYS";
        }
        if (userPrompt.contains("改成") || userPrompt.contains("换成") || userPrompt.contains("修改") ||
                userPrompt.contains("变为")) {
            return "MODIFY";
        }
        if (userPrompt.contains("预算")) {
            if (userPrompt.contains("改成") || userPrompt.contains("调整") || userPrompt.contains("增加") || userPrompt.contains("减少")) {
                return "UPDATE_BUDGET";
            }
        }

        return "UNKNOWN";
    }
    private int extractDaysFromPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) return 0;

        // 匹配数字+天的模式（如 "改为5天"→5, "增加2天"→2）
        Pattern pattern = Pattern.compile("(\\d+)\\s*天");
        Matcher matcher = pattern.matcher(prompt);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("提取天数失败: {}", matcher.group(1));
            }
        }
        // 自然语言天数变更：多一天/少一天 → 返回 -1 表示相对变更，由调用方计算
        if (prompt.contains("多一天") || prompt.contains("多玩一天") || prompt.contains("加一天") ||
                prompt.contains("再多一天") || prompt.contains("延长一天")) {
            return -1; // 表示 +1 天
        }
        if (prompt.contains("少一天") || prompt.contains("少玩一天") || prompt.contains("减一天") ||
                prompt.contains("缩短一天")) {
            return -2; // 表示 -1 天
        }
        return 0;
    }

}
