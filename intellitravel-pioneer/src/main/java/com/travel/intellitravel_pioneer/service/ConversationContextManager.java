// ConversationContextManager.java
package com.travel.intellitravel_pioneer.service;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.travel.intellitravel_pioneer.service.ConversationService;
import com.travel.intellitravel_pioneer.service.ai.TripSummaryAiClient;
import com.travel.intellitravel_pioneer.vo.conversation.AgentMessageVO;

import lombok.Builder;
import lombok.Data;
@Component
public class ConversationContextManager {
    private static final Logger log = LoggerFactory.getLogger(ConversationContextManager.class);
    private static final int MAX_RECENT_MESSAGES = 10;      // 保留最近N条原始消息
    private static final int MAX_SUMMARY_MESSAGES = 5;     // 超过此数量触发摘要
    private static final int SUMMARY_TRIGGER_THRESHOLD = 15; // 触发摘要的消息阈值

    @Autowired
    @Lazy
    private ConversationService conversationService;

    @Autowired
    private TripSummaryAiClient summaryAiClient;


      //获取优化后的对话上下文
    public OptimizedContext getOptimizedContext(String conversationId, String currentUserMessage,String token) {
        List<AgentMessageVO> allMessages = conversationService.getConversationMessages(token, conversationId);

        log.info("【滑动窗口】对话ID: {}, 总消息数: {}, 阈值: {}",
                conversationId, allMessages.size(), MAX_SUMMARY_MESSAGES);

        if (allMessages.size() <= MAX_SUMMARY_MESSAGES) {
            log.info("【摘要压缩】未触发 - 消息数 {} <= 阈值 {}", allMessages.size(), MAX_SUMMARY_MESSAGES);
        } else {
            log.info("【摘要压缩】已触发 - 消息数 {} > 阈值 {}", allMessages.size(), MAX_SUMMARY_MESSAGES);
        }

        if (allMessages.size() <= MAX_SUMMARY_MESSAGES) {
            return OptimizedContext.builder()
                    .messages(allMessages)
                    .strategy(ContextStrategy.FULL)
                    .build();
        }
        List<AgentMessageVO> recentMessages = allMessages.stream()
                .skip(allMessages.size() - MAX_RECENT_MESSAGES)
                .collect(Collectors.toList());

        List<AgentMessageVO> historyMessages = allMessages.stream()
                .limit(allMessages.size() - MAX_RECENT_MESSAGES)
                .collect(Collectors.toList());
        log.info("【滑动窗口】保留最近 {} 条原始消息，历史 {} 条将被压缩",
                MAX_RECENT_MESSAGES, historyMessages.size());
        String historySummary = generateHistorySummary(conversationId, historyMessages);
        List<AgentMessageVO> optimizedMessages = new ArrayList<>();
        AgentMessageVO summaryMessage = AgentMessageVO.builder()
                .role("system")
                .content(buildSummaryContext(historySummary))
                .build();
        optimizedMessages.add(summaryMessage);
        optimizedMessages.addAll(recentMessages);

        return OptimizedContext.builder()
                .messages(optimizedMessages)
                .summary(historySummary)
                .strategy(ContextStrategy.SLIDING_WINDOW_WITH_SUMMARY)
                .originalCount(allMessages.size())
                .optimizedCount(optimizedMessages.size())
                .build();
    }

    private String generateHistorySummary(String conversationId, List<AgentMessageVO> historyMessages) {
        // 构建摘要请求
        StringBuilder sb = new StringBuilder();
        sb.append("请总结以下对话的关键信息，包括：\n");
        sb.append("- 用户提到的目的地、天数、预算\n");
        sb.append("- 用户已确认的行程安排\n");
        sb.append("- 用户提出的特殊需求或偏好\n");
        sb.append("- 双方已达成一致的决定\n\n");
        sb.append("对话内容：\n");

        for (AgentMessageVO msg : historyMessages) {
            sb.append(msg.getRole()).append(": ");
            if (msg.getRole().equals("user")) {
                sb.append(msg.getContent()).append("\n");
            } else {
                String content = msg.getContent();
                if (content != null && content.length() > 300) {
                    content = content.substring(0, 300) + "...";
                }
                sb.append(content).append("\n");
            }
        }
        return callLightweightLlmForSummary(sb.toString());
    }

    private String buildSummaryContext(String summary) {
        return "【对话历史摘要】以下是之前对话的关键信息摘要，请基于这些信息理解上下文：\n\n" + summary;
    }


    @Data
    @Builder
    public static class OptimizedContext {
        private List<AgentMessageVO> messages;
        private String summary;
        private ContextStrategy strategy;
        private int originalCount;
        private int optimizedCount;
    }

    public enum ContextStrategy {
        FULL,                    // 完整上下文
        SLIDING_WINDOW,          // 仅滑动窗口
        SLIDING_WINDOW_WITH_SUMMARY  // 滑动窗口+历史摘要
    }
    private String callLightweightLlmForSummary(String prompt) {
        try {
            String result = summaryAiClient.summarizeText(prompt);
            if (result != null && !result.isBlank() && !result.equals("无摘要内容")) {
                return result;
            }
        } catch (Exception e) {
            System.err.println("LLM summary failed: " + e.getMessage());
        }
        return extractSimpleSummary(prompt);
    }


    public List<AgentMessageVO> getShortTermMemory(String conversationId) {
        List<AgentMessageVO> allMessages = conversationService.getConversationMessages(null, conversationId);
        int maxSize = 10;
        if (allMessages.size() <= maxSize) {
            return allMessages;
        }
        return allMessages.subList(allMessages.size() - maxSize, allMessages.size());
    }

    private String extractSimpleSummary(String prompt) {
        StringBuilder summary = new StringBuilder();

        Pattern destPattern = Pattern.compile("目的地[：:]\\s*([\\u4e00-\\u9fa5]+)|去([\\u4e00-\\u9fa5]{2,5})");
        Matcher destMatcher = destPattern.matcher(prompt);
        if (destMatcher.find()) {
            String dest = destMatcher.group(1) != null ? destMatcher.group(1) : destMatcher.group(2);
            summary.append("目的地：").append(dest).append("。");
        }

        Pattern dayPattern = Pattern.compile("(\\d+)[天日]");
        Matcher dayMatcher = dayPattern.matcher(prompt);
        if (dayMatcher.find()) {
            summary.append("天数：").append(dayMatcher.group(1)).append("天。");
        }

        if (prompt.contains("经济")) summary.append("预算：经济型。");
        else if (prompt.contains("舒适")) summary.append("预算：舒适型。");
        else if (prompt.contains("豪华")) summary.append("预算：豪华型。");

        if (prompt.contains("历史") || prompt.contains("文化")) summary.append("偏好：历史文化。");
        else if (prompt.contains("美食")) summary.append("偏好：美食。");
        else if (prompt.contains("自然") || prompt.contains("徒步")) summary.append("偏好：自然风光。");

        if (summary.length() == 0) {
            return "用户正在规划旅行。";
        }
        return summary.toString();
    }
}