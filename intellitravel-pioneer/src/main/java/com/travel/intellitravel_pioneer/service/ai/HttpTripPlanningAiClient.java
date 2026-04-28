package com.travel.intellitravel_pioneer.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryRequest;
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

    @Value("${trip.ai.llm.model:Pro/zai-org/GLM-5}")
    private String llmModel;

    @Value("${trip.ai.llm.temperature:0.2}")
    private Double llmTemperature;

    public HttpTripPlanningAiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ItineraryPlan generateItinerary(String token, ItineraryRequest request) {
        return generateItinerary(token, request, null);
    }

    @Override
    public ItineraryPlan generateItinerary(String token, ItineraryRequest request,
            java.util.function.Consumer<String> toolNotifier) {
        if (isLlmProvider()) {
            return callLlmForItinerary(request, toolNotifier);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("request", request);

        String rawResponse = callCustomAiService(aiPlanPath, token, requestBody);
        return parseItinerary(rawResponse);
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

    private ItineraryPlan callLlmForItinerary(ItineraryRequest request,
            java.util.function.Consumer<String> toolNotifier) {
        String amapUrl = "https://mcp.amap.com/sse?key=bc02d1f93e142eb512f2a9d058d32560";
        try (AmapMcpClient mcpClient = new AmapMcpClient(amapUrl)) {
            mcpClient.initialize();

            JsonNode tools = mcpClient.listTools();

            List<Map<String, Object>> messages = new ArrayList<>();
            // System prompt
            String systemInstructions = "你是一位专业的旅行规划助手。请根据用户请求，生成一个详细的行程计划。\n" +
                    "【工作流程与思考机制】\n" +
                    "1. 在调用任何工具之前，你必须先输出一个【思考过程】（Thought），简要分析当前需求，并解释为何需要调用该工具（例如验证天气、查询票价等）。\n" +
                    "   格式示例：\n" +
                    "   Thought: 用户想去北京，我需要先查询北京的天气情况来决定行程。\n" +
                    "   (随后发起工具调用)\n" +
                    "2. **强烈建议在一次回复中并行调用多个工具有效收集信息**。如果你需要验证多个景点、餐厅或住宿的经纬度与信息，请在一次回复中同时发起多个工具调用，不要一次只查一个。\n" +
                    "3. 必须使用提供的工具查询和验证每一个景点的真实性和经纬度。严禁伪造、猜测或生成不存在的地点名称和坐标。只有在工具返回结果中真实存在的地点，才能加入最终的行程计划。\n" +
                    "4. 如果工具调用失败或未找到信息，请在思考过程中说明情况，并尝试更换关键词重新搜索，绝不能凭空捏造地点。\n" +
                    "\n" +
                    "【最终交付物】\n" +
                    "当所有规划完成且不再调用工具时，请直接且仅输出最终的行程计划JSON。\n" +
                    "注意：\n" +
                    "1. 最终输出必须是纯粹合法的JSON字符串，严禁在JSON文本内部或周围夹杂Markdown代码块（如```json）、【思考过程】或任何其他解释性文字。\n" +
                    "2. 必须确保JSON格式完全合法。特别注意：所有的字符串值如果包含双引号必须进行转义，各个属性之间必须有逗号分隔！\n" +
                    "3. 规划中禁止使用泛指词汇，每一项具体行程必须为具体且真实存在的地点，严禁出现“当地特色餐厅”、“网红小吃店”这类模糊描述，并且必须提供真实准确的经纬度。\n" +
                    "4. 每天的行程项不得少于三项，最终规划中不得有重复的行程节点。\n" +
                    "JSON结构如下：\n" +
                    "{\n" +
                    "  \"title\": \"整个行程的标题，如：北京三日游\",\n" +
                    "  \"estimatedCost\": 1500.0, // 预算总花费\n" +
                    "  \"totalDays\": 3, // 总天数\n" +
                    "  \"dailyPlans\": [\n" +
                    "    {\n" +
                    "      \"dayNumber\": 1, // 第几天，依次为1,2,3...\n" +
                    "      \"items\": [\n" +
                    "        {\n" +
                    "          \"type\": \"景点|餐饮|住宿|购物\", // 只能填这4个中的1个\n" +
                    "          \"name\": \"具体的地点名称，如：故宫博物院\",\n" +
                    "          \"description\": \"该行程节点的详细描述，如游玩时间，门票，交通建议等\",\n" +
                    "          \"location\": {\n" +
                    "            \"lat\": 39.9163, // 必须是 Double 类型的真实纬度\n" +
                    "            \"lng\": 116.3971, // 必须是 Double 类型的真实经度\n" +
                    "            \"name\": \"位置名称\"\n" +
                    "          }\n" +
                    "        }\n" +
                    "      ]\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";

            messages.add(Map.of("role", "system", "content", systemInstructions));
            Map<String, Object> input = new HashMap<>();
            input.put("request", request);
            messages.add(Map.of("role", "user", "content", toJsonString(input)));

            // Agent Loop
            int maxTurns = 30; // 增加最大轮数以支持更多的搜索和验证
            for (int i = 0; i < maxTurns; i++) {
                System.out.println("Agent turn " + (i + 1));
                JsonNode response = callLlmChat(messages, tools);
                JsonNode choices = response.path("choices");
                if (choices.isEmpty()) {
                    throw new IntellitravelException("AI 没有返回任何内容，请稍候再试呀～");
                }
                JsonNode message = choices.get(0).path("message");

                Map<String, Object> assistantMsg = new HashMap<>();
                assistantMsg.put("role", message.path("role").asText());
                if (message.has("content") && !message.get("content").isNull()) {
                    assistantMsg.put("content", message.get("content").asText());
                }
                if (message.has("tool_calls")) {
                    assistantMsg.put("tool_calls", message.get("tool_calls"));
                }
                messages.add(assistantMsg);

                if (message.has("tool_calls")) {
                    JsonNode toolCalls = message.get("tool_calls");
                    for (JsonNode toolCall : toolCalls) {
                        String id = toolCall.path("id").asText();
                        JsonNode functionNode = toolCall.path("function");
                        String name = functionNode.path("name").asText();
                        String args = functionNode.path("arguments").asText("{}");

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
                        try {
                            if (name.isEmpty()) {
                                result = "Error: Tool name is missing";
                            } else {
                                result = mcpClient.callTool(name, args);
                                // 防止工具返回结果过长导致Token超出限制
                                if (result != null && result.length() > 5000) {
                                    result = result.substring(0, 5000) + "... [Result truncated due to length]";
                                }
                            }
                        } catch (Exception e) {
                            result = "Error executing tool " + name + ": " + e.toString();
                        }

                        if (toolNotifier != null) {
                            try {
                                toolNotifier.accept("Tool " + name + " completed");
                            } catch (Exception e) {
                                System.err.println("Tool notifier error: " + e.getMessage());
                            }
                        }

                        Map<String, Object> toolMsg = new HashMap<>();
                        toolMsg.put("role", "tool");
                        toolMsg.put("tool_call_id", id);
                        toolMsg.put("name", name);
                        toolMsg.put("content", result);
                        messages.add(toolMsg);
                    }
                } else {
                    // Final answer found
                    String content = message.path("content").asText();
                    ItineraryPlan plan = parseItinerary(content);
                    if (plan != null && plan.getDestination() == null && request.getDestination() != null) {
                        plan.setDestination(request.getDestination());
                    }
                    return plan;
                }
            }
            throw new IntellitravelException("AI 认真思考了太久，暂时无法完成复杂的规划，请换个简单点的要求试试吧～");

        } catch (IntellitravelException ie) {
            throw ie;
        } catch (Exception e) {
            e.printStackTrace();
            throw new IntellitravelException("啊哦，生成行程时遇到了小阻碍（网络不稳定或服务器繁忙），请稍后重试哦～");
        }
    }

    private String toJsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IntellitravelException("Failed to build GPT request: " + e.getMessage());
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

        String cleaned = stripCodeFence(rawResponse);
        String jsonToParse = cleaned;

        // 移除可能的思考过程标记（Thought: ...）
        // 查找最后一个完整的 JSON 对象
        int firstBrace = -1;
        int lastBrace = -1;
        int braceCount = 0;
        int inString = 0; // 0 = not in string, 1 = in string
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

        // 尝试修复常见的 JSON 格式问题
        jsonToParse = fixJsonIssues(jsonToParse);

        try {
            JsonNode root = objectMapper.readTree(jsonToParse);
            // 直接尝试解析为ItineraryPlan（期望根节点就是行程计划对象）
            ItineraryPlan plan = objectMapper.treeToValue(root, ItineraryPlan.class);
            return plan;
        } catch (Exception e) {
            // 如果直接解析失败，可以尝试回退到旧格式（兼容性处理）
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

    private String fixJsonIssues(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        // 移除 JSON 中的注释（// 和 /* */）
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

            // 只在非字符串内处理注释
            if (!inString) {
                // 检查单行注释 //
                if (i + 1 < json.length() && json.charAt(i) == '/' && json.charAt(i + 1) == '/') {
                    // 跳过直到行尾
                    while (i < json.length() && json.charAt(i) != '\n') {
                        i++;
                    }
                    continue;
                }
                // 检查多行注释 /* */
                if (i + 1 < json.length() && json.charAt(i) == '/' && json.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < json.length() && !(json.charAt(i) == '*' && json.charAt(i + 1) == '/')) {
                        i++;
                    }
                    i += 2; // 跳过 */
                    continue;
                }
                // 检查尾随逗号（对象中）
                if (c == ',' && i + 1 < json.length()) {
                    int j = i + 1;
                    while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
                        j++;
                    }
                    if (j < json.length() && (json.charAt(j) == '}' || json.charAt(j) == ']')) {
                        // 跳过尾随逗号
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
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", llmModel);
        body.put("temperature", llmTemperature);
        body.set("messages", objectMapper.valueToTree(messages));

        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
        }

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IntellitravelException("准备 AI 请求数据失败，请重试～");
        }

        String raw = executeGptRequest(jsonBody);

        try {
            // 首先检查返回的是否是有效的 JSON
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

                // 检查响应是否是 HTML（可能是错误页面）
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
                // 4xx 错误
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
                // 5xx 错误
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
            // 1. Connect to SSE and find endpoint
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
                                // Dispatch event
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
                                                    // Convert MCP error to Exception if easier, or just pass the full
                                                    // node
                                                    // Here we pass the full node and let caller check "error"
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

                                // Reset
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
            // Append query parameters from original URL if not present in the new endpoint?
            // Usually the session ID is enough, but as seen in testing, original key might
            // be irrelevant if session is active.
            // But let's just resolve.
            this.postEndpoint = uri.resolve(relativeEndpoint).toString();

            // 2. Initialize MCP session
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
            // Convert to OpenAI Tool format
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

                // Wait for response from SSE
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
