package com.travel.intellitravel_pioneer.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryRequest;
import com.travel.intellitravel_pioneer.vo.trip.GenerationResult;
import com.travel.intellitravel_pioneer.vo.trip.GenerationStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HttpTripPlanningAiClient implements TripPlanningAiClient {

    private final ObjectMapper objectMapper;

    @Value("${trip.ai.provider:llm}")
    private String aiProvider;

    @Value("${trip.ai.base-url:}")
    private String aiBaseUrl;

    @Value("${trip.ai.plan-path:/api/trips/plan}")
    private String aiPlanPath;

    @Value("${trip.ai.status-analysis-path:/api/trips/status/analyze}")
    private String aiStatusAnalysisPath;

    @Value("${trip.ai.llm.base-url:https://api.siliconflow.cn}")
    private String llmBaseUrl;

    @Value("${trip.ai.llm.chat-path:/v1/chat/completions}")
    private String llmChatPath;

    @Value("${trip.ai.llm.api-key:}")
    private String llmApiKey;

    @Value("${trip.ai.llm.model:Qwen/Qwen3-8B}")
    private String llmModel;

    @Value("${trip.ai.llm.temperature:0.2}")
    private Double llmTemperature;

    @Value("${trip.ai.amap.mcp-url:https://mcp.amap.com/sse?key=bc02d1f93e142eb512f2a9d058d32560}")
    private String amapMcpUrl;

    public HttpTripPlanningAiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
            return callLlmForItinerary(request, toolNotifier);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("request", request);

        String rawResponse = callCustomAiService(aiPlanPath, token, requestBody);
        ItineraryPlan plan = parseItinerary(rawResponse);
        return new GenerationResult(plan, null);
    }

    @Override
    public ItineraryPlan modifyItinerary(String token, ItineraryRequest request,
            ItineraryPlan currentPlan, String userPrompt) {
        GenerationResult result = modifyItineraryWithStats(token, request, currentPlan, userPrompt, null);
        return result != null ? result.getPlan() : null;
    }

    @Override
    public GenerationResult modifyItineraryWithStats(String token, ItineraryRequest request,
            ItineraryPlan currentPlan, String userPrompt,
            java.util.function.Consumer<String> toolNotifier) {
        if (!isLlmProvider()) {
            throw new IntellitravelException("非 LLM 模式下暂不支持行程修改");
        }
        return callLlmForRefine(request, toJsonString(currentPlan), userPrompt, toolNotifier);
    }

    private GenerationResult callLlmForRefine(ItineraryRequest request, String currentPlanJson,
            String userPrompt, java.util.function.Consumer<String> toolNotifier) {
        GenerationStats stats = new GenerationStats();
        String amapUrl = amapMcpUrl;
        try (AmapMcpClient mcpClient = new AmapMcpClient(amapUrl)) {
            mcpClient.initialize();

            JsonNode mcpTools = mcpClient.listTools();
            JsonNode tools = mergeTools(mcpTools);

            sendPhaseNotification(toolNotifier, "REFINE_START",
                    "开始分析修改建议，调用工具验证新地点...");

            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", REFINE_SYSTEM_PROMPT));
            messages.add(Map.of("role", "user", "content",
                "当前已有行程计划：\n" + currentPlanJson + "\n\n用户修改建议：\n" + userPrompt));

            ItineraryPlan refinedPlan;
            int executeRetries = 0;
            int maxExecuteRetries = 2;
            while (true) {
                try {
                    refinedPlan = runAgentLoop(messages, tools, mcpClient, request, toolNotifier, stats);
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

            1.【纯删除意图】（如"删掉故宫""去掉第2天"）
               → 不需要调用任何工具，直接在已有 JSON 上删除指定条目。

            2.【新增或替换地点】（如"加一个故宫""把长城换成颐和园""再加一天"）
               → 必须调用 maps_text_search 查询新地点的真实名称和精确经纬度，
                 验证通过后才写入 JSON。严禁凭空编造坐标。
               → 如果 maps_text_search 返回空结果，尝试修改搜索关键词（如去掉"市/区"后缀），
                 若仍无结果，在 Thought 中说明并在最终结果中使用用户提供的名称，location 标注为 null。

            3.【仅修改描述/预算】（如"门票改成60元""预算调整为2000"）
               → 不需要调用工具，直接修改对应字段。

            【输出规范】
            - 不改动的部分原样保留
            - Thought 里说明每一步操作
            - 新增/替换地点后必须做 SelfCheck

            【SelfCheck 自检（输出前必须完成）】
            Thought: SelfCheck:
            OK 新增地点坐标来自工具返回 -> [已验证/存在未验证项]
            OK 删除操作正确执行 -> [已完成]
            OK 预算仍与需求匹配 -> [匹配]
            OK description 控制在30字以内 -> [通过]

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
            1. 鼓励并行：同时查天气+搜索景点，不要逐个来。Thought 要具体，如"子任务1.天气侦察：北京3天需查3天预报来决定户外/室内分配"
            2. 失败处理：工具调用失败时在 Thought 中说明，并尝试备选方案（换关键词或使用其他工具替代）
            3. 严禁编造：所有地点名称和坐标必须来自工具返回结果
            4. 每天至少安排 2-3 个行程项
            5. **天数硬约束：totalDays 和 dailyPlans 的数量必须严格等于用户请求的天数（request.days），不多不少！绝对不能重复生成相同 dayNumber 的计划**

            【可用工具】
            - 高德地图工具（maps_开头）：查询真实地理信息、天气、路线，验证地点经纬度
            - search_destination_info：查询目的地旅行季节、热门景点、当地美食、交通建议

            【停止规则】
            最多 10 轮工具调用。完成以下所有步骤后立即停止并输出最终结果：
            1. 已查询目的地天气
            2. 已搜索并验证足够的景点（每天至少 2-3 个）
            3. 已搜索必要的餐饮/住宿信息
            4. 已完成 SelfCheck 自检
            信息足够后直接输出 final_output，不要再调用工具。

            【SelfCheck 自检（输出前必须完成）】
            Thought: SelfCheck:
            OK totalDays 等于用户请求天数 -> [请求N天, 输出M天, 一致/不一致]
            OK dailyPlans 数量等于 totalDays -> [dailyPlans有X项, totalDays=Y]
            OK 每个 dayNumber 不重复且连续从1到totalDays -> [已检查]
            OK 每天至少3个行程项 -> [实际: Day1=X项 ...]
            OK 所有地点名称来自工具返回 -> [已验证]
            OK 所有坐标来自工具返回 -> [已验证]
            OK 无模糊描述 -> [全部具体]

            【最终输出格式】
            用 final_output 标签包裹纯 JSON：
            final_output
            {"title":"...", "estimatedCost":1500.0, "totalDays":N, "dailyPlans":[{"dayNumber":1, "items":[{"type":"景点|餐饮|住宿|购物","name":"具体地点名称","description":"详细描述（30字以内）","location":{"lat":39.9163,"lng":116.3971,"name":"位置名称"}}]}]}
            /final_output
            其中 N 必须严格等于用户 request 中的 days 字段值。
            final_output 标签外的任何内容都不会进入最终结果。""";

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

            请输出审核结论，格式：
            {"status":"PASS","issues":[],"summary":"总体评价..."}
            如果 status 为 FAIL，则 issues 数组中指明具体哪些天、哪些项有问题。""";

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

    private GenerationResult callLlmForItinerary(ItineraryRequest request,
            java.util.function.Consumer<String> toolNotifier) {
        GenerationStats stats = new GenerationStats();
        String amapUrl = amapMcpUrl;
        try (AmapMcpClient mcpClient = new AmapMcpClient(amapUrl)) {
            mcpClient.initialize();

            JsonNode mcpTools = mcpClient.listTools();
            JsonNode tools = mergeTools(mcpTools);
            String userInput = buildUserInputJson(request);

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

            // ========== Phase 2: EXECUTE ==========
            sendPhaseNotification(toolNotifier, "EXECUTE_START",
                    "开始按规划执行，调用工具查询真实数据...");

            ItineraryPlan draftPlan;
            int executeRetries = 0;
            int maxExecuteRetries = 2;
            while (true) {
                List<Map<String, Object>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content",
                        EXECUTE_SYSTEM_PROMPT_PREFIX + "\n\n【已制定的执行规划，请严格遵循】\n" + planContent));
                messages.add(Map.of("role", "user", "content", userInput));

                try {
                    draftPlan = runAgentLoop(messages, tools, mcpClient, request, toolNotifier, stats);
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

    private String buildUserInputJson(ItineraryRequest request) {
        Map<String, Object> input = new HashMap<>();
        input.put("request", request);
        return toJsonString(input);
    }

    private ItineraryPlan runAgentLoop(List<Map<String, Object>> messages, JsonNode tools,
            AmapMcpClient mcpClient, ItineraryRequest request,
            java.util.function.Consumer<String> toolNotifier, GenerationStats stats) throws Exception {
        int maxTurns = 10;
        for (int i = 0; i < maxTurns; i++) {
            System.out.println("Agent turn " + (i + 1));
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
                    while (true) {
                        try {
                            if (name.isEmpty()) {
                                result = "Error: Tool name is missing";
                            } else if (isCustomTool(name)) {
                                result = executeCustomTool(name, args);
                                if (result != null && result.length() > 5000) {
                                    result = result.substring(0, 5000) + "...";
                                }
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
                            System.err.println("Tool " + name + " failed (attempt " + toolRetries + "): " + errMsg);
                            if (toolRetries >= maxToolRetries) {
                                result = "Error executing tool " + name + " after " + toolRetries + " attempts: " + errMsg
                                        + ". 建议更换关键词或使用其他工具替代。";
                                if (toolNotifier != null) {
                                    try {
                                        toolNotifier.accept("THOUGHT:工具 " + name + " 执行失败(" + toolRetries + "次重试)，将告知Agent更换策略。");
                                    } catch (Exception ignored) {}
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
                    System.out.println("Agent turn " + (i + 1) + " output truncated (finish_reason=length), retrying with larger max_tokens...");
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
                    System.out.println("Agent turn " + (i + 1) + " produced thought without final output, continuing...");
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
                    System.out.println("Parse failed in agent turn " + (i + 1) + ": " + parseErr.getMessage()
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
        // maxTurns exhausted: force LLM to produce final answer
        System.out.println("Max turns reached, forcing final answer via streaming...");
        Map<String, Object> forceMsg = new HashMap<>();
        forceMsg.put("role", "user");
        forceMsg.put("content", "你已经完成了所有必要的查询。现在请立即输出完整的行程JSON，用final_output包裹。不要再调用任何工具，直接输出最终结果。");
        messages.add(forceMsg);
        if (toolNotifier != null) {
            try {
                toolNotifier.accept("THOUGHT:已达到最大轮次限制，正在通过流式模式强制生成最终结果...");
            } catch (Exception ignored) {}
        }
        JsonNode finalResponse;
        try {
            finalResponse = callLlmChatStreaming(messages);
        } catch (Exception streamingErr) {
            System.out.println("Streaming failed at max turns, falling back to normal call: " + streamingErr.getMessage());
            finalResponse = callLlmChat(messages, null, 16384);
        }
        accumulateUsageFromResponse(finalResponse, stats);
        stats.setExecuteCalls(stats.getExecuteCalls() + 1);
        JsonNode finalChoices = finalResponse.path("choices");
        if (!finalChoices.isArray() || finalChoices.isEmpty()) {
            throw new IntellitravelException("AI 认真思考了太久，暂时无法完成复杂的规划，请换个简单点的要求试试吧～");
        }

        // Check finish_reason and retry if truncated
        String finalFinishReason = finalChoices.get(0).path("finish_reason").asText("");
        if ("length".equals(finalFinishReason)) {
            System.out.println("Force output truncated, retrying without max_tokens limit...");
            if (toolNotifier != null) {
                toolNotifier.accept("THOUGHT:最终输出被截断，尝试无上限重新生成...");
            }
            JsonNode retryResponse = callLlmChat(messages, null, null);
            accumulateUsageFromResponse(retryResponse, stats);
            stats.setExecuteCalls(stats.getExecuteCalls() + 1);
            JsonNode retryChoices = retryResponse.path("choices");
            if (retryChoices.isArray() && !retryChoices.isEmpty()) {
                finalChoices = retryChoices;
            }
        }

        String finalContent = finalChoices.get(0)
                .path("message").path("content").asText("");
        ItineraryPlan plan = parseItinerary(finalContent);
        if (plan != null && plan.getDailyPlans() != null && !plan.getDailyPlans().isEmpty()) {
            if (plan.getDestination() == null && request.getDestination() != null) {
                plan.setDestination(request.getDestination());
            }
            return normalizePlan(plan, request, toolNotifier);
        }
        throw new IntellitravelException("AI 认真思考了太久，暂时无法完成复杂的规划，请换个简单点的要求试试吧～");
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

                // If LLM was asked to correct, try parse corrected plan from final_output
                if (retryCount > 0 && content.contains("final_output")) {
                    try {
                        ItineraryPlan corrected = parseItinerary(content);
                        if (corrected != null && corrected.getDailyPlans() != null
                                && !corrected.getDailyPlans().isEmpty()) {
                            currentPlan = corrected;
                        }
                    } catch (Exception ex) {
                    }
                }

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
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + llmApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        try {
            HttpResponse<String> response = streamingClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                String errBody = response.body() != null ? response.body() : "";
                throw new IntellitravelException("AI 流式请求失败 (HTTP " + response.statusCode() + "): " +
                        (errBody.length() > 300 ? errBody.substring(0, 300) + "..." : errBody));
            }

            StringBuilder contentBuilder = new StringBuilder();
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
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            String fullContent = contentBuilder.toString();
            if (fullContent.isBlank()) {
                throw new IntellitravelException("AI 流式响应未产生任何内容");
            }

            ObjectNode responseNode = objectMapper.createObjectNode();
            ArrayNode choicesArr = responseNode.putArray("choices");
            ObjectNode choice = choicesArr.addObject();
            choice.put("index", 0);
            ObjectNode msg = choice.putObject("message");
            msg.put("role", "assistant");
            msg.put("content", fullContent);
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
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            return client.send(postReq, HttpResponse.BodyHandlers.ofString());
        }

        @Override
        public void close() {
            if (sseTask != null) {
                sseTask.cancel(true);
            }
        }
    }
}
