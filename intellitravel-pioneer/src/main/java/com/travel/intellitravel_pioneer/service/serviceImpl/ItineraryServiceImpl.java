package com.travel.intellitravel_pioneer.service.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.ItineraryPO;
import com.travel.intellitravel_pioneer.repository.ItineraryRepository;
import com.travel.intellitravel_pioneer.service.ConversationService;
import com.travel.intellitravel_pioneer.service.ItineraryService;
import com.travel.intellitravel_pioneer.service.ai.TripPlanningAiClient;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.conversation.AgentMessageCreateRequest;
import com.travel.intellitravel_pioneer.vo.conversation.ConversationCreateRequest;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryRequest;
import com.travel.intellitravel_pioneer.vo.itinerary.ItinerarySaveRequest;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryVO;
import com.travel.intellitravel_pioneer.vo.trip.GenerationResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ItineraryServiceImpl implements ItineraryService {

    @Autowired
    private TripPlanningAiClient tripPlanningAiClient;

    @Autowired
    private ItineraryRepository itineraryRepository;

    @Autowired
    private TokenUtil tokenUtil;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public ItineraryPlan modifyItinerary(String token, ItineraryRequest request,
            ItineraryPlan currentPlan, String userPrompt, String conversationId) {
        GenerationResult result = modifyItineraryWithStats(token, request, currentPlan, userPrompt, null, conversationId);
        return result != null ? result.getPlan() : null;
    }

    @Override
    public GenerationResult modifyItineraryWithStats(String token, ItineraryRequest request,
            ItineraryPlan currentPlan, String userPrompt,
            java.util.function.Consumer<String> toolNotifier, String conversationId) {
        if (token == null || token.isBlank()) {
            throw new IntellitravelException("token is required");
        }
        if (request == null || currentPlan == null) {
            throw new IntellitravelException("request and currentPlan are required");
        }

        boolean reuseExisting = conversationId != null && !conversationId.isBlank();
        ConversationContext ctx;
        if (reuseExisting) {
            ctx = reuseConversation(token, conversationId, request, userPrompt);
        } else {
            ctx = createConversation(token, request, true);
        }
        java.util.function.Consumer<String> wrappedNotifier = buildNotifier(ctx, toolNotifier);

        // 意图分类已移至 HttpTripPlanningAiClient.callLlmForRefine() 中由AI处理
        // 这里直接调用 modifyItineraryWithStats，由底层AI判断是聊天还是修改

        try {
            GenerationResult result = tripPlanningAiClient.modifyItineraryWithStats(
                    token, request, currentPlan, userPrompt, wrappedNotifier, conversationId);
            
            // 如果返回的plan为null，说明是聊天回答
            if (result.getPlan() == null && result.getChatAnswer() != null) {
                // 保存聊天回答到对话
                if (ctx.conversationId != null) {
                    try {
                        conversationService.addMessage(token, ctx.conversationId,
                                AgentMessageCreateRequest.builder()
                                        .role("assistant")
                                        .content(result.getChatAnswer())
                                        .build());
                    } catch (Exception ignored) {}
                }
                if (toolNotifier != null) {
                    toolNotifier.accept("THOUGHT:" + (result.getChatAnswer().length() > 200 
                        ? result.getChatAnswer().substring(0, 200) + "..." : result.getChatAnswer()));
                }
                result.setConversationId(ctx.conversationId);
                return result;
            }
            
            finalizeConversation(ctx, token, result.getPlan(), true, result.getChatAnswer());
            result.setConversationId(ctx.conversationId);
            return result;
        } catch (Exception e) {
            saveErrorMessage(ctx, token, e.getMessage());
            throw new IntellitravelException("Failed to modify itinerary: " + e.getMessage());
        }
    }

    @Override
    public ItineraryPlan generateItinerary(String token, ItineraryRequest request) {
        GenerationResult result = generateItineraryWithStats(token, request, null);
        return result != null ? result.getPlan() : null;
    }

    @Override
    public GenerationResult generateItineraryWithStats(String token, ItineraryRequest request,
            java.util.function.Consumer<String> toolNotifier) {
        if (token == null || token.isBlank()) {
            throw new IntellitravelException("token is required");
        }
        if (request == null) {
            throw new IntellitravelException("request body is required");
        }

        // 复用已有对话（纯聊天场景下后续消息不再创建新对话）
        boolean reuseExisting = request.getConversationId() != null && !request.getConversationId().isBlank();
        ConversationContext ctx;
        if (reuseExisting) {
            ctx = reuseConversation(token, request.getConversationId(), request, request.getUserPrompt());
        } else {
            ctx = createConversation(token, request, false);
        }
        java.util.function.Consumer<String> wrappedNotifier = buildNotifier(ctx, toolNotifier);

        try {
            GenerationResult result = tripPlanningAiClient.generateItineraryWithStats(
                    token, request, wrappedNotifier);

            // 如果是聊天回答（plan为null），保存实际聊天内容而非模板摘要
            if (result.getPlan() == null && result.getChatAnswer() != null) {
                if (ctx.conversationId != null) {
                    try {
                        conversationService.addMessage(token, ctx.conversationId,
                                AgentMessageCreateRequest.builder()
                                        .role("assistant")
                                        .content(result.getChatAnswer())
                                        .build());
                    } catch (Exception ignored) {}
                }
                result.setConversationId(ctx.conversationId);
                return result;
            }

            finalizeConversation(ctx, token, result.getPlan(), false, result.getChatAnswer());
            result.setConversationId(ctx.conversationId);
            return result;
        } catch (Exception e) {
            saveErrorMessage(ctx, token, e.getMessage());
            throw new IntellitravelException("Failed to generate itinerary: " + e.getMessage());
        }
    }

    private static class ConversationContext {
        final String conversationId;
        final List<String> bufferedThoughts = new ArrayList<>();
        final List<String> bufferedPhaseMsgs = new ArrayList<>();
        final List<String> bufferedToolCalls = new ArrayList<>();
        int thoughtCount = 0;
        int toolCount = 0;

        ConversationContext(String conversationId) {
            this.conversationId = conversationId;
        }
    }

    private ConversationContext createConversation(String token, ItineraryRequest request, boolean isModify) {
        try {
            var convVO = conversationService.createConversation(token, ConversationCreateRequest.builder()
                    .title(buildConversationTitle(request, isModify))
                    .tags(buildConversationTags(request, isModify))
                    .description(buildConversationDescription(request, isModify))
                    .build());

            // 保存用户消息：优先使用原始用户输入（userPrompt），保留实际对话内容
            String userContent = buildNiceUserMessage(request, isModify, request.getUserPrompt());
            conversationService.addMessage(token, convVO.getId(),
                    AgentMessageCreateRequest.builder()
                            .role("user")
                            .content(userContent)
                            .build());

            return new ConversationContext(convVO.getId());
        } catch (Exception e) {
            System.err.println("[Conversation] Failed to create: " + e.getMessage());
            return new ConversationContext(null);
        }
    }

    private ConversationContext reuseConversation(String token, String conversationId,
            ItineraryRequest request, String userPrompt) {
        try {
            String userContent = buildNiceUserMessage(request, true, userPrompt);
            conversationService.addMessage(token, conversationId,
                    AgentMessageCreateRequest.builder()
                            .role("user")
                            .content(userContent)
                            .build());

            return new ConversationContext(conversationId);
        } catch (Exception e) {
            System.err.println("[Conversation] Failed to reuse: " + e.getMessage());
            return new ConversationContext(null);
        }
    }

    private java.util.function.Consumer<String> buildNotifier(
            ConversationContext ctx,
            java.util.function.Consumer<String> originalNotifier) {

        boolean hasOriginal = originalNotifier != null;

        return message -> {
            if (hasOriginal) {
                originalNotifier.accept(message);
            }

            if (ctx.conversationId == null) return;

            if (message.startsWith("PHASE:")) {
                ctx.bufferedPhaseMsgs.add(extractPhaseContent(message));
            } else if (message.startsWith("THOUGHT:")) {
                String thought = message.substring(8);
                ctx.bufferedThoughts.add(thought);
                ctx.thoughtCount++;
            } else if (!message.isBlank()) {
                ctx.bufferedToolCalls.add(truncate(message, 300));
                ctx.toolCount++;
            }
        };
    }

    private void finalizeConversation(ConversationContext ctx, String token,
            ItineraryPlan plan, boolean isModify, String chatAnswer) {
        if (ctx.conversationId == null) return;
        try {
            String summary = buildAiSummary(plan, ctx, isModify, chatAnswer);
            conversationService.addMessage(token, ctx.conversationId,
                    AgentMessageCreateRequest.builder()
                            .role("assistant")
                            .content(summary)
                            .build());

            if (plan != null) {
                String planJson = objectMapper.writeValueAsString(plan);
                conversationService.addMessage(token, ctx.conversationId,
                        AgentMessageCreateRequest.builder()
                                .role("system")
                                .content("行程方案已生成")
                                .toolCalls(planJson)
                                .build());
            }
        } catch (Exception e) {
            System.err.println("[Conversation] Failed to finalize: " + e.getMessage());
        }
    }

    private String buildAiSummary(ItineraryPlan plan, ConversationContext ctx, boolean isModify, String chatAnswer) {
        // 如果有AI生成的聊天回答（包含自定义规则），直接使用
        if (chatAnswer != null && !chatAnswer.isBlank()) {
            return chatAnswer;
        }
        
        // 否则使用固定模板
        StringBuilder sb = new StringBuilder();
        if (isModify) {
            sb.append("✅ 行程已根据你的要求优化！你可以在右侧画布中查看更新后的行程。");
        } else {
            sb.append("✅ 行程方案已生成！你可以在右侧画布中查看和编辑。");
        }
        if (ctx.thoughtCount > 0) {
            sb.append("（本次规划共进行 ").append(ctx.thoughtCount)
                    .append(" 轮思考，调用 ").append(ctx.toolCount).append(" 次工具查询）");
        }
        return sb.toString();
    }

    private void saveErrorMessage(ConversationContext ctx, String token, String errorMsg) {
        if (ctx.conversationId == null) return;
        try {
            conversationService.addMessage(token, ctx.conversationId,
                    AgentMessageCreateRequest.builder()
                            .role("system")
                            .content(" 规划过程出现错误：" + errorMsg)
                            .build());
        } catch (Exception ignored) {
        }
    }

    private String buildNiceUserMessage(ItineraryRequest request, boolean isModify, String userPrompt) {
        // 优先保存用户实际输入的原始文本，保持对话历史的真实性
        if (userPrompt != null && !userPrompt.isBlank()) {
            return userPrompt.trim();
        }

        // 降级：没有原始用户输入时，使用格式化模板
        StringBuilder sb = new StringBuilder();
        if (isModify) {
            sb.append("请帮我修改已有的行程方案。");
            return sb.toString().trim();
        } else {
            sb.append("请帮我规划旅行方案。\n");
        }
        if (request.getDestination() != null && !request.getDestination().isBlank()) {
            sb.append("目的地：").append(request.getDestination()).append("\n");
        }
        if (request.getDays() != null && request.getDays() > 0) {
            sb.append("天数：").append(request.getDays()).append("天\n");
        }
        if (request.getBudgetLevel() != null && !request.getBudgetLevel().isBlank()) {
            sb.append("预算：").append(request.getBudgetLevel()).append("\n");
        }
        if (request.getInterests() != null && !request.getInterests().isEmpty()) {
            sb.append("兴趣偏好：").append(String.join("、", request.getInterests())).append("\n");
        }
        if (request.getAdditionalNotes() != null && !request.getAdditionalNotes().isBlank()) {
            sb.append("补充说明：").append(request.getAdditionalNotes()).append("\n");
        }
        return sb.toString().trim();
    }

    private String buildConversationTitle(ItineraryRequest request, boolean isModify) {
        // 优先使用用户输入的原始文本生成标题
        String userPrompt = request.getUserPrompt();
        if (userPrompt != null && !userPrompt.isBlank()) {
            String trimmed = userPrompt.trim();
            // 如果用户输入较短（<=30字符），直接作为标题
            if (trimmed.length() <= 30) {
                return isModify ? "修改：" + trimmed : trimmed;
            }
            // 如果用户输入较长，提取前30字符并添加省略号
            String shortTitle = trimmed.substring(0, 30) + "...";
            return isModify ? "修改：" + shortTitle : shortTitle;
        }
        
        // 降级：使用目的地和天数组合
        StringBuilder title = new StringBuilder();
        if (isModify) {
            title.append("修改行程 - ");
        } else {
            title.append("旅行规划 - ");
        }
        if (request.getDestination() != null && !request.getDestination().isBlank()) {
            title.append(request.getDestination());
        } else {
            title.append("新目的地");
        }
        if (request.getDays() != null && request.getDays() > 0) {
            title.append(" ").append(request.getDays()).append("天");
        }
        return title.toString();
    }

    private String buildConversationDescription(ItineraryRequest request, boolean isModify) {
        StringBuilder desc = new StringBuilder();
        if (isModify) {
            desc.append("对");
        }
        if (request.getDestination() != null && !request.getDestination().isBlank()) {
            desc.append(request.getDestination());
        } else {
            desc.append("某地");
        }
        if (request.getDays() != null && request.getDays() > 0) {
            desc.append(request.getDays()).append("天");
        }
        String budgetLabel = budgetLabel(request.getBudgetLevel());
        if (!budgetLabel.isEmpty()) {
            desc.append(budgetLabel);
        }
        desc.append("旅行");
        if (isModify) {
            desc.append("方案的调整需求");
        } else {
            desc.append("方案的规划讨论");
        }
        desc.append("。");
        if (request.getInterests() != null && !request.getInterests().isEmpty()) {
            desc.append("偏好：").append(String.join("、", request.getInterests())).append("。");
        }
        return desc.toString();
    }

    private String budgetLabel(String level) {
        if (level == null || level.isBlank()) return "";
        switch (level) {
            case "low": return "经济";
            case "medium": return "舒适";
            case "high": return "豪华";
            default: return "";
        }
    }

    private String buildConversationTags(ItineraryRequest request, boolean isModify) {
        List<String> tags = new ArrayList<>();
        tags.add(isModify ? "行程修改" : "旅行规划");
        if (request.getDestination() != null && !request.getDestination().isBlank()) {
            tags.add(request.getDestination().trim());
        }
        if (request.getDays() != null && request.getDays() > 0) {
            tags.add(request.getDays() + "天");
        }
        if (request.getBudgetLevel() != null && !request.getBudgetLevel().isBlank()) {
            switch (request.getBudgetLevel()) {
                case "low": tags.add("经济型"); break;
                case "medium": tags.add("舒适型"); break;
                case "high": tags.add("豪华型"); break;
                default: break;
            }
        }
        return String.join(",", tags);
    }

    private String extractPhaseContent(String phaseMsg) {
        String body = phaseMsg.substring(6);
        int colonIdx = body.indexOf(":");
        if (colonIdx > 0) {
            return body.substring(colonIdx + 1);
        }
        return body;
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }

    @Override
    public String saveItinerary(String token, ItinerarySaveRequest request) {
        if (token == null || token.isBlank()) {
            throw new IntellitravelException("token is required");
        }
        if (request == null) {
            throw new IntellitravelException("request body is required");
        }

        String userId = String.valueOf(tokenUtil.getUser(token).getId());

        ItineraryPlan plan = ItineraryPlan.builder()
                .title(request.getTitle())
                .estimatedCost(request.getEstimatedCost())
                .totalDays(request.getTotalDays())
                .destination(request.getDestination())
                .dailyPlans(request.getDailyPlans())
                .build();

        ItineraryPO itineraryPO = ItineraryPO.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .conversationId(request.getConversationId())
                .title(request.getTitle())
                .plan(plan)
                .stats(request.getStats())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        itineraryRepository.save(itineraryPO);
        return itineraryPO.getId();
    }

    @Override
    public void updateItinerary(String token, String itineraryId, ItinerarySaveRequest request) {
        if (token == null || token.isBlank()) {
            throw new IntellitravelException("token is required");
        }
        if (itineraryId == null || itineraryId.isBlank()) {
            throw new IntellitravelException("itineraryId is required");
        }
        if (request == null) {
            throw new IntellitravelException("plan data is required");
        }

        ItineraryPlan plan = ItineraryPlan.builder()
                .title(request.getTitle())
                .estimatedCost(request.getEstimatedCost())
                .totalDays(request.getTotalDays())
                .destination(request.getDestination())
                .dailyPlans(request.getDailyPlans())
                .build();

        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        ItineraryPO existingItinerary = itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new IntellitravelException("itinerary not found"));

        if (!existingItinerary.getUserId().equals(userId)) {
            throw new IntellitravelException("access denied: itinerary does not belong to user");
        }

        existingItinerary.setTitle(request.getTitle());
        existingItinerary.setPlan(plan);
        if (request.getConversationId() != null) {
            existingItinerary.setConversationId(request.getConversationId());
        }
        if (request.getStats() != null) {
            existingItinerary.setStats(request.getStats());
        }
        existingItinerary.setUpdateTime(LocalDateTime.now());
        itineraryRepository.save(existingItinerary);
    }

    @Override
    public ItineraryVO getItineraryDetail(String token, String itineraryId) {
        if (token == null || token.isBlank()) {
            throw new IntellitravelException("token is required");
        }
        if (itineraryId == null || itineraryId.isBlank()) {
            throw new IntellitravelException("itineraryId is required");
        }

        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        ItineraryPO itinerary = itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new IntellitravelException("itinerary not found"));

        if (!itinerary.getUserId().equals(userId)) {
            throw new IntellitravelException("access denied: itinerary does not belong to user");
        }

        return ItineraryVO.builder()
                .id(itinerary.getId())
                .userId(itinerary.getUserId())
                .conversationId(itinerary.getConversationId())
                .title(itinerary.getTitle())
                .destination(itinerary.getPlan() == null ? null : itinerary.getPlan().getDestination())
                .plan(itinerary.getPlan())
                .stats(itinerary.getStats())
                .createTime(itinerary.getCreateTime())
                .updateTime(itinerary.getUpdateTime())
                .build();
    }

    @Override
    public java.util.List<String> getAllItineraries(String token) {
        if (token == null || token.isBlank()) {
            throw new IntellitravelException("token is required");
        }

        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        return itineraryRepository.findByUserId(userId)
                .stream()
                .map(ItineraryPO::getId)
                .toList();
    }

    @Override
    public java.util.List<String> getItinerariesByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IntellitravelException("userId is required");
        }
        return itineraryRepository.findByUserId(userId).stream()
                .map(ItineraryPO::getId)
                .toList();
    }

    @Override
    public void deleteItinerary(String token, String itineraryId) {
        if (token == null || token.isBlank()) {
            throw new IntellitravelException("token is required");
        }
        if (itineraryId == null || itineraryId.isBlank()) {
            throw new IntellitravelException("itineraryId is required");
        }

        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        ItineraryPO existingItinerary = itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new IntellitravelException("itinerary not found"));

        if (!existingItinerary.getUserId().equals(userId)) {
            throw new IntellitravelException("access denied: itinerary does not belong to user");
        }

        // 级联删除关联的对话历史
        String conversationId = existingItinerary.getConversationId();
        if (conversationId != null && !conversationId.isBlank()) {
            try {
                conversationService.deleteConversation(token, conversationId);
                System.out.println("[Itinerary] Cascade deleted conversation: " + conversationId);
            } catch (Exception e) {
                // 对话删除失败不影响行程删除，仅记录日志
                System.err.println("[Itinerary] Failed to cascade delete conversation " 
                    + conversationId + ": " + e.getMessage());
            }
        }

        itineraryRepository.deleteById(itineraryId);
    }
}
