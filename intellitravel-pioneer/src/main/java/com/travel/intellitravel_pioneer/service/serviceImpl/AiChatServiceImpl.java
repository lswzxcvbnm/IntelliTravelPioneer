package com.travel.intellitravel_pioneer.service.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.travel.intellitravel_pioneer.po.AgentConversationPO;
import com.travel.intellitravel_pioneer.po.AgentMessagePO;
import com.travel.intellitravel_pioneer.repository.AgentConversationRepository;
import com.travel.intellitravel_pioneer.repository.AgentMessageRepository;
import com.travel.intellitravel_pioneer.service.AiChatService;
import com.travel.intellitravel_pioneer.service.ConversationContextManager;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class AiChatServiceImpl implements AiChatService {

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

    private static final String CHAT_SYSTEM_PROMPT = """ 
            你是一个智能旅行助手，名叫 IntelliTravel Pioneer。你可以回答用户关于旅行计划、目的地推荐、行程建议、美食攻略、交通住宿等问题。\s

            重要规则：
            1. 用友好、热情的语气回答，就像一位有经验的旅行规划师
            2. 回答简洁有力，每次控制在200字以内
            3. 回答时不要虚构具体的行程编号（如"day1"），提供通用的旅行建议即可""";

    private static final String INTENT_CLASSIFY_SYSTEM = """ 
            你是一个意图分析器。分析用户消息的意图，只回复一个词：CHAT 或 MODIFY。

            CHAT：用户在进行普通对话、咨询旅行建议、询问攻略、了解目的地信息、闲聊等，不涉及修改任何具体行程。
            MODIFY：用户明确要求修改、增加、删除、替换、调整现有的行程方案中的某个项目或某一天的安排。

            只回复一个词。""";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TokenUtil tokenUtil;

    @Autowired
    private ConversationContextManager contextManager;

    @Autowired
    private AgentConversationRepository conversationRepository;

    @Autowired
    private AgentMessageRepository messageRepository;

    @Override
    public SseEmitter chatStream(String token, String message, String conversationId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        CompletableFuture.runAsync(() -> {
            try {
                String userId = tokenUtil.getUser(token).getId().toString();
                String convId = getOrCreateConversation(userId, conversationId, message);

                // 1. Save user message
                AgentMessagePO userMsg = AgentMessagePO.builder()
                        .id(UUID.randomUUID().toString())
                        .conversationId(convId)
                        .role("user")
                        .content(message)
                        .createTime(LocalDateTime.now())
                        .build();
                messageRepository.save(userMsg);

                // 2. AI intent classification — CHAT or MODIFY?
                String intent = classifyIntent(message);
                if ("MODIFY".equals(intent)) {
                    // User wants to modify itinerary — suggest using AI完善 instead
                    String suggestMsg = "这听起来像是在修改行程哦～请使用右侧面板的 **AI完善** 功能来调整行程方案，那里可以帮你精确地修改每一天的安排。继续聊天的话，我可以回答旅行相关的问题！😊";
                    saveAndSendFixedResponse(convId, suggestMsg, emitter);
                    return;
                }

                // 3. Build messages for LLM chat
                List<Map<String, Object>> messages = buildMessages(convId, token, message);

                // 4. Build request body
                ObjectNode body = objectMapper.createObjectNode();
                body.put("model", llmModel);
                body.put("temperature", llmTemperature);
                body.put("stream", true);
                body.set("messages", objectMapper.valueToTree(messages));

                String jsonBody = objectMapper.writeValueAsString(body);
                String requestUrl = llmBaseUrl + llmChatPath;

                // 5. Send streaming request and collect full response
                StringBuilder fullContent = new StringBuilder();

                RestClient.create()
                        .post()
                        .uri(requestUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(h -> h.setBearerAuth(llmApiKey))
                        .body(jsonBody)
                        .exchange((req, resp) -> {
                            // Read SSE stream
                            try (var reader = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(resp.getBody()))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (line.startsWith("data: ")) {
                                        String data = line.substring(6).trim();
                                        if ("[DONE]".equals(data)) continue;
                                        try {
                                            JsonNode chunk = objectMapper.readTree(data);
                                            JsonNode choices = chunk.path("choices");
                                            if (choices.isArray() && choices.size() > 0) {
                                                JsonNode delta = choices.get(0).path("delta");
                                                JsonNode content = delta.path("content");
                                                if (!content.isMissingNode() && !content.asText().isEmpty()) {
                                                    String tokenText = content.asText();
                                                    fullContent.append(tokenText);
                                                    emitter.send(SseEmitter.event()
                                                            .name("token")
                                                            .data(tokenText));
                                                }
                                            }
                                        } catch (Exception ignored) {
                                            // Skip malformed chunks
                                        }
                                    }
                                }
                            }
                            return null;
                        });

                // 6. Save assistant response
                AgentMessagePO aiMsg = AgentMessagePO.builder()
                        .id(UUID.randomUUID().toString())
                        .conversationId(convId)
                        .role("assistant")
                        .content(fullContent.toString())
                        .createTime(LocalDateTime.now())
                        .build();
                messageRepository.save(aiMsg);

                // 7. Send done event
                ObjectNode doneData = objectMapper.createObjectNode();
                doneData.put("conversationId", convId);
                doneData.put("messageId", aiMsg.getId());
                emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(doneData)));
                emitter.complete();

            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("对话失败：" + e.getMessage()));
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    private String getOrCreateConversation(String userId, String existingConvId, String firstMessage) {
        if (existingConvId != null && !existingConvId.isBlank()) {
            Optional<AgentConversationPO> existing = conversationRepository.findById(existingConvId);
            if (existing.isPresent() && existing.get().getUserId().equals(userId)) {
                return existingConvId;
            }
        }
        // Create new conversation
        String title = firstMessage.length() > 30 ? firstMessage.substring(0, 30) + "..." : firstMessage;
        AgentConversationPO conv = AgentConversationPO.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .title(title)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        conversationRepository.save(conv);
        return conv.getId();
    }

    private List<Map<String, Object>> buildMessages(String conversationId, String token, String currentMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // System prompt
        Map<String, Object> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", CHAT_SYSTEM_PROMPT);
        messages.add(systemMsg);

        // Load history from context manager
        var ctx = contextManager.getOptimizedContext(conversationId, currentMessage, token);
        if (ctx != null && ctx.getMessages() != null) {
            for (var msg : ctx.getMessages()) {
                Map<String, Object> histMsg = new LinkedHashMap<>();
                histMsg.put("role", msg.getRole());
                histMsg.put("content", msg.getContent());
                messages.add(histMsg);
            }
        }

        return messages;
    }

    /**
     * Use AI to classify user intent as CHAT or MODIFY.
     * A lightweight non-streaming call — expected output is a single word.
     */
    private String classifyIntent(String userMessage) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", llmModel);
            body.put("temperature", 0.0);
            body.put("max_tokens", 10);

            ArrayNode msgs = body.putArray("messages");

            ObjectNode sysMsg = objectMapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", INTENT_CLASSIFY_SYSTEM);
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
            return "CHAT";
        } catch (Exception e) {
            System.err.println("Intent classification failed, defaulting to CHAT: " + e.getMessage());
            return "CHAT";
        }
    }

    /**
     * Save AI response and send it as token-by-token SSE stream.
     */
    private void saveAndSendFixedResponse(String conversationId, String content, SseEmitter emitter) throws Exception {
        // Save message
        AgentMessagePO aiMsg = AgentMessagePO.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .role("assistant")
                .content(content)
                .createTime(LocalDateTime.now())
                .build();
        messageRepository.save(aiMsg);

        // Stream response token-by-token for a natural feel
        for (int i = 0; i < content.length(); i++) {
            emitter.send(SseEmitter.event().name("token").data(String.valueOf(content.charAt(i))));
        }

        // Send done event
        ObjectNode doneData = objectMapper.createObjectNode();
        doneData.put("conversationId", conversationId);
        doneData.put("messageId", aiMsg.getId());
        emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(doneData)));
        emitter.complete();
    }
}
