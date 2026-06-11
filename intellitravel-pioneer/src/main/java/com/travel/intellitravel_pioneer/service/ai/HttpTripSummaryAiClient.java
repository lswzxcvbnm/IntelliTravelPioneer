package com.travel.intellitravel_pioneer.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.vo.trip.TripSummaryAiRequest;
import com.travel.intellitravel_pioneer.vo.trip.TripSummaryAiResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class HttpTripSummaryAiClient implements TripSummaryAiClient {

    private final ObjectMapper objectMapper;

    @Value("${trip.ai.provider}")
    private String aiProvider;

    @Value("${trip.ai.base-url}")
    private String aiBaseUrl;

    @Value("${trip.ai.summary-path}")
    private String aiSummaryPath;

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

    public HttpTripSummaryAiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public TripSummaryAiResult summarizeTrip(String token, TripSummaryAiRequest request) {
        if (isLlmProvider()) {
            return callLlmForTripSummary(request);
        }

        if (aiBaseUrl == null || aiBaseUrl.isBlank()) {
            throw new IntellitravelException("AI summary service is not configured");
        }

        String rawResponse;
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("trip_summary", request);

            rawResponse = RestClient.create()
                    .post()
                    .uri(aiBaseUrl + aiSummaryPath)
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
            throw new IntellitravelException("Failed to call AI trip summary service: " + e.getMessage());
        }

        return parseTripSummary(rawResponse);
    }

    private boolean isLlmProvider() {
        return "llm".equalsIgnoreCase(aiProvider);
    }

    private TripSummaryAiResult callLlmForTripSummary(TripSummaryAiRequest request) {
        Map<String, Object> input = new HashMap<>();
        input.put("trip_summary_context", request);
        input.put("output_schema", "{\"title\":\"string\",\"summary\":\"string\"}");

        String prompt = "你是旅行总结助手，请基于给定的真实行程数据生成一次中文旅行总结。\n"
            + "你只能依据输入中的 itinerary_raw、itinerary_daily_outline、planned_highlights、sampled_trajectory、final_status、final_location_text、nearby_pois 信息输出，不得编造未提供的到访事实或具体体验。\n"
            + "请明确区分“计划内容”和“实际轨迹/状态”：planned_highlights 与 itinerary_daily_outline 是原计划，不代表实际已到达。\n"
            + "nearby_pois 是终点附近环境线索，可用于增加场景感，但不能写成“用户一定去了这些点”。\n"
            + "请重点综合：行程主题、计划亮点、轨迹长度与节奏、持续时长、步数、体力变化、终点环境。\n"
            + "表达风格要求：语言自然、生动、有画面感，像一位贴心的旅行教练在复盘；语气积极但不过度夸张。\n"
                + "输出要求：\n"
                + "1) 仅输出合法 JSON，不要输出解释、注释、markdown；\n"
                + "2) title 为自动生成标题，8-18 个中文字符，具体自然；\n"
            + "3) summary 为 150-260 字中文，风格简洁、有画面感，但必须忠于数据；\n"
            + "4) summary 里至少包含一句“计划与实际”的对照描述；\n"
            + "5) 当轨迹点很少或步数很低时，不要硬写成完整游玩经历，应保守描述为短时/试运行/未展开行程；\n"
            + "6) 无论活动量高低，结尾都给出一句温和、具体、可执行的鼓励建议（例如下一步可从附近1个点位开始，或先完成10-15分钟步行热身）；\n"
            + "7) 避免模板化空话，避免连续使用“整体”“较为”等机械表达；\n"
            + "8) 若数据不足，允许保守概括，不要虚构。";

        JsonNode root = callLlm(prompt, input);
        return parseTripSummary(root.toString());
    }

    private JsonNode callLlm(String systemPrompt, Map<String, Object> input) {
        if (llmApiKey == null || llmApiKey.isBlank()) {
            throw new IntellitravelException("GPT API key is not configured");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", llmModel);
        body.put("temperature", llmTemperature);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", toJsonString(input))));

        String raw;
        try {
            raw = RestClient.create()
                    .post()
                    .uri(llmBaseUrl + llmChatPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(llmApiKey))
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new IntellitravelException("Failed to call GPT API: " + e.getMessage());
        }

        try {
            JsonNode chatRoot = objectMapper.readTree(raw);
            JsonNode contentNode = chatRoot.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                throw new IntellitravelException("GPT response does not contain message content");
            }
            String jsonText = stripCodeFence(contentNode.asText());
            return objectMapper.readTree(jsonText);
        } catch (IntellitravelException e) {
            throw e;
        } catch (Exception e) {
            throw new IntellitravelException("Failed to parse GPT response: " + e.getMessage());
        }
    }

    private TripSummaryAiResult parseTripSummary(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode summaryNode = root;

            if (root.has("data") && root.get("data") != null) {
                JsonNode dataNode = root.get("data");
                summaryNode = dataNode.has("summary") ? dataNode.get("summary") : dataNode;
            } else if (root.has("summary") && root.get("summary") != null && root.get("summary").isObject()) {
                summaryNode = root.get("summary");
            }

            String title = summaryNode.path("title").asText(null);
            String summary = summaryNode.path("summary").asText(null);
            if (title == null || title.isBlank() || summary == null || summary.isBlank()) {
                throw new IntellitravelException("AI response does not contain valid trip summary");
            }

            TripSummaryAiResult result = new TripSummaryAiResult();
            result.setTitle(title.trim());
            result.setSummary(summary.trim());
            return result;
        } catch (IntellitravelException e) {
            throw e;
        } catch (Exception e) {
            throw new IntellitravelException("Failed to parse AI trip summary result: " + e.getMessage());
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



    //任务二
    @Override
    public String summarizeText(String text) {
        if (text == null || text.isBlank()) {
            return "无对话内容";
        }

        // 限制输入长度，避免 Token 超限
        String truncated = text.length() > 3000 ? text.substring(0, 3000) + "..." : text;

        String systemPrompt = "你是对话摘要助手。从以下对话中提取关键信息，只输出纯文本，不要JSON格式。\n" +
                "需要提取的信息：\n" +
                "- 目的地\n- 天数\n- 预算等级（经济/舒适/豪华）\n" +
                "- 用户偏好（历史/美食/自然等）\n" +
                "- 已确认的行程安排\n\n" +
                "没有的信息就跳过。输出控制在200字以内。";

        try {
            String response = callLlmDirectly(systemPrompt, truncated);
            return extractContentFromResponse(response);
        } catch (Exception e) {
            System.err.println("LLM summary failed: " + e.getMessage());
            return fallbackExtract(truncated);
        }
    }

    private String callLlmDirectly(String systemPrompt, String userText) {
        if (llmApiKey == null || llmApiKey.isBlank()) {
            throw new IntellitravelException("LLM API key is not configured");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", llmModel);
        body.put("temperature", 0.3);
        body.put("max_tokens", 500);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userText)
        ));

        String requestUrl = llmBaseUrl + llmChatPath;

        String response = RestClient.create()
                .post()
                .uri(requestUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(llmApiKey))
                .body(body)
                .retrieve()
                .body(String.class);

        return response;
    }

    private String extractContent(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            return content != null && !content.isBlank() ? content.trim() : "无摘要内容";
        } catch (Exception e) {
            return "无摘要内容";
        }
    }

    private String fallbackExtract(String text) {
        StringBuilder summary = new StringBuilder();

        // 提取目的地
        java.util.regex.Pattern destPattern = java.util.regex.Pattern.compile("目的地[：:]\\s*([\\u4e00-\\u9fa5]+)|去([\\u4e00-\\u9fa5]{2,5})");
        java.util.regex.Matcher destMatcher = destPattern.matcher(text);
        if (destMatcher.find()) {
            String dest = destMatcher.group(1) != null ? destMatcher.group(1) : destMatcher.group(2);
            summary.append("目的地：").append(dest).append("。");
        }

        // 提取天数
        java.util.regex.Pattern dayPattern = java.util.regex.Pattern.compile("(\\d+)[天日]");
        java.util.regex.Matcher dayMatcher = dayPattern.matcher(text);
        if (dayMatcher.find()) {
            summary.append("天数：").append(dayMatcher.group(1)).append("天。");
        }

        // 提取预算
        if (text.contains("经济")) summary.append("预算：经济型。");
        else if (text.contains("舒适")) summary.append("预算：舒适型。");
        else if (text.contains("豪华")) summary.append("预算：豪华型。");

        // 提取兴趣
        if (text.contains("历史") || text.contains("文化")) summary.append("偏好：历史文化。");
        else if (text.contains("美食")) summary.append("偏好：美食。");
        else if (text.contains("自然") || text.contains("徒步")) summary.append("偏好：自然风光。");

        if (summary.length() == 0) {
            return "用户正在规划旅行。";
        }
        return summary.toString();
    }
    private String extractContentFromResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            return content != null && !content.isBlank() ? content.trim() : "无摘要内容";
        } catch (Exception e) {
            return "无摘要内容";
        }
    }
}